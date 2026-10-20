
/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant
import android.os.Environment
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.arm.Llm
import com.arm.stt.Whisper
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.audio.AudioReader
import com.arm.voiceassistant.huggingface.HuggingFaceApiService
import com.arm.voiceassistant.huggingface.HuggingFaceModel
import com.arm.voiceassistant.huggingface.ModelDownloadSpec
import com.arm.voiceassistant.huggingface.ProgressListener
import com.arm.voiceassistant.speech.SpeechRecorder
import com.arm.voiceassistant.speech.SpeechSynthesis
import kotlinx.coroutines.cancel
import com.arm.voiceassistant.ui.composables.pipeline
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Constants.LLM_INITIALIZATION_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_CONTEXT_CAPACITY_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_QUERY_EVALUATION_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_IMAGE_ADD_ERROR
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.createLlmDefaultConfig
import com.arm.voiceassistant.utils.Utils.createSttDefaultConfig
import com.arm.voiceassistant.utils.Utils.isValidLlmConfig
import com.arm.voiceassistant.utils.Utils.isValidSttConfig
import com.arm.voiceassistant.utils.Utils.readLlmUserConfig
import com.arm.voiceassistant.utils.Utils.readSttUserConfig
import com.arm.voiceassistant.utils.userFacingModelAvailabilityErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream


/** Main processing pipeline that coordinates STT, LLM, and TTS components.
 *
 * The pipeline handles recording user audio, transcribing it to text, generating responses
 * via a language model, and synthesizing speech output. It also manages relevant configuration,
 * timing, and model initialization logic.
 *
 * @param modelPath Path to model files used by LLM and STT engines
 * @param isTest Flag to control test behavior (e.g., for CI or mocks)
 * @param sharedLibraryPath path to shared libraries location, this path can be used by JNI
 *        libraries to load other shared libs
 *
 */

class Pipeline(
    private val modelPath: String,
    errorFlow: MutableSharedFlow<String>,
    isTest: Boolean = false,
    private val sharedLibraryPath: String = "",
    private val statusReporter: ((String) -> Unit)? = null
) : AutoCloseable {
    private var timers = PipelineTimers()                    // Various timers needed
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @VisibleForTesting
    var speechRecorder: SpeechRecorder = SpeechRecorder(audioScope)
        internal set
    private var speechFilePath: String = ""                  // Path to the recorded audio file
    private var stt = Whisper()                              // Speech-to-text engine
    public var llm = Llm()                                  // Language model
    private var llmInitialized = false                       // Flag indicating if the LLM is initialized
    private var sttContext = 0L                              // Internal context/state for the STT engine
    private val reader = AudioReader()                       // Reads audio files
    private var speechSynthesis = SpeechSynthesis()          // Generator of speech
    private var llmFramework = BuildConfig.LLM_FRAMEWORK     // Selected llm framework
    private val llmMutex = Mutex()
    @OptIn(ExperimentalCoroutinesApi::class)
    private val llmScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var lastImageEncodeJob: Job? = null
    private var isTestMode = isTest

    private val manifestDownloader = HuggingFaceApiService()

    // User config file for llm, resolved per framework (files now generated in module dirs)
    private var configFileName: String = Utils.getLlmConfig(llmFramework)
    private var pipelineErrorFlow = errorFlow
    // User config file name stt
    private var configFileNameSTT = "whisperTextConfig.json"
    private val configAssetsDir = "configs"

    private fun getAssetConfigText(fileName: String): String? {
        Log.i(VOICE_ASSISTANT_TAG, "configAssetsDir... $configAssetsDir")
        val appContext = AppContext.getInstance().context ?: return null
        val assetPath = "$configAssetsDir/$fileName"
        Log.i(VOICE_ASSISTANT_TAG, "asset path... $assetPath")
        return try {
            appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Config asset not found: $assetPath")
            return ""
        }
    }

    /** Initialize speech recognition, large language model and speech synthesis. */
    suspend fun initialize(): ModelAvailabilityStatus {
        Log.i(VOICE_ASSISTANT_TAG,"Android Shared Library Path $sharedLibraryPath")

        if (isTestMode) return ModelAvailabilityStatus(available = true)

        val availability = checkManifestModelsAvailability()
        if (availability.available) {
            withContext(Dispatchers.IO) {
                statusReporter?.invoke("Loading speech-to-text model...")
                initializeSTT(modelPath)
                statusReporter?.invoke("Loading language model...")
                initializeLLM(modelPath)
                statusReporter?.invoke("Finalizing setup...")
            }
        }
        return availability
    }

    /**
     * Releases all resources owned by the voice assistant pipeline.
     *
     * Stops ongoing work, cancels audio and synthesis, resets the LLM context,
     * cancels coroutines, and frees the native model.
     * This method is synchronized and the instance must not be reused afterward.
     */
    @Synchronized
    fun destroy() {
        runCatching {
            // Stop async encode job
            lastImageEncodeJob?.cancel()
            lastImageEncodeJob = null
        }

        // Stop audio / synthesis safely
        runCatching { cancelRecording() }
        runCatching { speechRecorder.release() }
        runCatching { cancelSpeechSynthesis() }
        runCatching { audioScope.cancel() }


        // Reset context before freeing model
        runCatching { resetContext() }

        // Cancel pipeline-owned coroutine scope
        runCatching { llmScope.cancel() }

        // Free native LLM model explicitly (THIS IS THE IMPORTANT PART)
        runCatching {
            if (llmInitialized) {
                llm.freeModel()
                llmInitialized = false
            }
        }

        // Clear global reference used by TopBar
        runCatching {
            if (pipeline === this) pipeline = null
        }
    }

    /**
     * AutoCloseable support so callers can use `pipeline.close()`.
     */
    override fun close() {
        destroy()
    }

    internal suspend fun checkManifestModelsAvailability(): ModelAvailabilityStatus = withContext(Dispatchers.IO) {
        val appContext = AppContext.getInstance().context ?: run {
            Log.w(VOICE_ASSISTANT_TAG, "Manifest check skipped: context unavailable")
            return@withContext ModelAvailabilityStatus(
                available = false,
                errorMessage = "Application context has not been set"
            )
        }
        val unresolvedSpecs = readManifestModels().getOrElse { e ->
            val message = e.message ?: "Failed to read manifest.json for $llmFramework"
            Log.e(VOICE_ASSISTANT_TAG, message, e)
            return@withContext ModelAvailabilityStatus(
                available = false,
                errorMessage = message
            )
        }
        val client = OkHttpClient()
        val specs = resolveManifestSpecSha256s(client, unresolvedSpecs).getOrElse { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to resolve model SHA-256 metadata for $llmFramework", e)
            return@withContext ModelAvailabilityStatus(
                available = false,
                errorMessage = userFacingModelAvailabilityErrorMessage(e)
            )
        }
        if (specs.isEmpty()) {
            val message = "No models defined in manifest.json for $llmFramework"
            Log.i(VOICE_ASSISTANT_TAG, message)
            return@withContext ModelAvailabilityStatus(
                available = false,
                errorMessage = message
            )
        }

        val downloadRoot =
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        if (downloadRoot.isNullOrBlank()) {
            val message = "Model download path unavailable"
            Log.w(VOICE_ASSISTANT_TAG, message)
            return@withContext ModelAvailabilityStatus(
                available = false,
                errorMessage = message
            )
        }

        val invalidSpecs = specs.filter { spec ->
            val targetFile = resolveManifestTargetFile(downloadRoot, spec)
            !manifestDownloader.verifyIfNeeded(targetFile, spec.sha256)
        }
        if (invalidSpecs.isNotEmpty()) {
            val missingFileNames = invalidSpecs.map { it.fileName }.distinct()
            Log.w(
                VOICE_ASSISTANT_TAG,
                "Models missing or invalid: ${missingFileNames.joinToString()}"
            )
            return@withContext ModelAvailabilityStatus(
                available = false,
                missingFileNames = missingFileNames
            )
        }
        ModelAvailabilityStatus(available = true)
    }

    internal suspend fun downloadManifestModels(
        onProgress: ((ModelDownloadSpec, Int, Int, Long, Long?) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = AppContext.getInstance().context
            ?: return@withContext manifestDownloadFailure(
                "Manifest download failed: context unavailable"
            )
        val unresolvedSpecs = readManifestModels().getOrElse { e ->
            return@withContext manifestDownloadFailure(
                e.message ?: "Failed to read manifest.json for $llmFramework"
            )
        }
        val client = OkHttpClient()
        val specs = resolveManifestSpecSha256s(client, unresolvedSpecs).getOrElse { e ->
            return@withContext manifestDownloadFailure(
                e.message ?: "Failed to resolve model SHA-256 metadata for $llmFramework"
            )
        }
        if (specs.isEmpty()) {
            return@withContext manifestDownloadFailure(
                "No models defined in manifest.json for $llmFramework"
            )
        }

        val downloadRoot =
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        if (downloadRoot.isNullOrBlank()) {
            return@withContext manifestDownloadFailure("Model download path unavailable")
        }

        val pendingSpecs = specs.filter { spec ->
            val targetFile = resolveManifestTargetFile(downloadRoot, spec)
            !manifestDownloader.verifyIfNeeded(targetFile, spec.sha256)
        }

        pendingSpecs.forEachIndexed { index, spec ->
            val targetFile = resolveManifestTargetFile(downloadRoot, spec)
            val modelDownloadMessage =
                "Downloading model ${spec.fileName} -> ${targetFile.absolutePath}"
            Log.i(VOICE_ASSISTANT_TAG, modelDownloadMessage)
            val fileIndex = index + 1
            val fileCount = pendingSpecs.size
            onProgress?.invoke(spec, fileIndex, fileCount, 0L, null)
            val result = manifestDownloader.downloadModelFile(
                context = appContext,
                client = client,
                modelInfo = HuggingFaceModel(
                    id = "manifest",
                    modelId = "manifest",
                    filename = spec.fileName,
                    revision = spec.huggingFaceSource?.revision
                ),
                downloadPath = downloadRoot,
                spec = spec,
                listener = onProgress?.let {
                    object : ProgressListener {
                        override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
                            it(spec, fileIndex, fileCount, downloadedBytes, totalBytes)
                        }
                    }
                }
            )
            if (result.isFailure) {
                val exception = result.exceptionOrNull()
                    ?: IllegalStateException("Failed to download ${spec.fileName}")
                Log.e(VOICE_ASSISTANT_TAG, "Failed to download ${spec.fileName}", exception)
                return@withContext Result.failure(exception)
            }
            Log.i(VOICE_ASSISTANT_TAG, "Downloaded model ${spec.fileName}")
        }
        Result.success(Unit)
    }

    private fun manifestDownloadFailure(message: String): Result<Unit> {
        val exception = IllegalStateException(message)
        Log.e(VOICE_ASSISTANT_TAG, message, exception)
        return Result.failure(exception)
    }

    private fun resolveManifestTargetFile(
        downloadRoot: String,
        spec: ModelDownloadSpec
    ): File {
        val destination = spec.destination.trim().trimStart('/', '\\')
        return if (destination.isBlank()) {
            File(downloadRoot, spec.fileName)
        } else if (destination.endsWith("/") || destination.endsWith(File.separator)) {
            File(File(downloadRoot, destination), spec.fileName)
        } else {
            File(downloadRoot, destination)
        }
    }

    private fun readManifestModels(): Result<List<ModelDownloadSpec>> {
        val appContext = AppContext.getInstance().context
            ?: return Result.failure(IllegalStateException("Application context unavailable"))
        return runCatching {
            val manifestJson = appContext.assets.open("manifest.json")
                .bufferedReader()
                .use { it.readText() }
            val manifest = Json { ignoreUnknownKeys = true }
                .decodeFromString<Manifest>(manifestJson)
            resolveManifestDownloadSpecs(manifest, llmFramework).getOrThrow()
        }.onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to read manifest.json", e)
        }
    }

    private suspend fun resolveManifestSpecSha256s(
        client: OkHttpClient,
        specs: List<ModelDownloadSpec>
    ): Result<List<ModelDownloadSpec>> = runCatching {
        specs.map { spec ->
            manifestDownloader.resolveSha256IfNeeded(client, spec).getOrThrow()
        }
    }

    /**
     * Method to emit error status of jobs launched form pipeline
     * @param status error message to be displayed on toast
     */
    private suspend fun emitStatus(status: String) {
        pipelineErrorFlow.emit(status)
    }

    /**
     * Method to initialize the speech transcription instance
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun initializeSTT(modelPath: String) {

        runCatching {
            sttContext = stt.initContext(
                "$modelPath/${Constants.STT_MODEL_NAME}",
                sharedLibraryPath
            )
            val configTextWhisper = getAssetConfigText(configFileNameSTT)
            var whisperParams: WhisperConfig = if (configTextWhisper != null && isValidSttConfig(configTextWhisper)) {
                readSttUserConfig(configTextWhisper)
            } else {
                createSttDefaultConfig()
            }
            // Initialize stt parameters
            stt.initParameters(whisperParams)
        }.onFailure { e ->
            val msg = "Failed to initialize STT"
            Log.e(VOICE_ASSISTANT_TAG, msg, e)
            runBlocking {
                emitStatus(msg)
            }
        }
    }

    /**
     * Method to initialize a LLM instance in android pipeline from java.
     * @param modelPath path to llm model file or directory
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal  fun initializeLLM(modelPath: String) {
        val configText = getAssetConfigText(configFileName)
        val configurationString =
            if (configText != null && isValidLlmConfig(configText)) {
                readLlmUserConfig(configText, modelPath).toString()
            } else {
                Log.w(VOICE_ASSISTANT_TAG, "Using default config file to initialize LLM")
                createLlmDefaultConfig(modelPath, llmFramework).toString()
            }
        val initResult = runCatching {
            llm.llmInit(
                        configurationString,
                        sharedLibraryPath
             )
            llmInitialized = true
            speechFilePath = "$modelPath/${Constants.RESPONSE_FILE_NAME}"
            speechSynthesis.initSpeechSynthesis()
            // Set the pipeline for the TopBar (could not find a clean way of doing this)
            pipeline = this
        }
        initResult.onFailure { e ->
             if (configText != null && isValidLlmConfig(configText)) {
                 Log.e(VOICE_ASSISTANT_TAG, "Failed to initialize LLM using user provided config file", e)
             } else {
                 Log.w(VOICE_ASSISTANT_TAG, "User provided invalid config file for LLM")
             }
            val msg = LLM_INITIALIZATION_ERROR
            Log.e(VOICE_ASSISTANT_TAG, msg, e)
            runBlocking {
                emitStatus(msg)
            }
        }
    }

    /**
     * Recorder initialized
     * @return true if the recorder is ready to use, false otherwise
     */
    fun recorderInitialized(): Boolean {
        return speechRecorder.recorderInitialized()
    }

    /**
     * Start recording
     */
    suspend fun startRecording() {
        speechRecorder.startRecording()
    }

    /**
     * Stop recording
     * @param outputAudioFilePath The path where the recorded audio should be saved
     */
    fun stopRecording(outputAudioFilePath: String) {
        speechRecorder.stopRecording(outputAudioFilePath)
    }

    /**
     * Cancel recording
     */
    fun cancelRecording() {
        speechRecorder.cancelRecording()
    }

    /**
     * Generate speech
     * @param prompt The text to be spoken aloud by the assistant
     */
    fun generateSpeech(prompt: String) {
        speechSynthesis.generateSpeech(prompt)
    }

    /**
     * Transcribe audio file to string text
     * @param audioFile The path to the audio file to be transcribed
     * @return The transcribed text from the audio input
     */
    suspend fun transcribe(audioFile: String): String {
        return runCatching {
        timers.toggleSpeechRecTimer(true)
        val audioInputStream = FileInputStream(audioFile)
        val audioArray: FloatArray = reader.readWavData(audioInputStream)

            val transcribed = withContext(Dispatchers.Default) {
                stt.fullTranscribe(sttContext, audioArray)
        }
            Utils.removeTags(transcribed)
        }.onFailure { e ->
            val msg = "Failed to transcribe your query"
            Log.e(VOICE_ASSISTANT_TAG, msg, e)
            emitStatus(msg)
        }.also {
            // Always stop timer, even if success/failure
        timers.toggleSpeechRecTimer(false)
        }.getOrElse {
            "" // Fallback to empty string if failed
    }
    }

    /**
     * Return encode speed for backend LLM
     * @return The encoding speed in tokens per second
     */
    fun getEncodeTokensPerSec(): Float {
        return llm.encodeRate
    }
    /**
     * Return decode speed for backend LLM
     * @return The decoding speed in tokens per second
     */
    fun getDecodeTokensPerSec(): Float {
        return llm.decodeRate
    }

    /**
     * Return true if the llm has been initialized
     * @return true if the LLM is ready for use, false otherwise
     */
    fun llmInitialized(): Boolean {
        return llmInitialized
    }

    /**
     * Return true if speech synthesis has been initialized
     * @return true if the TTS system is ready to generate speech, false otherwise
     */
    fun speechSynthesisInitialized(): Boolean {
        return speechSynthesis.speechSynthesisInitialized()
    }

    /**
     * Cancel conversation
     */
    fun cancelSpeechSynthesis() {
        runBlocking {
            speechSynthesis.cancelSpeechSynthesis()
        }
    }

    /**
     * Return the timers object
     * @return The [PipelineTimers] object used in this pipeline
     */
    fun getTimers(): PipelineTimers {
        return timers
    }

    /**
     * Method sends the query to llm module and triggers Response process
     * @param query The user's input text
     */
    suspend fun generateResponse(query: String)
    {
        sendToLlm(query)
    }

    /**
     * Reset the context if active
     */
    fun resetContext() {
        runBlocking {
            llm.resetContext()
        }
        speechSynthesis.clearResponses()
    }

    /**
     * Start speech synthesis
     */
    fun startSpeechSynthesis() {
        speechSynthesis.startSpeechSynthesis()
    }

    /**
     * Wrapper Method to dispatch asynchronous query to Llm instance.
     * @param query : transcribed query string from speech
     */
    private suspend fun sendToLlm(query: String) = withContext(Dispatchers.Default) {
        runCatching {
            llmMutex.withLock {
                llm.submit(query)   // returns only after native workers finish
            }
        }.onFailure { e ->
            val msg = if (e.message!!.contains("context is full") or (getChatProgress() == 100)) {
                LLM_CONTEXT_CAPACITY_ERROR
            } else {
                LLM_QUERY_EVALUATION_ERROR
            }
            emitStatus(msg)
            Log.e(VOICE_ASSISTANT_TAG, msg, e)
        }
    }

    /**
     * Method to get percentage of a llm context
     * @return filled context as a whole number percentage .
     */
    suspend fun getChatProgress(): Int
    {
        llmMutex.withLock {
            return llm.getChatProgress()
        }
    }
    /**
     * Generate response tokens asynchronously and pass to the callback
     * @param transcription The user's transcribed input to be processed by the LLM
     */
    suspend fun generateResponseTokens(transcription: String) {
        lastImageEncodeJob?.join()
        sendToLlm(transcription)
    }

    /**
     * Finalize speech synthesis
     */
    suspend fun finalizeSpeechSynthesis() {
        speechSynthesis.finalizeSpeechSynthesis()
    }

    /**
     * Add words from llm to speech synthesis
     * @param tokens A string of words or tokens to be spoken aloud
     */
    fun addWordsToSpeechSynthesis(tokens: String) {
        speechSynthesis.addWordsToSpeechSynthesis(tokens)
    }

    /**
     * Return true if speech synthesis is in progress
     * @return true if synthesis is ongoing, false otherwise
     */
    fun speechSynthesisInProgress(): Boolean {
        return speechSynthesis.speechSynthesisInProgress()
    }

    fun supportsImageInput(): Boolean {
        // Probably need to break this out into a new ticket.
        // When this class is being used is a test, the test does not
        // init/config the llm instance, so when this method is subsequently called
        // it throws an exception and nearly all the RTVA tests fail.
        // We'll also need support the ability to test both supportsImageInput = true & false.
        return if (isTestMode) {
            true
        } else {
            llm.supportsImageInput()
        }
    }

    /**
     * Passes the original image to the LLM instance.
     * The LLM wrapper handles any model-specific image resizing during encoding.
     * @param originalResImage The original image at its original resolution.
     */
    fun addImageToLLmDialog(originalResImage: File) {
        runCatching {
            if (!originalResImage.exists() || !originalResImage.canRead()) {
                throw IllegalArgumentException("Image file does not exist or cannot be read: ${originalResImage.path}")
            }
            this.llm.setImageLocation(originalResImage.absolutePath)
            Log.i(VOICE_ASSISTANT_TAG, "file location is ${originalResImage.absolutePath}")

            lastImageEncodeJob = llmScope.launch {
                runCatching {
                    sendToLlm(query = "")
                }.onFailure { e ->
                    val msg = "Image embedding failed"
                    Log.e(VOICE_ASSISTANT_TAG, "$msg ${e.message}")
                    emitStatus(msg)
                }
            }
        } .onFailure { e ->
            Log.e(VOICE_ASSISTANT_TAG, LLM_IMAGE_ADD_ERROR, e)
            throw RuntimeException(LLM_IMAGE_ADD_ERROR)
        }
    }
}
