/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD
import android.util.Log
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.breakSentence
import com.arm.voiceassistant.utils.Utils.breakSentenceAtPeriod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly
import java.lang.Exception

/**
 * Speech synthesis
 */
class SpeechSynthesis {
    private lateinit var androidTTS: AndroidTTS
    private var speechSynthesisMutex = Mutex()
    private var speechSynthesisStarted = false
    private var responses = arrayListOf<String>()
    private var context = AppContext.getInstance().context
    private var responsesProcessed = 0

    /**
     * Initialize the speech synthesis object
     */
    fun initSpeechSynthesis() {
        if (context != null) {
            try {
                androidTTS = AndroidTTS.getInstance(context!!)
            }
            catch (e: Exception) {
                Log.e(Constants.VOICE_ASSISTANT_TAG, "Speech Synthesizer initialization phase failed", e)
            }
        }
    }
    /**
     * Sets a custom context for testing purposes.
     * @param testContext The mock context to use in tests.
     */
    @TestOnly
    fun setContext(testContext: Context) {
        this.context = testContext
    }

    /**
     * Start speech synthesis
     */
    fun startSpeechSynthesis() {
        responses.clear()
        responsesProcessed = 0
        speechSynthesisStarted = true
    }

    /**
     * Finalize speech synthesis
     */
    suspend fun finalizeSpeechSynthesis() {
        speechSynthesisMutex.withLock {
            if (speechSynthesisStarted) {
                if (responsesProcessed < (responses.size)) {
                    processCurrentSpeechBlock()
                }
                launchFinalize()
            }
            speechSynthesisStarted = false
        }
    }

    /**
     * Blocks execution until Android TTS finishes speaking.
     */
    private fun launchFinalize() {
        // wait for android-TTS speaking to complete
        while (androidTTS.isSpeaking()) {

        }
    }

    /**
     * Cancels ongoing speech synthesis in a thread-safe manner.
     */
    suspend fun cancelSpeechSynthesis() {
        speechSynthesisMutex.withLock {
            speechSynthesisStarted = false
        }
    }

    /**
     * Generate speech from given prompt and add
     * the corresponding (generation and playback job) to a Queue
     * @param prompt the response from llm to be played on speaker
     */
    fun generateSpeech(prompt: String) {
        val params = Bundle()
        val utteranceId =
            "synthesize_${prompt.hashCode()}" // Generate a unique ID with prefix for synthesizeToFile to allow tracking
        val appContext = AppContext.getInstance().context

        if (appContext != null) {
            androidTTS = AndroidTTS.getInstance(appContext)
        } else {
            Log.e(Constants.VOICE_ASSISTANT_TAG, "Null context used to initialize AndroidTTS")
        }

        if (androidTTS.ttsInitialized) {
            val speechStatus = androidTTS.tts?.speak(prompt, QUEUE_ADD, params, utteranceId)
            if (speechStatus == TextToSpeech.ERROR) {
                Log.e(Constants.VOICE_ASSISTANT_TAG, "Queuing the Speak Job failed")
            }
        }
    }

    /**
     * Return true if speech synthesis has been initialized
     * @return True if the Android TTS engine has been initialized successfully.
     */
    fun speechSynthesisInitialized(): Boolean {
        return androidTTS.ttsInitialized
    }


    /**
     * Add words to speech synthesis
     * @param tokens The new words to add to the speech response queue.
     */
    fun addWordsToSpeechSynthesis(tokens: String) {
        if (responses.isEmpty()) {
            responses.add("")
        }
        // Start a new line if the sentence is being broken
        val breaking = breakSentenceAtPeriod(responses, tokens)
        if (breaking) {
            processCurrentSpeechBlock()
            responsesProcessed++
            responses.add("")
        }

        var words = responses.last()
        words = "$words$tokens"
        responses[responses.size - 1] = words

        // Start a new line if the sentence is being broken
        if ((!breaking) && breakSentence(tokens, words)) {
            processCurrentSpeechBlock()
            responsesProcessed++
            responses.add("")
        }
    }

    /**
     * Process the current speech synthesis response line. This function must be called
     * within a speechSynthesisMutex.withLock {} block
     */
    private fun processCurrentSpeechBlock() {
        val words = responses.last()
        val sanitizedWords = Utils.cleanupLine(responses, words)
        generateSpeech(sanitizedWords)
    }

    /**
     * Return true if speech synthesis in progress
     * @return True if a speech synthesis session is currently active.
     */
    fun speechSynthesisInProgress(): Boolean {
        return speechSynthesisStarted
    }

    /**
     * Clear the responses list
     */
    fun clearResponses() {
        responses.clear()
    }

    /**
     * Method to check speechSynthesis object state
     * @return True if speech synthesis has started.
     */
    fun speechSynthesisStarted(): Boolean {
        return this.speechSynthesisStarted
    }

    /**
     * Method to stop speech Synthesis job
     */
    fun cancel() {
        this.androidTTS.tts?.stop()
    }

}
