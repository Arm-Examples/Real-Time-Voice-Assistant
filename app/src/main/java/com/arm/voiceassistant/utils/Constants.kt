/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

/**
 * Holds global constants used throughout the app.
 */
object Constants {
    // Log tag
    const val VOICE_ASSISTANT_TAG = "VoiceAssistant"

    enum class ContentStates {
        Idle, Recording, Transcribing, Responding, Speaking, Cancelling
    }

    const val EOS = "<eos>"
    const val NEXT_MESSAGE = "</NextMessage>"


    const val MARKDOWN_CODE = "```"

    const val STT_MODEL_NAME = "model.bin"

    const val RESPONSE_FILE_NAME = "response.wav"

    const val INITIAL_METRICS_VALUE = "0.00"

    // Recording related, in ms
    const val MIN_ALLOWED_RECORDING : Long = 1100
}
