/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import android.app.Application
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arm.Llm
import com.arm.voiceassistant.BuildConfig
import com.arm.voiceassistant.Pipeline
import com.arm.voiceassistant.utils.ChatMessage
import com.arm.voiceassistant.utils.ChatMetricsUpdater
import com.arm.voiceassistant.utils.Constants.ContentStates
import com.arm.voiceassistant.utils.Constants.EOS
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE
import com.arm.voiceassistant.utils.Constants.LLM_CONTEXT_CAPACITY_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_DECODE_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_IMAGE_ADD_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_QUERY_EVALUATION_ERROR
import com.arm.voiceassistant.utils.Constants.MIN_ALLOWED_RECORDING
import com.arm.voiceassistant.utils.Constants.MODEL_NOT_FOUND_ERROR
import com.arm.voiceassistant.utils.Constants.PIPELINE_INIT_ERROR
import com.arm.voiceassistant.utils.Constants.RESPONSE_JOB_IN_PROGRESS_ERROR
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.LlmBridge
import com.arm.voiceassistant.utils.NativeResult
import com.arm.voiceassistant.utils.Timer
import com.arm.voiceassistant.utils.TimingStats
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.responseComplete
import com.arm.voiceassistant.utils.ToastService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val ttsWarningMessage: String? = null
)

/**
 * Class to hold error information
 * @property state true if there is an error
 * @property message error message to display
 */
data class Error(
    val state: Boolean = false,
    val contextCapacity: Boolean = false,
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
    private val tmpFilePath: File = application.applicationContext.cacheDir
    private val contentResolver = application.contentResolver
    private val sharedLibraryPath: String = application.applicationInfo?.nativeLibraryDir ?: ""

    lateinit var pipeline: Pipeline
    lateinit var llm: Llm
    lateinit var llmBridge: LlmBridge

    private var timer: Timer = Timer()
    private var delayMilliseconds = 21L
    private var useAsyncLLM = true
    private var llmResponseGenerationJob: Job? = null
    var imageUploadEnabled: Boolean = false

    private val _toastMessages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    val toastMessages: SharedFlow<String> = _toastMessages
    private val metricsUpdater = ChatMetricsUpdater(_uiState, messages)

    private var llmFramework = BuildConfig.LLM_FRAMEWORK
    private var encodeUpdatedForThisTurn = false

    private val benchmarkMutex = Mutex()
    private val pipelineMutex = Mutex()
    private val isTestMode = isTest

    private val stringStatusFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = stringStatusFlow.asSharedFlow()

    /**
     * Runs an LLM benchmark and returns its result summary as a single atomic operation.
     *
     * @param modelKey Logical model name selected in the UI.
     * @param inputTokens Number of input tokens for the synthetic prompt.
     * @param outputTokens Number of tokens to generate during decode.
     * @param contextSize Context length in tokens (must exceed input + output tokens).
     * @param threads Number of runtime threads to use.
     * @param iterations Number of measured benchmark iterations.
     * @param warmup Number of warm-up iterations (excluded from stats).
     *
     * @return A human-readable benchmark summary string. If the benchmark fails,
     * the returned string includes the failure code and any available output.
     */
    suspend fun runBenchmarkAndGetSummary(
        modelKey: String,
        inputTokens: Int,
        outputTokens: Int,
        contextSize: Int,
        threads: Int,
        iterations: Int,
        warmup: Int
    ): String = benchmarkMutex.withLock {
        withContext(Dispatchers.IO) {
            val code = runBenchmark(
                modelKey,
                inputTokens,
                outputTokens,
                contextSize,
                threads,
                iterations,
                warmup
            )
            val results = getBenchmarkResults()
            if (code == 0) results else "Benchmark failed (code=$code)\n$results"
        }
    }

    /**
     * Initializes the [Pipeline] and related LLM helpers.
     *
     * NOTE: Callers should hold [pipelineMutex] to prevent concurrent init/shutdown races.
     */
    private suspend fun initPipelineLocked(isTest: Boolean) {
        viewModelScope.launch {
            errorFlow.collect { msg ->
                onError(msg)
                setContentState(ContentStates.Idle)
            }
        }
        runCatching {
            pipeline = Pipeline(filePath, stringStatusFlow,isTest, sharedLibraryPath)
            llm = pipeline.llm
            llmBridge = LlmBridge(llm)
            imageUploadEnabled = pipeline.supportsImageInput()
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to Initialize the pipeline :$e", e)
            onError(PIPELINE_INIT_ERROR)
        }
    }

    /**
     * Synchronized entry for pipeline init using a coroutine-friendly mutex.
     */
    private suspend fun initPipeline(isTest: Boolean) = pipelineMutex.withLock {
        initPipelineLocked(isTest)
    }

    /**
     * Shuts down the current pipeline and clears chat/UI state.
     *
     * NOTE: Callers should hold [pipelineMutex] to prevent concurrent init/shutdown races.
     */
    private fun shutdownPipelineLocked() {
        // Cancel token stream, if available
        if (this::llmBridge.isInitialized) {
            runCatching { llmBridge.cancel() }
        }

        cancelLLMResponseGenerationJob()

        // Stop any ongoing audio/speech
        runCatching {
            if (this::pipeline.isInitialized) {
                if (pipeline.recorderInitialized()) pipeline.cancelRecording()
                pipeline.cancelSpeechSynthesis()
            }
        }

        // Free native resources
        runCatching {
            if (this::pipeline.isInitialized) {
                pipeline.close()
            }
        }

        // Clear previous chat UI state
        messages.clear()

        reset()
    }

    /**
     * Initialization block for the ViewModel.
     */
    init {
        reset()
        // Keep initial pipeline creation synchronous for predictable startup behavior.
        runBlocking {
            initPipeline(isTest)
        }
    }

    /**
     * Shuts down the current pipeline and clears chat/UI state.
     *
     * Cancels in-flight LLM work, stops recording/synthesis if needed,
     * frees native resources via [Pipeline.close], clears messages, and resets state.
     */
    suspend fun shutdownPipeline() = pipelineMutex.withLock {
        shutdownPipelineLocked()
    }

    /**
     * Recreates the pipeline for a fresh chat session.
     *
     * Performs a full shutdown and re-initialization, then returns to [ContentStates.Idle].
     */
    suspend fun reinitializePipelineForChat(isTest: Boolean = isTestMode) = pipelineMutex.withLock {
        shutdownPipelineLocked()
        initPipelineLocked(isTest)
        setContentState(ContentStates.Idle)
    }

    /**
     * Find a GGUF model file for llama.cpp using the given model key.
     *
     * Looks under:
     *   <filePath>/llama.cpp/<modelKey>/
     *
     * The directory is scanned for `.gguf` files (projector models are ignored).
     * The first file in sorted order is selected.
     *
     * @param modelKey Model name from the UI.
     * @return Absolute path to the selected `.gguf` file, or null if none is found.
     */
    private fun resolveLlamaModelPath(modelKey: String): String? {
        val llamaRoot = File(filePath, "llama.cpp")
        val modelDir = File(llamaRoot, modelKey)

        if (!modelDir.exists() || !modelDir.isDirectory) {
            val msg = "Model folder not found for \"$modelKey\""
            Log.e(VOICE_ASSISTANT_TAG, msg + ": ${modelDir.absolutePath}")
            showToast(msg)
            return null
        }

        val ggufFiles = modelDir.listFiles { f ->
            f.isFile &&
                    f.extension.equals("gguf", ignoreCase = true) &&
                    !f.name.contains("proj", ignoreCase = true)
        }?.sortedBy { it.name } ?: emptyList()

        if (ggufFiles.isEmpty()) {
            val msg = "No compatible .gguf model found for \"$modelKey\""
            Log.e(VOICE_ASSISTANT_TAG, msg + " in ${modelDir.absolutePath}")
            showToast(msg)
            return null
        }

        val chosen = ggufFiles.first()
        Log.i(VOICE_ASSISTANT_TAG, "Using GGUF model: ${chosen.absolutePath}")
        return chosen.absolutePath
    }


    /**
     * Run the native LLM benchmark with the selected configuration.
     *
     * @param modelKey Logical model name from UI (e.g. "llama-3.2", "phi-2").
     * @param inputTokens Number of input tokens for the synthetic prompt.
     * @param outputTokens Number of tokens to generate during decode.
     * @param contextSize Context length in tokens (must exceed input + output tokens).
     * @param threads Number of threads to use in the backend runtime.
     * @param iterations Number of measured iterations.
     * @param warmup Number of warm-up iterations (ignored in stats).
     * @return 0 on success, non-zero on failure.
     */
    fun runBenchmark(
        modelKey: String,
        inputTokens: Int,
        outputTokens: Int,
        contextSize: Int,
        threads: Int,
        iterations: Int,
        warmup: Int
    ): Int {
        val modelPath: String? = if (llmFramework == "llama.cpp") {
            resolveLlamaModelPath(modelKey)
        } else {
            "$filePath/$llmFramework/$modelKey/"
        }

        if (modelPath == null) {
            onError(MODEL_NOT_FOUND_ERROR.format(modelKey))
            return -1
        }

        return llm.runBenchmark(
            modelPath,
            inputTokens,
            outputTokens,
            contextSize,
            threads,
            iterations,
            warmup,
            sharedLibraryPath
        )
    }

    /**
     * Get the last benchmark results string from the native benchmark.
     */
    fun getBenchmarkResults(): String {
        return llm.getBenchmarkResults()
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
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to create response for Prompt", e)
            onError(LLM_QUERY_EVALUATION_ERROR)
        }
    }

    /**
     * Cancel current recording in process
     */
    fun cancelRecording() {
        if (pipeline.recorderInitialized()) {
            pipeline.cancelRecording()
        }
        setContentState(ContentStates.Idle)
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
                onError(LLM_CONTEXT_CAPACITY_ERROR)
            } else {
                onError("Failed to create Audio record and generate response, try restarting the voice-assistant app")
            }
        }
    }

    /**
     * Method to show warning toasts
     * @param message : Warning message to show in toast
     */
    fun showToast(message: String) {
        ToastService.showToast(message)
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

        val timing = TimingStats(
            sttTime = uiState.value.sttTime,
            llmEncodeTps = "-",
            llmDecodeTps = "-"
        )

        messages.add(ChatMessage.UserText(text, timing = timing))
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
     * Clears only the assistant response text from UI state.
     */
    private fun clearResponseText() {
        _uiState.update { currentState ->
            currentState.copy(responseText = "")
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
        reset()
    }

    /**
     * Resets the user input and performance metrics.
     */
    fun resetUserText() {
        clearText()
        clearPerfMetrics()
        _uiState.update { currentState ->
            currentState.copy(
                error = Error(
                    false, contextCapacity = false,
                    message = ""
                )
            )
        }
        clearError()
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
        if (useAsyncLLM) {
            Log.i(VOICE_ASSISTANT_TAG, "Invoking LLM async")
            invokeLLMAndSSAsync()
            // Verify error state before proceeding to generate response tokens
            if ((!_uiState.value.error.contextCapacity) and (!_uiState.value.error.state)) {
                getLlmResponse()
            }
        }
        else {
            Log.i(VOICE_ASSISTANT_TAG, "Invoking LLM sync")
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
        val contextCapacity = error.contains("Context capacity has filled")
        if (!uiState.value.error.state) {
            _uiState.update { currentState ->
                currentState.copy(error = Error(true, contextCapacity, error))
            }
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
            onError(RESPONSE_JOB_IN_PROGRESS_ERROR)
            return
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
        }
    }

    /**
     * Reads streamed tokens from the native bridge and updates UI/chat messages.
     *
     * Updates encode TPS once per turn, appends tokens to the response, and finalizes
     * when EOS/cancel/completion is detected.
     */
    private suspend fun getLlmResponse() {
        if (!pipeline.llmInitialized()) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to init LLM module")
            return
        }

        llmBridge.clearCancel()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                var count = 0
                var complete = false
                do {
                    var message = ""
                    var token = ""
                    Log.d(VOICE_ASSISTANT_TAG, "Waiting on get token")
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

                    if (!encodeUpdatedForThisTurn) {
                        encodeUpdatedForThisTurn = true

                        val encodeTps = "%.2f".format(pipeline.getEncodeTokensPerSec())

                        launch(Dispatchers.Main) {
                            // Update uiState encode TPS
                            metricsUpdater.onEncodeAvailableOncePerTurn(encodeTps)
                        }
                    }

                    if (llm.isStopToken(token)) {
                        Log.d(VOICE_ASSISTANT_TAG, "EOS token retrieved")
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
                } while (count++ < 100 && complete == false)
            }.onFailure { e ->
                val msg = LLM_DECODE_ERROR
                if (!uiState.value.error.state) {
                    onError(msg)
                }
                Log.e(VOICE_ASSISTANT_TAG, msg, e)
            }
        }
    }


    /**
     * Asynchronous handling of the LLM + SS part of the pipeline
     */
    private suspend fun invokeLLMAndSSAsync() {
        if (pipeline.llmInitialized()) {
            setContentState(ContentStates.Responding)
            messages.add(ChatMessage.AssistantText(""))

            encodeUpdatedForThisTurn = false
            generateResponseTokens(uiState.value.userText)
        }
    }

    /**
     * Callback to handle the response generation for LLM
     * @param tokens The latest chunk of response tokens received from the LLM.
     */
    suspend fun generatedResponseCallback(tokens: String?) {
        runCatching {
            if (tokens == null) return@runCatching

            if (responseComplete(tokens)) {
                // Snapshot latest perf numbers
                val encodeTps = "%.2f".format(pipeline.getEncodeTokensPerSec())
                val decodeTps = "%.2f".format(pipeline.getDecodeTokensPerSec())

                // Update UI metrics + message timing footers via helper
                metricsUpdater.onEncodeAvailableOncePerTurn(encodeTps)
                metricsUpdater.onDecodeAvailableAtEnd(decodeTps)

                pipeline.finalizeSpeechSynthesis()
                updateToIdleState()

                if (pipeline.getChatProgress() > 80) {
                    Log.w(VOICE_ASSISTANT_TAG, "context is ${pipeline.getChatProgress()}% full")
                    showToast("LLM context consumed %${pipeline.getChatProgress()} , consider resetting context")
                }
            } else if (uiState.value.isTTSEnabled) {
                if (!pipeline.speechSynthesisInProgress()) {
                    pipeline.getTimers().toggleFirstResponseTimer(start = false, dump = true)
                    pipeline.startSpeechSynthesis()
                }
                pipeline.addWordsToSpeechSynthesis(tokens)
            }
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to generate response $e", e)
            onError(LLM_DECODE_ERROR)
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
            val existing = messages[lastIndex] as ChatMessage.AssistantText
            messages[lastIndex] = existing.copy(text = currentText)
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
            onError(LLM_IMAGE_ADD_ERROR)
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
                it.copy(ttsWarningMessage = "TTS is not available.")
            }
            return
        }
        _uiState.update {
            it.copy(
                isTTSEnabled = !it.isTTSEnabled,
                ttsWarningMessage = null
            )
        }
    }

    /**
     * Clears the TTS warning message
     */
    fun clearTtsWarningMessage() {
        _uiState.update { it.copy(ttsWarningMessage = null) }
    }
}
