/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.composables.ActionButton
import com.arm.voiceassistant.ui.composables.ConfirmationDialog
import com.arm.voiceassistant.ui.composables.ConversationText
import com.arm.voiceassistant.ui.composables.ErrorSnackbar
import com.arm.voiceassistant.ui.composables.ModelMetrics
import com.arm.voiceassistant.ui.composables.userTextFieldDefaults
import com.arm.voiceassistant.ui.composables.voiceAssistantTextFieldDefaults
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.viewmodels.MainUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

// Definition of the main application screen

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    state: MainUiState = MainUiState(),
    onClickStartRecording: () -> Unit = {},
    onClickStopRecording: () -> Unit = {},
    onClickCancelRecording: () -> Unit = {},
    onClickCancel: () -> Unit = {},
    onError: (String) -> Unit = {},
    clearErrorMsg: () -> Unit = {},
    recordingPermissionState: PermissionState =
        rememberPermissionState(Manifest.permission.RECORD_AUDIO)
) {
    var openConfirmationDialog by remember { mutableStateOf(false) }

    // Display error
    if (state.error.state) {
        ErrorSnackbar(
            snackbarHostState = snackbarHostState,
            message = state.error.message,
            onDismiss = clearErrorMsg
        )
    }

    // Display confirmation dialog for cancelling recording
    if (openConfirmationDialog) {
        ConfirmationDialog(
            onDismissRequest = { openConfirmationDialog = false },
            onConfirmation = {
                onClickCancelRecording()
                openConfirmationDialog = false
            },
            dialogText = "Cancel the current recording?"
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            wasGranted ->
        if (wasGranted) onClickStartRecording()
        else onError("Need to grant permission to record!")
    }

    Column(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.primary)
    ) {

        if (state.displayPerformance) {
            ModelMetrics(
                model1metric = state.sttTime,
                model2metric = state.llmEncodeTPS,
                model3metric = state.llmDecodeTPS
            )
        }

        val backgroundModifier =
            if (isSystemInDarkTheme()) {
                Modifier.background(color = MaterialTheme.colorScheme.primary)
            } else {
                Modifier.background(brush = Brush.verticalGradient(listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.inversePrimary)))
            }

        Column(
            modifier = backgroundModifier
                .padding(start = 10.dp, end = 10.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    // Fill remaining space
                    .weight(1f)
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Reduce aspect ratio if displaying performance metrics
                val cardAspectRatio = if (state.displayPerformance) 1.35f else 1.1f

                ConversationText(
                    cardAspectRatio = cardAspectRatio,
                    textFieldContentDescription = "user_input",
                    label = userTextFieldDefaults().label,
                    text = state.userText,
                    isTalking = state.contentState == Constants.ContentStates.Recording,
                    isTranscribing = state.contentState == Constants.ContentStates.Transcribing,
                    isVoiceAssistant = false,
                    shape = userTextFieldDefaults().shape,
                    colors = userTextFieldDefaults().colors
                )

                Spacer(modifier = Modifier.height(15.dp))

                ConversationText(
                    cardAspectRatio = cardAspectRatio,
                    textFieldContentDescription = "response_output",
                    label = voiceAssistantTextFieldDefaults().label,
                    text = state.responseText,
                    isTalking = state.contentState == Constants.ContentStates.Speaking,
                    playingAudio = state.playingAudio,
                    isVoiceAssistant = true,
                    shape = voiceAssistantTextFieldDefaults().shape,
                    colors = voiceAssistantTextFieldDefaults().colors
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ActionButton(
                onClickStartRecording = {
                    when (recordingPermissionState.status) {
                        PermissionStatus.Granted ->
                            onClickStartRecording()
                        else ->
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onClickStopRecording = { onClickStopRecording() },
                onClickCancelRecording = { openConfirmationDialog = true },
                onClickCancel = { onClickCancel() },
                contentState = state.contentState,
                timerText = state.recTime,
                animateIcon = state.contentState == Constants.ContentStates.Recording
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

}

@OptIn(ExperimentalPermissionsApi::class)
class PermissionStatePreview(
    override val permission: String = Manifest.permission.RECORD_AUDIO,
    override val status: PermissionStatus = PermissionStatus.Granted
) : PermissionState {
    override fun launchPermissionRequest() {}
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    VoiceAssistantTheme {
        MainScreen(recordingPermissionState = PermissionStatePreview())
    }
}
