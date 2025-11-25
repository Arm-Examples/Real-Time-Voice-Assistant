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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.ui.composables.*
import com.arm.voiceassistant.utils.ChatMessage
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Message item composable.
 * @param label of the message
 * @param isUser true if user, false otherwise
 * @param bubble UI composable used for this message item
 */
@Composable
private fun MessageItem(
    label: String,
    isUser: Boolean,
    bubble: @Composable () -> Unit
) {
    val align = if (isUser) Alignment.End else Alignment.Start
    val labelMod = if (isUser) Modifier.padding(end = 12.dp, bottom = 4.dp)
    else Modifier.padding(start = 12.dp, bottom = 4.dp)

    Column(horizontalAlignment = align) {
        Text(
            text = label,
            fontSize = 16.sp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = labelMod
        )
        bubble()
    }
}
/**
 * Main UI screen for the Voice Assistant app.
 * @param modifier modifier for padding and background of Main UI screen
 * @param viewModel The shared [MainViewModel] managing app state and actions
 * @param snackbarHostState Snackbar state for showing errors or alerts
 * @param recordingPermissionState Permission state for microphone access
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    recordingPermissionState: PermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
) {
    // Collect state
    val uiState by viewModel.uiState.collectAsState()
    val messages = viewModel.messages
    val toastFlow = viewModel.toastMessages
    var currentToast by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.responseText, messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            messages.add(ChatMessage.AssistantText("Hi!\nI'm your AI assistant. How can I help you?"))
        }
    }


    LaunchedEffect(Unit) {
        viewModel.toastMessages.collect { message ->
            currentToast = message
        }
    }

    if (currentToast.isNotEmpty()) {
        TopToast(
            message = currentToast,
            onDismiss = { currentToast = "" }
        )
    }


    var openConfirmationDialog by remember { mutableStateOf(false) }

    // Error Snackbar
    if (uiState.error.state) {
        ErrorSnackbar(
            snackbarHostState = snackbarHostState,
            message = uiState.error.message,
            onDismiss = { viewModel.clearError() }
        )
    }

    // Cancel recording confirmation
    if (openConfirmationDialog) {
        ConfirmationDialog(
            onDismissRequest = { openConfirmationDialog = false },
            onConfirmation = {
                viewModel.cancelRecording()
                openConfirmationDialog = false
            },
            dialogText = "Cancel the current recording?"
        )
    }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onStartRecording()
        else viewModel.onError("Need to grant permission to record!")
    }

    // Themed background
    val backgroundModifier = if (isSystemInDarkTheme()) {
        Modifier.background(color = MaterialTheme.colorScheme.primary)
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.inversePrimary
                )
            )
        )
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = backgroundModifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.displayPerformance) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp)
                        .height(IntrinsicSize.Min)
                ) {
                    ModelMetrics(
                        model1metric = uiState.sttTime,
                        model2metric = uiState.llmEncodeTPS,
                        model3metric = uiState.llmDecodeTPS
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Chat history
            if (messages.size == 1 && messages.first() is ChatMessage.AssistantText) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Voice Assistant",
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                    )

                    AssistantBubble(text = (messages.first() as ChatMessage.AssistantText).text)
                }
            } else {
                // Normal message history
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    items(messages /*, key = { it.id } if you have one */) { message ->
                        when (message) {
                            is ChatMessage.UserText -> MessageItem(label = "User", isUser = true) {
                                UserBubble(text = message.text)
                            }

                            is ChatMessage.UserImage -> MessageItem(label = "User", isUser = true) {
                                UserImageBubble(uri = message.uri)
                            }

                            is ChatMessage.AssistantText -> MessageItem(
                                label = "Voice Assistant",
                                isUser = false
                            ) {
                                AssistantBubble(text = message.text)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            CombinedActionRow(
                contentState = uiState.contentState,
                timerText = uiState.recTime,
                animateIcon = uiState.contentState == Constants.ContentStates.Recording,
                onClickStartRecording = {
                    when (recordingPermissionState.status) {
                        PermissionStatus.Granted -> viewModel.onStartRecording()
                        else -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onClickStopRecording = { viewModel.onStopRecording() },
                onClickCancelRecording = { openConfirmationDialog = true },
                onClickCancel = { viewModel.cancelPipeline() },
                onAddImage = { uri -> viewModel.addImage(uri) },
                showImageButton = viewModel.imageUploadEnabled
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
        if (currentToast.isNotEmpty()) {
            TopToast(
                message = currentToast,
                onDismiss = { currentToast = "" },
                modifier = Modifier
                    .align(Alignment.TopCenter)
            )
        }
    }

}
