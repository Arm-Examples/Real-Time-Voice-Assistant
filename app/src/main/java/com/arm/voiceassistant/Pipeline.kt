
/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.arm.Llm
import com.arm.stt.Whisper
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.audio.AudioReader
import com.arm.voiceassistant.speech.SpeechRecorder
import com.arm.voiceassistant.speech.SpeechSynthesis
import com.arm.voiceassistant.ui.composables.pipeline
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Constants.LLM_INITIALIZATION_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_CONTEXT_CAPACITY_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_QUERY_EVALUATION_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_IMAGE_ADD_ERROR
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.createLlmDefaultConfig
import com.arm.voiceassistant.utils.Utils.createWhisperDefaultConfig
import com.arm.voiceassistant.utils.Utils.isValidLlmConfig
import com.arm.voiceassistant.utils.Utils.isValidWhisperConfig
import com.arm.voiceassistant.utils.Utils.readLlmUserConfig
import com.arm.voiceassistant.utils.Utils.readWhisperUserConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


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

class Pipeline(modelPath: String, errorFlow: MutableSharedFlow<String>, isTest: Boolean = false, private val sharedLibraryPath: String = "") : AutoCloseable {
    private var timers = PipelineTimers()                    // Various timers needed
    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @get:androidx.annotation.VisibleForTesting
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

    // User config file for llm, default is llama.cpp
    private var configFileName: String = Utils.getLlmConfig(llmFramework)

    private var pipelineErrorFlow = errorFlow

    // User config file name stt
    private var configFileNameSTT = "whisperConfigUser.json"

    /**
     * Initialize speech recognition, large language model and speech synthesis
     */
    init {
        Log.i(VOICE_ASSISTANT_TAG,"Android Shared Library Path $sharedLibraryPath")

        if (!isTest) {
            initializeSTT(modelPath)
            initializeLLM(modelPath)
        }
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

                sttContext = stt.initContext("$modelPath/${Constants.STT_MODEL_NAME}")
                val configFileWhisper = File("$modelPath/$configFileNameSTT")
                var whisperParams = WhisperConfig()
                if (configFileWhisper.exists()) {
                    if (isValidWhisperConfig(configFileWhisper)) {
                        whisperParams = readWhisperUserConfig(configFileWhisper)
                    }
            } else {
                    whisperParams = createWhisperDefaultConfig()
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
        val configFile = File("$modelPath/$configFileName")
        val configurationString =
            if (configFile.exists() && isValidLlmConfig(configFile)) {
                readLlmUserConfig(configFile, modelPath).toString()
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
             if (configFile.exists()) {
                 if (isValidLlmConfig(configFile)) {
                     Log.e(VOICE_ASSISTANT_TAG, "Failed to initialize LLM using user provided config file", e)
                 }
             else
                 {
                     Log.w(VOICE_ASSISTANT_TAG, "User provided invalid config file for LLM")
                 }
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
     * Takes the original image, and resizes it to the config spec, and passes it to the LLM instance
     * @param originalResImage The original image at its original res
     * @param tempDirPath The path to the temp directory
     */
    fun addImageToLLmDialog(originalResImage: File, tempDirPath: String) {
        runCatching {
            // Max size of the larger Dim of an image, should handle both portrait and landscape images
            val maxDim = this.llm.maxInputImageDim
            val tempDirectoryFile = File(tempDirPath)

            val resizedImageFile = resizeImage(originalResImage, maxDim, tempDirectoryFile)
            if (resizedImageFile != null) {
                this.llm.setImageLocation(resizedImageFile.absolutePath)
            }
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

    /**
     * Resizes the given image file to fit within a maximum dimension
     * @param displayedImage The original image file to be resized.
     * @param maxDim The maximum width or height (whichever is larger) of the resized image.
     * @param tmpFileDir The directory where the resized temporary image file will be saved.
     * @return A temporary file containing the resized JPEG image.
     */
    private fun resizeImage(
        displayedImage: File,
        maxDim: Int,
        tmpFileDir: File
    ): File? {
        if (!displayedImage.exists() || !displayedImage.canRead()) {
            Log.e("ImageResize", "File does not exist or cannot be read: ${displayedImage.path}")
            return null
        }

        val decoded = BitmapFactory.decodeFile(displayedImage.absolutePath)
        if (decoded == null) {
            Log.e("ImageResize", "Failed to decode image: ${displayedImage.path}")
            return null
        }

        if (decoded.width <= 0 || decoded.height <= 0) {
            Log.e("ImageResize", "Invalid image dimensions: ${decoded.width}x${decoded.height}")
            return null
        }

        val ratio = decoded.width.toFloat() / decoded.height

        val (targetW, targetH) = if (decoded.width >= decoded.height) {
            maxDim to (maxDim / ratio).toInt()
        } else {
            (maxDim * ratio).toInt() to maxDim
        }
        val resized: Bitmap = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        val tempFile = File.createTempFile("tmp", displayedImage.name, tmpFileDir)
        FileOutputStream(tempFile).use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return tempFile
    }
}
