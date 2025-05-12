/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import android.util.Log
import com.arm.llm.Llama
import com.arm.stt.Whisper
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.audio.AudioReader
import com.arm.voiceassistant.speech.SpeechRecorder
import com.arm.voiceassistant.speech.SpeechSynthesis
import com.arm.voiceassistant.subscribers.ResponseSubscriber
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.createDefaultConfig
import com.arm.voiceassistant.utils.Utils.createWhisperDefaultConfig
import com.arm.voiceassistant.utils.Utils.isValidConfig
import com.arm.voiceassistant.utils.Utils.readUserConfig
import com.arm.voiceassistant.utils.Utils.readWhisperUserConfig
import com.arm.voiceassistant.utils.Utils.isValidWhisperConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.lang.Exception
import java.io.File


class Pipeline(modelPath: String, isTest: Boolean = false) {

    // The pipeline contains multiple components for handling the interaction with the user

    // Various timers needed
    private var timers = PipelineTimers()

    // Audio recorder
    private var speechRecorder = SpeechRecorder()

    private var speechFilePath: String = ""

    private var stt = Whisper()

    private var llama = Llama()

    private var llmInitialized = false

    private var sttContext = 0L

    private val reader = AudioReader()

    // Generator of speech
    private var speechSynthesis = SpeechSynthesis()

    // User config file name
    private var configFileName = "llamaConfigUser.json"

    // User config file name stt
    private var configFileNameSTT = "whisperConfigUser.json"

    /**
     * Initialize speech recognition, large language model and speech synthesis
     */
    init {
        if (!isTest) {
            try {
                sttContext = stt.initContext("$modelPath/${Constants.STT_MODEL_NAME}")

                val configFileWhisper = File("$modelPath/$configFileNameSTT")
                var whisperParams = WhisperConfig()
                if (configFileWhisper.exists()) {
                    if (isValidWhisperConfig(configFileWhisper)) {
                        whisperParams = readWhisperUserConfig(configFileWhisper)
                    }
                }
                else{
                    whisperParams = createWhisperDefaultConfig()
                }
                // Initialize stt parameters
                stt.initParameters(whisperParams);

                // User llm config file
                val configFile = File("$modelPath/$configFileName")

                if (configFile.exists()) {
                    try {
                        // Read and check the given llm config file
                        if (isValidConfig(configFile)) {
                            // Initialize the llm with user config file
                            llama.llmInit(readUserConfig(configFile, modelPath))
                            llmInitialized = true
                        }
                    } catch (e: Exception) {
                        Log.w(VOICE_ASSISTANT_TAG, "Model initialization with llamaConfigUser.txt phase failed. Default configs will be created", e)
                    }
                }
                else {
                    Log.w(VOICE_ASSISTANT_TAG, "Missing configuration file: ${configFileName}. Default configs will be created")
                }

                if(!llmInitialized)
                {
                    // If user llm can't be initialized with user config file, initialize with the default config file
                    llama.llmInit(createDefaultConfig(modelPath))
                }

                llmInitialized = true

                speechFilePath = "$modelPath/${Constants.RESPONSE_FILE_NAME}"
                speechSynthesis.initSpeechSynthesis()

                // Set the pipeline for the TopBar (could not find a clean way of doing this)
                com.arm.voiceassistant.ui.composables.pipeline = this
            } catch (e: Exception) {
                Log.e(VOICE_ASSISTANT_TAG, "Model initialization phase failed", e)
            }
        }
    }

    /**
     * Init recorder
     */
    fun initRecorder() {
        speechRecorder.initRecorder()
    }

    /**
     * Recorder initialized
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
     */
    fun generateSpeech(prompt: String) {
        speechSynthesis.generateSpeech(prompt)
    }

    /**
     * Transcribe audio file to string text
     */
    suspend fun transcribe(audioFile: String): String {
        timers.toggleSpeechRecTimer(true)
        val audioInputStream = FileInputStream(audioFile)
        val audioArray: FloatArray = reader.readWavData(audioInputStream)
        var transcribed: String
        withContext(Dispatchers.Default) {
            transcribed = stt.fullTranscribe(sttContext, audioArray)
        }
        val sanitizedTranscribed = Utils.removeTags(transcribed)
        timers.toggleSpeechRecTimer(false)
        return sanitizedTranscribed
    }

    /**
     * Return encode speed for backend LLM
     */
    fun getEncodeTokensPerSec(): Float {
        return llama.encodeRate
    }

    /**
     * Return decode speed for backend LLM
     */
    fun getDecodeTokensPerSec(): Float {
        return llama.decodeRate
    }

    /**
     * Return true if the llm has been initialized
     */
    fun llmInitialized(): Boolean {
        // check if llm module was initialized
        return llmInitialized
    }

    /**
     * Return true if speech synthesis has been initialized
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
     */
    fun getTimers(): PipelineTimers {
        return timers
    }

    /**
     * Method sends the query to llm module and triggers Response process
     */
    suspend fun generateResponse(query: String): String = withContext(Dispatchers.Default)
    {
        val response: String = llama.send(query)
        return@withContext response
    }

    /**
     * Reset the context if active
     */
    fun resetContext() {
        runBlocking {
            llama.resetContext()
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
     * Generate response tokens asynchronously and pass to the callback
     */
    suspend fun generateResponseTokens(transcription: String) {
        withContext(Dispatchers.Default) {
            llama.sendAsync(transcription)
        }
    }

    /**
     * Finalize speech synthesis
     */
    suspend fun finalizeSpeechSynthesis() {
        speechSynthesis.finalizeSpeechSynthesis()
    }

    /**
     * Add words from llm to speech synthesis
     */
    fun addWordsToSpeechSynthesis(tokens: String) {
        speechSynthesis.addWordsToSpeechSynthesis(tokens)
    }

    /**
     * Return true if speech synthesis is in progress
     */
    fun speechSynthesisInProgress(): Boolean {
        return speechSynthesis.speechSynthesisInProgress()
    }

    fun setSubscriber(subscriber: ResponseSubscriber) {
        llama.setSubscriber(subscriber)
    }
}
