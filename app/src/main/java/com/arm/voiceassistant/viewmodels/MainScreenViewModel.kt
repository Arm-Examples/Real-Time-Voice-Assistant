/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arm.Llm
import com.arm.voiceassistant.Pipeline
import com.arm.voiceassistant.subscribers.ResponseSubscriber
import com.arm.voiceassistant.utils.Constants.ContentStates
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE
import com.arm.voiceassistant.utils.Constants.MIN_ALLOWED_RECORDING
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Constants.EOS
import com.arm.voiceassistant.utils.LlmBridge
import com.arm.voiceassistant.utils.NativeResult
import com.arm.voiceassistant.utils.Timer
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.responseComplete
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.arm.voiceassistant.utils.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    val imagePath: String = "",
    val recTime: String = "00:00",
    val recTimeMs: Long = 0,
    val playingAudio: Boolean = false,
    val displayPerformance: Boolean = false,
    val sttTime: String = INITIAL_METRICS_VALUE,
    val llmEncodeTPS: String = INITIAL_METRICS_VALUE,
    val llmDecodeTPS: String = INITIAL_METRICS_VALUE,
    val isTTSEnabled: Boolean = false,
    val TTSWarningMessage: String? = null
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

/**
 * The default filename used for storing the recorded audio file.
 */
private const val FILE_NAME = "recording.wav"

/**
 * Main ViewModel responsible for holding and managing UI related data
 * for the main screen along with executing any business logic.
 *
 * E.g. user input for inference options and running inference
 * @param application The [Application] context required for file paths and resource access.
 * @param isTest Boolean flag to indicate test mode, used to skip real pipeline initialization.
 */
class MainViewModel(application: Application, isTest: Boolean = false) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    var uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    val messages: SnapshotStateList<ChatMessage> = mutableStateListOf()
    private val filePath: String =
        application.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!.absolutePath
    private val tmpFilePath: File =
        application.applicationContext.cacheDir
    private val contentResolver = application.contentResolver
    lateinit var pipeline: Pipeline

  
    lateinit var llm: Llm
    lateinit var llmBridge: LlmBridge
    private var timer: Timer = Timer()
    private var delayMilliseconds = 21L
    private var useAsyncLLM = true
    private var llmResponseGenerationJob: Job? = null
    private var subscriber: ResponseSubscriber = ResponseSubscriber(this)
    var imageUploadEnabled: Boolean = false
    private val _toastMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages


    /**
     * Initialization block for the ViewModel.
     */
    init {
        reset()
        runCatching {
            val applicationInfo = application.applicationInfo
            val sharedLibraryPath =
                if (applicationInfo != null) applicationInfo.nativeLibraryDir else ""
            pipeline = Pipeline(filePath, isTest, sharedLibraryPath)

            llm = pipeline.llm
            llmBridge = LlmBridge(llm)

            imageUploadEnabled = pipeline.supportsImageInput()
        }
        .onFailure{ e ->
            Log.e(VOICE_ASSISTANT_TAG,"Failed to Initialize the pipeline :$e")
            onError( "Failed to initialize the Voice assistant pipeline, Check configs and models")
        }
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
        runCatching {
        // Reset state from previous run
            clearResponseText()
        //clearPerformanceMetrics()
            _uiState.update { currentState ->
                currentState.copy(
                    playingAudio = false,
                    recTimeMs = 0
                )
            }
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
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Recording query failed, $e")
            onError("Failed to create Audio record")
    }
    }

    /**
     * Stop recording and timer
     */
    private fun stopRecording() {
        runCatching {
        if (_uiState.value.contentState != ContentStates.Recording) {
                return@runCatching
        }

        timer.stop()

        _uiState.update { currentState ->
            currentState.copy(recTime = "00:00", recTimeMs = timer.elapsedTime)
        }

        if (pipeline.recorderInitialized()) {
            pipeline.stopRecording("$filePath/$FILE_NAME")
        }
        }.onFailure {e ->
            Log.e(VOICE_ASSISTANT_TAG,"Failed to create response for Prompt" ,e)
            onError("Failed to create query response, Try restarting")
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
        runCatching {
        pipeline.getTimers().toggleRealTimer(start = true, dump = false)
        if (_uiState.value.recTimeMs < MIN_ALLOWED_RECORDING) {
            // Reset timer so that, recTimeMs does not accumulate and render this condition useless
            timer.reset()
            setContentState(ContentStates.Idle)
            if (pipeline.recorderInitialized()) {
                pipeline.cancelRecording()
            }
            onError("Short recording. Please try again.")
                return@runCatching
        }

        viewModelScope.launch {
            stopRecording()
            invokePipeline()
        }
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to create Audio record and generate response", e)
            if (e.message?.contains("context is full") == true) {
                onError("Query Encoding failed due to llm context overflow. Try resetting the context")
    }
            else {
                onError("Failed to create Audio record and generate response, Try restarting the voice-assistant")
            }
        }
    }

    /**
     * Method to show warning toasts
     * @param message : Warning message to show in toast
     */
    fun showToast(message: String) {
        _toastMessages.tryEmit(message)
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
        messages.add(ChatMessage.UserText(text))
    }

    /**
     * Reset user and voice assistant text box values
     */
    private fun clearText() {
        _uiState.update { currentState ->
            currentState.copy(userText = "", responseText = "")
        }
        messages.clear()
    }

    /**
     * Reset user and voice assistant text box values
     */
    private fun clearPerfMetrics() {
        _uiState.update { currentState ->
            currentState.copy(
                sttTime = INITIAL_METRICS_VALUE,
                llmEncodeTPS = INITIAL_METRICS_VALUE,
                llmDecodeTPS = INITIAL_METRICS_VALUE,
                displayPerformance = false
            )
        }
    }

    /**
     * Reset voice assistant text box values
     */
    private fun clearResponseText()
    {
        _uiState.update { currentState ->
            currentState.copy( responseText = "")
        }
    }
    /**
     * Function to request the cancellation of a ongoing operation / functional call
     * @param operationId associated with operation / functional call
     */
    fun CancelAsync() {
        llmBridge.cancel()
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
        }
        reset()
    }

    /**
     * Resets the user input and performance metrics.
     */
    fun resetUserText() {
        clearText()
        clearPerfMetrics()
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
            Log.i (VOICE_ASSISTANT_TAG, "Invoking LLM async")
            invokeLLMAndSSAsync()

            getLlmResponse()
        } else{
            Log.i (VOICE_ASSISTANT_TAG, "Invoking LLM sync")
            invokeLLMAndSS()
        }
    }

    /**
     * Updates the UI state with the latest speech recognition time.
     */
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
        messages.add(ChatMessage.AssistantText(text))
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
            pipeline.generateResponse(_uiState.value.userText)
            updateLLMTokensPerSec(pipeline.getEncodeTokensPerSec(), pipeline.getDecodeTokensPerSec())
        }

        // Synchronous so responses is an empty array
        val sanitizedWords = Utils.cleanupLine(arrayListOf(), speechText)

        if (uiState.value.isTTSEnabled && pipeline.speechSynthesisInitialized()) {
            setResponseText(sanitizedWords)
            updateToSpeakingState()
            // Log time from end of recording to first speech synthesis
            pipeline.getTimers().toggleRealTimer(start = false, dump = true)
            /* val responseJob = */ CoroutineScope(Dispatchers.Default).launch {
                Thread.currentThread().priority = Thread.MAX_PRIORITY // highest priority
                pipeline.generateSpeech(sanitizedWords)
                updateToIdleState()
            }
        } else {
            // Skip speaking, just show response
            setResponseText(sanitizedWords)
            updateToIdleState()
        }
    }

    /**
     * Dispatch the response job
     * @param work A suspend lambda that performs the response generation logic.
     * @throws RuntimeException if an existing response job is still running.
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
     * @param transcription The user's transcribed input to be processed by the LLM.
     */
    private suspend fun generateResponseTokens(transcription: String) {
        runCatching {
            pipeline.generateResponseTokens(transcription)
        }.onFailure { e ->
            if ((pipeline.getChatProgress() > 85) || (e.message?.contains("context is full") == true)) {
                onError("Query Encoding failed due to llm context overflow. Try resetting the context")
            } else {
                onError("Query Response phase failed . Try restarting the app")
            }
        }
    }

    private suspend fun getLlmResponse() {

         if (!pipeline.llmInitialized()) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to init LLM module")
            return
        }

        llmBridge.clearCancel()

        CoroutineScope(Dispatchers.IO).launch {

            var count = 0
            var complete = false
            do {
                var message = ""
                var token = ""
                Log.i(VOICE_ASSISTANT_TAG, "Waiting on get token")
                val result = llmBridge.getNextToken()
                when (result) {
                    is NativeResult.Success -> token = result.data ?: ""
                    is NativeResult.Cancelled -> message = "Info: Sync operation cancelled"
                    is NativeResult.Error -> message =
                        "Error: getNextToken Failed. " + (result.message ?: "")
                }

                if (result !is NativeResult.Success) {
                   Log.e(VOICE_ASSISTANT_TAG, message)
                    complete = true
                }

                if (token.isEmpty()) {
                    Log.e(VOICE_ASSISTANT_TAG, "Token returned from LLM is empty")
                    continue
                }

                if( llm.isStopToken(token)) {
                    Log.i(VOICE_ASSISTANT_TAG, "EOS token retrieved")
                    complete = true
                }

                if (llmBridge.isCancelInProgress.get()) {
                    Log.i(VOICE_ASSISTANT_TAG, "Token Generation cancelled")
                    complete = true
                }

                launch(Dispatchers.Main) {
                    complete = complete || Utils.responseComplete(token)
                    if (!complete) {
                        updateResponseFieldCallback(token)
                    } else {
                        token = EOS
                    }
                    generatedResponseCallback(token)
                }
            } while (count++ < 100 && complete == false )
        }

    }


    /**
     * Asynchronous handling of the LLM + SS part of the pipeline
     */
    private suspend fun invokeLLMAndSSAsync() {
        if (pipeline.llmInitialized()) {
            // Generate a response
            setContentState(ContentStates.Responding)
            messages.add(ChatMessage.AssistantText(""))
            generateResponseTokens(uiState.value.userText)
        }
    }

    /**
     * Updates the UI state with the latest LLM performance metrics.
     * @param encode The LLM's encode speed in tokens per second.
     * @param decode The LLM's decode speed in tokens per second.
     */
    private fun updateLLMTokensPerSec(encode : Float, decode : Float) {
        _uiState.update { currentState ->
            currentState.copy(llmEncodeTPS = "%.2f".format(encode), llmDecodeTPS = "%.2f".format(decode))
        }
    }

    /**
     * Callback to handle the response generation for LLM
     * @param tokens The latest chunk of response tokens received from the LLM.
     */
     suspend fun generatedResponseCallback(tokens: String?)
    {
         runCatching {
        if (tokens != null) {
            if (responseComplete(tokens)) {
                pipeline.finalizeSpeechSynthesis()
                updateToIdleState()
                updateLLMTokensPerSec(pipeline.getEncodeTokensPerSec(), pipeline.getDecodeTokensPerSec())
                    if (pipeline.getChatProgress() > 80)
                    {
                        Log.w(VOICE_ASSISTANT_TAG,"context is ${pipeline.getChatProgress()}% full")
                        showToast( "LLM context consumed %${pipeline.getChatProgress()} , consider resetting context")
                    }
                } else if (uiState.value.isTTSEnabled) {
                    if (!pipeline.speechSynthesisInProgress()) {
                    pipeline.getTimers().toggleFirstResponseTimer(start = false, dump = true)
                    pipeline.startSpeechSynthesis()
                }
                pipeline.addWordsToSpeechSynthesis(tokens)
            }
        }
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG,"Failed to generate response $e")
            onError(" Response generation failed , Try restarting .")
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
    fun cancelLLMResponseGenerationJob() {
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
     * @param tokens The latest response segment generated by the assistant.
     */
    fun updateResponseFieldCallback(tokens: String) {
        appendResponseText(tokens)
        val lastIndex = messages.indexOfLast { it is ChatMessage.AssistantText }
        if (lastIndex != -1) {
            val currentText = uiState.value.responseText
            messages[lastIndex] = ChatMessage.AssistantText(currentText)
    }
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

    /**
     * Handles adding an image selected by the user into the chat workflow.
     * @param uri The [Uri] of the image selected by the user.
     */
    fun addImage(uri: Uri) {
        runCatching {
            Log.d("tag", "User selected image URI = $uri")
            val inputStream = contentResolver.openInputStream(uri)
            val filename = "image_${System.currentTimeMillis()}.jpg"
            val originalResFile = File(filePath, filename)
            val outputStream = FileOutputStream(originalResFile)
            inputStream?.copyTo(outputStream)
            pipeline.addImageToLLmDialog(originalResFile, tmpFilePath.absolutePath)
            _uiState.update { currentState ->
                currentState.copy(imagePath = originalResFile.absolutePath)
            }
            val imageUri = Uri.fromFile(originalResFile)
            messages.add(ChatMessage.UserImage(imageUri))
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to add Image: $e")
            onError(" Querying Image failed , Try restarting .")
        }
    }

    /**
     * Sets the current UI state manually, used for testing purposes.
     * @param state The new test state to assign.
     */
    fun setUiStateForTest(state: MainUiState) {
        _uiState.value = state
    }

    /**
     * Toggles the Text-to-Speech (TTS) enabled state.
     */
    fun toggleTTS() {
        val isTurningOn = !_uiState.value.isTTSEnabled
        if (isTurningOn && !pipeline.speechSynthesisInitialized()) {
            _uiState.update {
                it.copy(TTSWarningMessage = "TTS is not available.")
            }
            return
        }
        _uiState.update {
            it.copy(
                isTTSEnabled = !it.isTTSEnabled,
                TTSWarningMessage = null
            )
        }
    }

    /**
     * Clears the TTS warning message
     */
    fun clearTTSWarningMessage() {
        _uiState.update { it.copy(TTSWarningMessage = null) }
    }
}
