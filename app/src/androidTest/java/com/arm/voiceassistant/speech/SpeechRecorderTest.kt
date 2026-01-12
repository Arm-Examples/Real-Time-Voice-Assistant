/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.speech

import com.arm.voiceassistant.audio.AudioRecorder
import android.Manifest
import android.media.AudioRecord
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob



@RunWith(AndroidJUnit4::class)
class SpeechRecorderInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private fun newAudioScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Test
    fun doubleInitialization_isHandledSafely() {
        val speechRecorder = SpeechRecorder(newAudioScope())

        // Start recording the first time
        speechRecorder.startRecording()
        // Reflectively get the underlying AudioRecord instance
        val recorderField =
            SpeechRecorder::class.java.getDeclaredField("recorder").apply { isAccessible = true }

        val audioRecorder1 = recorderField.get(speechRecorder) as AudioRecorder
        val audioRecord1 = requireNotNull(audioRecorder1.audioRecord)
        assertTrue(audioRecord1.recordingState == AudioRecord.RECORDSTATE_RECORDING)

        // Call startRecording() again (while already recording)
        speechRecorder.startRecording()
        val audioRecorder2 = recorderField.get(speechRecorder) as AudioRecorder
        val audioRecord2 = requireNotNull(audioRecorder2.audioRecord)
        assertSame(audioRecord1, audioRecord2)

        // Cleanup
        speechRecorder.cancelRecording()
    }

    @Test
    fun release_canBeCalledMultipleTimesSafely() {
        val speechRecorder = SpeechRecorder(newAudioScope())

        // Initialize
        speechRecorder.initRecorder()
        assertTrue(speechRecorder.recorderInitialized())

        // Must be safe to call multiple times (no crash)
        speechRecorder.release()
        speechRecorder.release()

        // And it should still be usable (re-init happens in startRecording)
        speechRecorder.startRecording()
        speechRecorder.cancelRecording()
    }
}
