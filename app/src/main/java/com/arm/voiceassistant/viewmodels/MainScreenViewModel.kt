/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import android.app.Application
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arm.voiceassistant.Pipeline
import com.arm.voiceassistant.subscribers.ResponseSubscriber
import com.arm.voiceassistant.utils.Constants.ContentStates
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE
import com.arm.voiceassistant.utils.Constants.MIN_ALLOWED_RECORDING
import com.arm.voiceassistant.utils.Timer
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.responseComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main screen UI state
 *
 * @property contentState state used to determine UI content to display
 * @property error error information
 * @property userText transcribed input from user
 * @property responseText text response from voice assistant
 * @property recTime formatted duration of current recording
 * @property recTimeMs the duration of current recording in ms
 * @property playingAudio if speech response is playing
 * @property sttTime whisper model time taken
 * @property llmEncodeTPS LLM model encode tokens/second
 * @property llmDecodeTPS LLM model decode tokens/second
 */
data class MainUiState(
    val contentState: ContentStates = ContentStates.Idle,
    val error: Error = Error(),
    val userText: String = "",
    val responseText: String = "",
    val recTime: String = "00:00",
    val recTimeMs: Long = 0,
    val playingAudio: Boolean = false,
    val displayPerformance: Boolean = false,
    val sttTime: String = INITIAL_METRICS_VALUE,
    val llmEncodeTPS: String = INITIAL_METRICS_VALUE,
    val llmDecodeTPS: String = INITIAL_METRICS_VALUE
)

/**
 * Class to hold error information
 * @property state true if there is an error
 * @property message error message to display
 */
data class Error(
    val state: Boolean = false,
    val message: String = ""
)

private const val FILE_NAME = "recording.wav"

/**
 * Main ViewModel responsible for holding and managing UI related data
 * for the main screen along with executing any business logic.
 *
 * E.g. user input for inference options and running inference
 */
class MainViewModel(application: Application, isTest: Boolean = false) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    var uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val filePath: String =
        application.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath

    var pipeline: Pipeline

    private var timer: Timer = Timer()
    private var delayMilliseconds = 21L
    private var useAsyncLLM = true

    private var llmResponseGenerationJob: Job? = null

    private var subscriber: ResponseSubscriber = ResponseSubscriber(this)

    init {
        reset()
        pipeline = Pipeline(filePath, isTest)
        pipeline.setSubscriber(subscriber)
    }

    /**
     * Reset to default values
     */
    private fun reset() {
        val preservePerformance = _uiState.value.displayPerformance
        _uiState.value = MainUiState(
            contentState = ContentStates.Idle,
            error = Error(),
            userText = "",
            responseText = "",
            recTime = "00:00",
            recTimeMs = 0,
            playingAudio = false,
            displayPerformance = preservePerformance // Preserves display performance opened when resetting the metrics
        )
    }

    /**
     * Start recording and timer and update UI state to show recording state
     */
    fun onStartRecording() {
        // Reset state from previous run
        clearText()
        //clearPerformanceMetrics()
        _uiState.update { currentState -> currentState.copy(playingAudio = false, recTimeMs = 0) }
        timer.reset()

        setContentState(ContentStates.Recording)

        viewModelScope.launch {
            pipeline.startRecording()
        }
        timer.start()

        // Run timer in a coroutine
        viewModelScope.launch {
            while (timer.isRunning) {
                _uiState.update { currentState ->
                    currentState.copy(recTime = timer.format(), recTimeMs = timer.elapsedTime)
                }
                delay(delayMilliseconds)
            }
        }
    }

    /**
     * Stop recording and timer
     */
    private fun stopRecording() {
        if (_uiState.value.contentState != ContentStates.Recording) {
            return
        }

        timer.stop()

        _uiState.update { currentState ->
            currentState.copy(recTime = "00:00", recTimeMs = timer.elapsedTime)
        }

        if (pipeline.recorderInitialized()) {
            pipeline.stopRecording("$filePath/$FILE_NAME")
        }
    }


    /**
     * Cancel current recording in process
     */
    fun cancelRecording() {
        if (pipeline.recorderInitialized()) {
            pipeline.cancelRecording()
            setContentState(ContentStates.Idle)
        }
    }

    /**
     * Used by the pipeline to change to speaking state just before audio starts
     */
    private fun updateToSpeakingState() {
        setContentState(ContentStates.Speaking)
        // Log time from end of recording to first speech synthesis
        pipeline.getTimers().toggleRealTimer(start = false, dump = true)
    }


    /**
     * Called when button clicked to finish recording and start main pipeline
     */
    fun onStopRecording() {
        pipeline.getTimers().toggleRealTimer(start = true, dump = false)
        if (_uiState.value.recTimeMs < MIN_ALLOWED_RECORDING) {
            // Reset timer so that, recTimeMs does not accumulate and render this condition useless
            timer.reset()
            setContentState(ContentStates.Idle)
            if (pipeline.recorderInitialized()) {
                pipeline.cancelRecording()
            }
            onError("Short recording. Please try again.")
            return
        }

        viewModelScope.launch {
            stopRecording()
            invokePipeline()
        }
    }

    /**
     * Update the current content state
     * @param contentState current state to set
     */
    fun setContentState(contentState: ContentStates) {
        _uiState.update { currentState ->
            currentState.copy(contentState = contentState)
        }
    }

    /**
     * Set userText to transcription of recording
     * @param text transcription of user speech input
     */
    fun setUserText(text: String) {
        _uiState.update { currentState ->
            currentState.copy(userText = text)
        }
    }

    /**
     * Reset user and voice assistant text box values
     */
    private fun clearText() {
        _uiState.update { currentState ->
            currentState.copy(userText = "", responseText = "")
        }
    }

    /**
     * Set cancelling state and cancel the running pipeline
     */
    fun cancelPipeline() {
        // Avoid double release
        if (_uiState.value.contentState == ContentStates.Cancelling) {
            return
        }
        setContentState(ContentStates.Cancelling)
        cancelLLMResponseGenerationJob()
        if (!useAsyncLLM) {
            pipeline.cancelSpeechSynthesis()
        }
        else {
            subscriber.cancel()
        }
        reset()
    }


    fun resetUserText() {
        clearText()
    }

    /**
     * invoke pipeline
     */
    private suspend fun invokePipeline() {
        setContentState(ContentStates.Transcribing)
        val transcription = pipeline.transcribe("$filePath/$FILE_NAME")
        updateSpeechRecTime()
        pipeline.getTimers().toggleFirstResponseTimer(start = true, dump = false)
        setUserText(transcription)
        if(useAsyncLLM) {
            invokeLLMAndSSAsync()
        } else{
            invokeLLMAndSS()
        }
    }


    private fun updateSpeechRecTime() {
        _uiState.update { currentState ->
            currentState.copy(
                sttTime = "%.2f".format(
                    pipeline.getTimers().getSpeechRecognitionTime()
                )
            )
        }
    }

    /**
     * Set error message to display
     * @param error error message to show
     */
    fun onError(error: String) {
        _uiState.update { currentState ->
            currentState.copy(error = Error(true, error))
        }
    }

    /**
     * Clear current error message after displaying
     */
    fun clearError() {
        _uiState.update { currentState ->
            currentState.copy(error = Error(false))
        }
    }

    /**
     * Set responseText to text response of VoiceAssistant
     * @param text voice assistant text response
     */
    private fun setResponseText(text: String) {
        _uiState.update { currentState ->
            currentState.copy(responseText = text)
        }
    }

    /**
     * Toggle displaying performance metrics
     */
    fun togglePerformanceMetrics() {
        _uiState.update { currentState ->
            currentState.copy(displayPerformance = !_uiState.value.displayPerformance)
        }
    }

    /**
     * Synchronous handling of the LLM + SS part of the pipeline
     */
    private suspend fun invokeLLMAndSS() {
        var speechText = ""

        if (pipeline.llmInitialized()) {
            setContentState(ContentStates.Responding)
            speechText = pipeline.generateResponse(_uiState.value.userText)
            setResponseText(speechText)
            updateLLMTokensPerSec(pipeline.getEncodeTokensPerSec(), pipeline.getDecodeTokensPerSec())
        }

        // Synchronous so responses is an empty array
        val sanitizedWords = Utils.cleanupLine(arrayListOf(), speechText)

        if (pipeline.speechSynthesisInitialized()) {
            setResponseText(sanitizedWords)

            updateToSpeakingState()
            // Log time from end of recording to first speech synthesis
            pipeline.getTimers().toggleRealTimer(start = false, dump = true)
            /* val responseJob = */ CoroutineScope(Dispatchers.Default).launch {
                Thread.currentThread().priority = Thread.MAX_PRIORITY // highest priority
                pipeline.generateSpeech(sanitizedWords)
                updateToIdleState()
            }
        }
    }

    /**
     * Dispatch the response job
     */
    private fun dispatchLLMResponseGenerationJob(work: suspend () -> Unit) {
        if (llmResponseGenerationJob != null) {
            throw RuntimeException(
                "Cannot start another response job before ending the current")
        }
        llmResponseGenerationJob = CoroutineScope(Dispatchers.Default).launch {
            Thread.currentThread().priority = Thread.MAX_PRIORITY // highest priority
            work()
            llmResponseGenerationJob = null
        }
    }

    /**
     * Generate response (async)
     */
    private suspend fun generateResponseTokens(transcription: String) {
        pipeline.generateResponseTokens(transcription)
    }

    /**
     * Asynchronous handling of the LLM + SS part of the pipeline
     */
    private suspend fun invokeLLMAndSSAsync() {
        if (pipeline.llmInitialized()) {
            // Generate a response
            setContentState(ContentStates.Responding)
            dispatchLLMResponseGenerationJob {
                generateResponseTokens(
                    uiState.value.userText
                )
            }
        }
    }

    private fun updateLLMTokensPerSec(encode : Float, decode : Float) {
        _uiState.update { currentState ->
            currentState.copy(llmEncodeTPS = "%.2f".format(encode), llmDecodeTPS = "%.2f".format(decode))
        }
    }

    /**
     * Callback to handle the response generation for LLM
     */
     suspend fun generatedResponseCallback(tokens: String?) {
        if (tokens != null) {
            if (responseComplete(tokens)) {
                pipeline.finalizeSpeechSynthesis()
                updateToIdleState()
                updateLLMTokensPerSec(pipeline.getEncodeTokensPerSec(), pipeline.getDecodeTokensPerSec())
            } else {
                if (! pipeline.speechSynthesisInProgress()) {
                    pipeline.getTimers().toggleFirstResponseTimer(start = false, dump = true)
                    pipeline.startSpeechSynthesis()
                }
                pipeline.addWordsToSpeechSynthesis(tokens)
            }
        }
    }

    /**
     * Used by the pipeline to change to idle state after speech audio completes
     */
    fun updateToIdleState() {
        cancelLLMResponseGenerationJob()
        setContentState(ContentStates.Idle)
    }

    /**
     * Stop the LLM response job
     */
    private fun cancelLLMResponseGenerationJob() {
        llmResponseGenerationJob?.cancel()
        llmResponseGenerationJob = null
    }

    /**
     * Appends text to the response of VoiceAssistant
     * @param text voice assistant text response
     */
    private fun appendResponseText(text: String) {
        val updatedResponse = _uiState.value.responseText + text
        _uiState.update { currentState ->
            currentState.copy(responseText = updatedResponse)
        }
    }


    /**
     * Used by the pipeline to update the response field after each sentence is spoken
     */
    fun updateResponseFieldCallback(tokens: String) {
        appendResponseText(tokens)
    }

    /**
     * Used to reset the performance metrics after the context reset
     */
    fun resetPerformanceMetrics() {
        _uiState.update {
            it.copy(
                sttTime = INITIAL_METRICS_VALUE,
                llmEncodeTPS = INITIAL_METRICS_VALUE,
                llmDecodeTPS = INITIAL_METRICS_VALUE
            )
        }
    }

}
