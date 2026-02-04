/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.composables.*
import com.arm.voiceassistant.utils.ChatMessage
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState


/**
 * Main chat screen for the voice assistant.
 *
 * Displays chat history, system metrics, and user/assistant messages,
 * manages recording permissions, and wires UI actions to [MainViewModel].
 *
 * @param modifier Optional modifier for layout customization
 * @param viewModel [MainViewModel] providing chat state and actions
 * @param snackbarHostState Host state for error and status snackbars
 * @param recordingPermissionState Permission state for audio recording
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    recordingPermissionState: PermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO),
) {
    val uiState by viewModel.uiState.collectAsState()
    val messages = viewModel.messages
    val listState = rememberLazyListState()

    var currentToast by remember { mutableStateOf("") }
    var openConfirmationDialog by remember { mutableStateOf(false) }
    var openExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.responseText, messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
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

    if (uiState.error.state) {
        ErrorSnackbar(
            snackbarHostState = snackbarHostState,
            message = uiState.error.message,
            onDismiss = { viewModel.clearError() }
        )
    }

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

    if (openExitDialog) {
        AlertDialog(
            onDismissRequest = { openExitDialog = false },
            title = { Text("Leave chat?") },
            text = { Text("A request is still running. Leaving now will cancel it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        openExitDialog = false
                        runCatching { viewModel.llmBridge.cancel() }
                        viewModel.cancelPipeline()
                    }
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { openExitDialog = false }) { Text("Stay") }
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onStartRecording()
        else viewModel.onError(Constants.RECORD_PERMISSION_ERROR)
    }

    val background = if (isSystemInDarkTheme()) {
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

    Scaffold(
        modifier = modifier.then(background).fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top status strip (icons only) - replaces old performance toggle area
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                    tonalElevation = 0.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        ModelMetrics() // one-line icon row (memory + thermal)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chat history
                if (messages.size == 1 && messages.first() is ChatMessage.AssistantText) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        val msg = messages.first() as ChatMessage.AssistantText
                        Column {
                            AssistantBubble(text = msg.text)
                            msg.timing?.let { AssistantTimingFooter(it) }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message ->
                            when (message) {
                                is ChatMessage.UserText -> MessageItem(label = "User", isUser = true) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        UserBubble(text = message.text)
                                        message.timing?.let { UserTimingFooter(it) }
                                    }
                                }

                                is ChatMessage.UserImage -> MessageItem(label = "User", isUser = true) {
                                    UserImageBubble(uri = message.uri)
                                }

                                is ChatMessage.AssistantText -> MessageItem(label = "Voice Assistant", isUser = false) {
                                    Column {
                                        AssistantBubble(text = message.text)
                                        message.timing?.let { AssistantDecodeFooter(it) }
                                    }
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
                    onClickCancel = {
                        viewModel.llmBridge.cancel()
                        viewModel.cancelPipeline()
                    },
                    onAddImage = { uri -> viewModel.addImage(uri) },
                    showImageButton = viewModel.imageUploadEnabled
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (currentToast.isNotEmpty()) {
                TopToast(
                    message = currentToast,
                    onDismiss = { currentToast = "" },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )
            }
        }
    }
}
