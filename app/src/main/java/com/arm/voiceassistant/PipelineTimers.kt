/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import android.util.Log
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Timer

/**
 * Container for the pipeline timers
 */
class PipelineTimers {

    private var speechRecTimer: Timer = Timer()     // Timer tracking how long speech recognition takes
    private var realTimer: Timer = Timer()          // End of speech to first synthesized speech word
    private var firstResponseTimer: Timer = Timer() // LLM start to first token response

    /**
     * Toggle the first response timer
     * @param start Whether to start (`true`) or stop (`false`) the timer.
     * @param dump If `true`, logs the timer result when stopping.
     */
    fun toggleFirstResponseTimer(start: Boolean, dump: Boolean) {
        toggleTimer(start, firstResponseTimer)
        if (dump) {
            // Log response time from start of LLM to when first response comes back
            dumpTimer(firstResponseTimer, "first response time")
        }
    }

    /**
     * Dump out timer information
     * @param timer The timer instance to log.
     * @param msg A tag or label to describe the log message.
     */
    private fun dumpTimer(timer: Timer, msg: String) {
        val seconds = if (timer.elapsedTime > 0) { timer.elapsedTime/1000f } else { 0.0f }
        Log.d(Constants.VOICE_ASSISTANT_TAG, "${msg}=${seconds}")
        timer.reset()
    }

    /**
     * Toggle the real timer
     * @param start Whether to start (`true`) or stop (`false`) the timer.
     * @param dump If `true`, logs the timer result when stopping.
     */
    fun toggleRealTimer(start: Boolean, dump: Boolean) {
        toggleTimer(start, realTimer)
        if (dump) {
            dumpTimer(realTimer, "real time")
        }
    }

    /**
     * Toggle timer
     * @param start Whether to start (`true`) or stop (`false`) the timer.
     * @param timer The timer to toggle.
     */
    private fun toggleTimer(start: Boolean, timer: Timer) {
        if (start) {
            timer.start()
        } else {
            timer.stop()
        }
    }

    /**
     * Toggle the speech rec timer
     * @param start Whether to start (`true`) or stop (`false`) the timer.
     */
    fun toggleSpeechRecTimer(start: Boolean) {
        toggleTimer(start, speechRecTimer)
    }

    /**
     * Return speech recognition time
     * @return The speech recognition time in seconds as a float.
     */
    fun getSpeechRecognitionTime() : Float {
        val speechRecognitionTimeSeconds = if (speechRecTimer.elapsedTime > 0) { speechRecTimer.elapsedTime/1000f } else { 0.0f }
        return speechRecognitionTimeSeconds
    }
}
