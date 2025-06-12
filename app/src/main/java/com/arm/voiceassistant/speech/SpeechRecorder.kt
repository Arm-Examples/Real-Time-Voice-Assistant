/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.speech

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import com.arm.voiceassistant.audio.AudioRecorder

/**
 * Speech recorder
 */
class SpeechRecorder {
    private lateinit var recorder: AudioRecorder
    private lateinit var record: AudioRecord

    /**
     * Initialize the recorder
     */
    @SuppressLint("MissingPermission")
    fun initRecorder() {
        record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioRecorder.SAMPLE_RATE, AudioRecorder.RECORDER_CHANNELS,
            AudioRecorder.RECORDER_AUDIO_ENCODING, 512
        )
        recorder = AudioRecorder(record)
    }

    /**
     * Check if the recorder has been initialized
     * @return `true` if the recorder is initialized, `false` otherwise.
     */
    fun recorderInitialized(): Boolean {
        return ::recorder.isInitialized
    }

    /**
     * Start recording
     */
    suspend fun startRecording() {
        initRecorder()
        recorder.startRecording()
    }

    /**
     * Stop recording
     * @param outputAudioFilePath The full path to save the recorded WAV file.
     */
    fun stopRecording(outputAudioFilePath: String) {
        recorder.stopRecording()
        recorder.writeToFile(outputAudioFilePath)
    }

    /**
     * Cancel recording
     */
    fun cancelRecording() {
        recorder.stopRecording()
        recorder.audioData.clear()
    }
}
