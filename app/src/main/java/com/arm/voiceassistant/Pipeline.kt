
/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.arm.Llm
import com.arm.stt.Whisper
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.audio.AudioReader
import com.arm.voiceassistant.speech.SpeechRecorder
import com.arm.voiceassistant.speech.SpeechSynthesis
import com.arm.voiceassistant.subscribers.ResponseSubscriber
import com.arm.voiceassistant.ui.composables.pipeline
import com.arm.voiceassistant.utils.Constants
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
class Pipeline(modelPath: String, isTest: Boolean = false, private val sharedLibraryPath: String = "") {
    private var timers = PipelineTimers()                    // Various timers needed
    private var speechRecorder = SpeechRecorder()            // Audio recorder
    private var speechFilePath: String = ""                  // Path to the recorded audio file
    private var stt = Whisper()                              // Speech-to-text engine
    private var llm = Llm()                                  // Language model
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
    private var configFileName: String = when (llmFramework) {
        "llama.cpp" -> "llamaVisionConfigUser.json"
        "onnxruntime-genai" -> "onnxTextConfigUser.json"
        "mnn" -> "mnnVisionConfigUser.json"
        else -> "llamaVisionConfigUser.json"
    }
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
     * Method to initialize the speech transcription instance
     */
    private fun initializeSTT(modelPath: String) {

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
            throw RuntimeException("$msg: ${e.message}", e)
        }
    }

    /**
     * Method to initialize a LLM instance in android pipeline from java.
     * @param modelPath path to llm model file or directory
     */
    private fun initializeLLM(modelPath: String) {
        runCatching {
            // User llm config file
            val configFile = File("$modelPath/$configFileName")
            Log.d(VOICE_ASSISTANT_TAG, "LLM Config file: $modelPath/$configFileName")

            if (configFile.exists()) {
                runCatching {
                    // Read and check the given llm config file
                    if (isValidLlmConfig(configFile)) {
                        // Initialize the llm with user config file
                        llm.llmInit(
                            readLlmUserConfig(configFile, modelPath).toString(),
                            sharedLibraryPath
                        )
                        llmInitialized = true
                    }
                }.onFailure { e ->
                    Log.e(
                        VOICE_ASSISTANT_TAG,
                        "Model initialization with user config phase failed. Default configs will be created",
                        e
                    )
                }
            } else if ((!configFile.exists()) or (!llmInitialized)) {
                Log.w(
                    VOICE_ASSISTANT_TAG,
                    "Missing configuration file: ${configFileName}. Default configs will be created"
                )
                runCatching {
                    llm.llmInit(
                        createLlmDefaultConfig(modelPath, llmFramework).toString(),
                        sharedLibraryPath
                    )
                    llmInitialized = true
                }.onFailure { e->
                    val msg = "Failed to initialize llm from default config"
                    Log.e(VOICE_ASSISTANT_TAG, msg, e)
                    throw RuntimeException("$msg: ${e.message}", e)
                }
            }

            speechFilePath = "$modelPath/${Constants.RESPONSE_FILE_NAME}"
            speechSynthesis.initSpeechSynthesis()
            // Set the pipeline for the TopBar (could not find a clean way of doing this)
            pipeline = this

        }.onFailure { e ->
            val msg = "Model initialization phase failed"
            Log.e(VOICE_ASSISTANT_TAG, msg)
            throw RuntimeException(msg + e.toString())
        }
    }

    /**
     * Initialize the speech recorder to record the query
     */
    fun initRecorder() {
        speechRecorder.initRecorder()
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
            Log.e(VOICE_ASSISTANT_TAG, msg)
            throw RuntimeException(msg + e.toString())
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
     * @return The generated response from the LLM
     */
    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default)
    {
        val response: String = llm.send(query, true)
        return@withContext response
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
     * @param decode : Boolean to indicate whether Llm should decode the query .
     * If decode is false, the query would be embedded into Llm's context but Llm would not respond
     */
    private suspend fun sendToLlm(query: String, decode: Boolean) = withContext(Dispatchers.Default) {
        llmMutex.withLock {
            llm.sendAsync(query, decode)   // returns only after native workers finish
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
        sendToLlm(transcription, decode = true)
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

    /**
     * Sets the subscriber responsible for receiving streamed LLM token updates.
     * @param subscriber The [ResponseSubscriber] used to handle streaming LLM responses
     */
    fun setSubscriber(subscriber: ResponseSubscriber) {
        llm.setSubscriber(subscriber)
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

                sendToLlm(query = "", decode = false)
            }
        } .onFailure { e ->
            val msg = "Failed to add image query ,Try restarting"
            Log.e(VOICE_ASSISTANT_TAG, msg)
            throw RuntimeException(msg + e.toString())
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
