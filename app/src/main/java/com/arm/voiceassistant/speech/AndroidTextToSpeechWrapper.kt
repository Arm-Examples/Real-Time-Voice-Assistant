/*
 * SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.arm.voiceassistant.utils.Constants
import org.jetbrains.annotations.TestOnly
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android TTS wrapper
 * Generate the speech and save to an audio wav file to the output path parameter
 * @param context The application context used to initialize TTS
 */
class AndroidTTS(context: Context) : TextToSpeech.OnInitListener {
    var ttsInitialized = false
    var tts: TextToSpeech? = null
    var ttsInProgress = AtomicBoolean(false)
    private var onUtteranceDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    companion object {
        @Volatile
        private var instance: AndroidTTS? = null

        /**
         * Returns a singleton instance of [AndroidTTS].
         * @param context Context used to initialize TTS.
         * @return A shared instance of AndroidTTS.
         */
        fun getInstance(context: Context): AndroidTTS {
            return instance ?: synchronized(this) {
                instance ?: AndroidTTS(context).also { instance = it }
            }
        }
    }

    /**
     * Callback triggered when the Android TTS engine is initialized.
     * @param status Status code indicating success or failure.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            tts?.language = Locale.US
            setOnUtteranceProgressListener()
            Log.d("AndroidTTS", "Initialization success")
        } else {
            Log.e("AndroidTTS", "Initialization failed")
        }
    }

    // TODO Progress listener is used to ensure speech file saving completes.
    // This requires the onDone listener to be overridden. It is only used
    // once so it may be possible to place the countdown latch in the main definition here
    /**
     * Sets a listener to track the progress of TTS utterances.
     */
    private fun setOnUtteranceProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                Log.d(Constants.VOICE_ASSISTANT_TAG, "Utterance: $utteranceId")
                ttsInProgress.set(true)
            }

            override fun onDone(utteranceId: String) {
                Log.d(Constants.VOICE_ASSISTANT_TAG, "Android TTS Utterance completed: $utteranceId")
                ttsInProgress.set(false)
                onUtteranceDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                Log.e(
                    Constants.VOICE_ASSISTANT_TAG,
                    "Error with TTS utterance progress listener for: $utteranceId"
                )
                ttsInProgress.set(false)
                onUtteranceDoneCallback?.invoke()
            }
        })
    }

    /**
     * Store a callback that gets invoked when the utterance finishes successfully.
     * Used to let the tests know the utterance has completed.
     */
    @TestOnly
    fun setOnUtteranceDoneCallback(callback: (() -> Unit)?) {
        onUtteranceDoneCallback = callback
    }

    /**
     * Returns whether TTS is currently speaking.
     * @return `true` if the TTS engine is currently speaking, `false` otherwise.
     */
    fun isSpeaking():Boolean
    {
        return ttsInProgress.get()
    }
}
