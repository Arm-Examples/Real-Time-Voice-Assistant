/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.os.SystemClock

/**
 * Simple Timer class
 * Uses system clock to get actual elapsed time.
 */
class Timer {

    private var duration = 0L
    private var initialTime = 0L
    var isRunning = false

    /**
     * Starts the timer by recording the current elapsed real-time.
     */
    fun start() {
        initialTime = SystemClock.elapsedRealtime()
        isRunning = true
    }

    /**
     * Stops the timer and calculates the elapsed duration since it was started.
     */
    fun stop() {
        duration = (SystemClock.elapsedRealtime() - initialTime)
        isRunning = false
    }

    /**
     * Resets the timer to its initial state without storing any duration.
     */
    fun reset() {
        initialTime = 0L
        isRunning = false
    }

    /**
     * Returns the current elapsed time in milliseconds.
     */
    val elapsedTime: Long
        get() {
            return if (isRunning) {
                (SystemClock.elapsedRealtime() - initialTime)
            } else {
                duration
            }
        }

    /**
     * Return the elapsed time as a nice looking string.
     * @return Formatted time string representing elapsed duration
     */
    fun format(): String {
        val seconds: Long = (elapsedTime / 1000) % 60
        val minutes: Long = (elapsedTime / (1000 * 60)) % 60
        val hours: Long = (elapsedTime / (1000 * 60 * 60))

        return if (hours > 0)
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        else
            "%02d:%02d".format(minutes, seconds)
    }
}
