/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.utils.Constants


/**
 * Row layout that combines the main voice action button and image upload button.
 * @param modifier Layout modifier
 * @param contentState Current state of the app (e.g. recording, idle)
 * @param timerText Timer shown while recording
 * @param animateIcon Whether to animate the mic icon
 * @param onClickStartRecording Called when recording starts
 * @param onClickStopRecording Called when recording stops
 * @param onClickCancelRecording Called when canceling during recording
 * @param onClickCancel Called when canceling processing
 * @param onAddImage Called when a new image is uploaded
 */
@Composable
fun CombinedActionRow(
    modifier: Modifier = Modifier,
    contentState: Constants.ContentStates,
    timerText: String = "00:00",
    animateIcon: Boolean = false,
    onClickStartRecording: () -> Unit = {},
    onClickStopRecording: () -> Unit = {},
    onClickCancelRecording: () -> Unit = {},
    onClickCancel: () -> Unit = {},
    onAddImage: (Uri) -> Unit = {},
    showImageButton: Boolean
) {
    val isRecording = contentState == Constants.ContentStates.Recording

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Voice Button – takes full width when recording
        Box(modifier = Modifier.weight(1f)) {
            ActionButton(
                modifier = Modifier.fillMaxSize(),
                contentState = contentState,
                timerText = timerText,
                animateIcon = animateIcon,
                onClickStartRecording = onClickStartRecording,
                onClickStopRecording = onClickStopRecording,
                onClickCancelRecording = onClickCancelRecording,
                onClickCancel = onClickCancel
            )
        }

        // Only show image upload button if NOT recording
        AnimatedVisibility(visible = !isRecording && showImageButton) {
            Spacer(modifier = Modifier.width(10.dp)) // Add spacing manually between buttons

            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(60.dp)
            ) {
                UploadImageActionButton(onAddImage)
            }
        }
    }
}

/**
 * Combined action preview
 */
@Preview(showBackground = true)
@Composable
fun CombinedActionRowPreview() {
    VoiceAssistantTheme {
        CombinedActionRow(
            contentState = Constants.ContentStates.Idle,
            timerText = "00:00",
            animateIcon = false,
            onClickStartRecording = {},
            onClickStopRecording = {},
            onClickCancelRecording = {},
            onClickCancel = {},
            onAddImage = {},
            showImageButton = false
        )
    }
}

/**
 * Combined action in recording preview
 */
@Preview(showBackground = true, name = "Recording State")
@Composable
fun CombinedActionRowRecordingPreview() {
    VoiceAssistantTheme {
        CombinedActionRow(
            contentState = Constants.ContentStates.Recording,
            timerText = "00:42",
            animateIcon = true,
            showImageButton = false
        )
    }
}
