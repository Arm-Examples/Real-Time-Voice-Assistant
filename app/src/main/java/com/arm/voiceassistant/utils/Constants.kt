/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
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

    const val LLM_INITIALIZATION_ERROR = "LLM initialization failed due to config/model error"
    const val LLM_CONTEXT_CAPACITY_ERROR = "LLM Context capacity has filled, try resetting the context!"
    const val LLM_QUERY_EVALUATION_ERROR = "LLM query evaluation failed, try restarting the app!"
    const val LLM_IMAGE_ADD_ERROR = "Failed to add image query, try restarting"
    const val LLM_DECODE_ERROR = "Failed to decode Llm."
    const val PIPELINE_INIT_ERROR = "Failed to initialize the Voice assistant pipeline, Check configs and models"
    const val RESPONSE_JOB_IN_PROGRESS_ERROR = "Cannot start another response job before ending the current"
    const val MODEL_NOT_FOUND_ERROR = "Could not find a model for \"%s\""
    const val RECORD_PERMISSION_ERROR = "Need to grant permission to record!"
    const val AUD_REC_START_FAILED = "Unable to start recording. Please try again."
    const val AUD_REC_INPUT_ERROR = "Audio input error. Please retry."
    const val AUD_REC_SAVE_FAILED = "Failed to save audio recording."

}
