/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import com.arm.voiceassistant.huggingface.HuggingFaceModel
import com.arm.voiceassistant.utils.Constants.ContentStates
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE

/**
 * Main screen UI state.
 *
 * @property contentState State used to determine UI content to display.
 * @property error Error information.
 * @property userText Transcribed input from user.
 * @property responseText Text response from voice assistant.
 * @property imagePath Path to the selected image.
 * @property recTime Formatted duration of current recording.
 * @property recTimeMs Duration of current recording in ms.
 * @property playingAudio Whether speech response is playing.
 * @property displayPerformance Whether performance metrics should be displayed.
 * @property sttTime Whisper model time taken.
 * @property llmEncodeTPS LLM model encode tokens/second.
 * @property llmDecodeTPS LLM model decode tokens/second.
 * @property isTTSEnabled Whether TTS is enabled.
 * @property ttsWarningMessage Optional TTS warning message.
 */
data class MainUiState(
    val contentState: ContentStates = ContentStates.Idle,
    val error: Error = Error(),
    val userText: String = "",
    val responseText: String = "",
    val imagePath: String = "",
    val recTime: String = "00:00",
    val recTimeMs: Long = 0,
    val playingAudio: Boolean = false,
    val displayPerformance: Boolean = false,
    val sttTime: String = INITIAL_METRICS_VALUE,
    val llmEncodeTPS: String = INITIAL_METRICS_VALUE,
    val llmDecodeTPS: String = INITIAL_METRICS_VALUE,
    val isTTSEnabled: Boolean = false,
    val ttsWarningMessage: String? = null
)

/**
 * Download UI state.
 *
 * @property done Bytes downloaded so far (clamped to Int range).
 * @property total Total bytes expected for the download (clamped to Int range).
 * @property doneBytes Bytes downloaded so far.
 * @property totalBytes Total bytes expected for the active file, or `null`.
 * @property fileProgress Percentage 0..100, or `null` when unknown.
 * @property currentFileIndex One-based index of the model file currently downloading.
 * @property fileCount Number of model files that need downloading.
 * @property bytesPerSecond Current estimated download speed for the active file.
 * @property elapsedSeconds Elapsed seconds for the active file download.
 * @property estimatedSecondsRemaining Estimated seconds remaining for the active file, or `null`.
 * @property errorMessage Last download error shown to the user.
 * @property canStart Whether the UI should allow starting a download.
 * @property canCancel Whether the UI should allow canceling an in-progress download.
 * @property isRunning Whether a download is currently in progress.
 * @property finishedOk Whether the download completed successfully.
 */
data class DownloadUiState(
    val done: Int = 0,
    val total: Int = 0,
    val doneBytes: Long = 0L,
    val totalBytes: Long? = null,
    val fileProgress: Int? = null,
    val currentFile: String? = null,
    val currentModelKey: String? = null,
    val currentFileIndex: Int = 0,
    val fileCount: Int = 0,
    val bytesPerSecond: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val estimatedSecondsRemaining: Long? = null,
    val errorMessage: String? = null,
    val canStart: Boolean = true,
    val canCancel: Boolean = false,
    val isRunning: Boolean = false,
    val finishedOk: Boolean = false
)

/**
 * High-level model availability lifecycle for the mode selection UI.
 */
enum class ModelAvailabilityState {
    Unchecked,
    Checked,
    Downloading,
    Available
}

/**
 * Model availability UI state.
 *
 * @property state Current model availability lifecycle state.
 * @property missingModelNames All missing or invalid model files, when unavailable.
 * @property errorMessage Reason model availability could not be checked or resolved.
 */
data class ModelAvailabilityUiState(
    val state: ModelAvailabilityState = ModelAvailabilityState.Unchecked,
    val missingModelNames: List<String> = emptyList(),
    val errorMessage: String? = null
) {
    val checked: Boolean = state != ModelAvailabilityState.Unchecked
    val available: Boolean = state == ModelAvailabilityState.Available
}

/**
 * Class to hold error information.
 *
 * @property state True if there is an error.
 * @property contextCapacity True if the error is due to LLM context capacity.
 * @property message Error message to display.
 */
data class Error(
    val state: Boolean = false,
    val contextCapacity: Boolean = false,
    val message: String = ""
)
