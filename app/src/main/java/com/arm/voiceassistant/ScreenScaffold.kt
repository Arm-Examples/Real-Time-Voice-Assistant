/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arm.voiceassistant.ui.composables.DownloadProgressContent
import com.arm.voiceassistant.ui.composables.TopBar
import com.arm.voiceassistant.ui.screens.BenchmarkScreen
import com.arm.voiceassistant.ui.screens.MainScreen
import com.arm.voiceassistant.ui.screens.ModeSelectionScreen
import com.arm.voiceassistant.utils.ModelAvailabilityState
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.arm.voiceassistant.viewmodels.ViewModelProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fullscreen loading indicator shown while the chat pipeline is initializing.
 */
@VisibleForTesting
@Composable
internal fun ChatLoadingScreen(
    modifier: Modifier = Modifier,
    statusMessage: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * High-level application modes used for top-level navigation.
 */
private enum class AppMode {
    ModeSelection,
    Chat,
    Benchmark
}

/**
 * Top-level scaffold that sets up the main app structure.
 *
 * @param snackbarHostState Host state for showing snackbars
 * @param mainViewModel Shared ViewModel passed to navigation and top bar
 * @return The provided or created [MainViewModel] instance
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun screenScaffold(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    mainViewModel: MainViewModel = viewModel(factory = ViewModelProvider.Factory)
): MainViewModel {
    val uiState = mainViewModel.uiState.collectAsState().value
    val modelAvailabilityUiState by mainViewModel.modelAvailabilityUiState.collectAsState()
    val downloadUiState by mainViewModel.downloadUiState.collectAsState()

    var appMode by remember { mutableStateOf(AppMode.ModeSelection) }
    var previousMode by remember { mutableStateOf(AppMode.ModeSelection) }

    LaunchedEffect(uiState.ttsWarningMessage) {
        uiState.ttsWarningMessage?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearTtsWarningMessage()
        }
    }

    LaunchedEffect(appMode) {
        when (appMode) {
            AppMode.ModeSelection -> {
                if (previousMode == AppMode.Chat) {
                    mainViewModel.shutdownPipeline()
                }
            }

            AppMode.Benchmark -> {
                if (previousMode == AppMode.Chat) {
                    runCatching { mainViewModel.llmBridge.cancel() }
                    mainViewModel.cancelPipeline()
                }
            }

            AppMode.Chat -> {
                // Entering Chat init is handled inside ChatScaffold.
            }
        }
        previousMode = appMode
    }

    val modelAvailabilityState = modelAvailabilityUiState.state
    val showModelDownloadPrompt = modelAvailabilityState == ModelAvailabilityState.Checked ||
        modelAvailabilityState == ModelAvailabilityState.Downloading

    if (
        appMode == AppMode.ModeSelection &&
        modelAvailabilityState == ModelAvailabilityState.Unchecked
    ) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Checking models") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Checking required model files before enabling Chat and Benchmark.")
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    } else if (showModelDownloadPrompt) {
        val errorMessage = modelAvailabilityUiState.errorMessage?.takeIf { it.isNotBlank() }
        val showAvailabilityError = errorMessage != null &&
            !downloadUiState.isRunning && downloadUiState.errorMessage.isNullOrBlank()
        val missingModelNames = modelAvailabilityUiState.missingModelNames
        val missingModelText = when (missingModelNames.size) {
            0 -> ""
            1 -> "\n\nMissing or invalid file:\n- ${missingModelNames.first()}"
            else -> "\n\nMissing or invalid files (${missingModelNames.size}):\n" +
                missingModelNames.joinToString("\n") { "- $it" }
        }
        val detailText = if (showAvailabilityError) "" else missingModelText

        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (showAvailabilityError) "Unable to check models" else "Models required") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (showAvailabilityError) {
                            errorMessage.orEmpty()
                        } else {
                            "Download the required models before using Chat or Benchmark." + detailText
                        }
                    )
                    DownloadProgressContent(ui = downloadUiState)
                }
            },
            confirmButton = {
                if (!downloadUiState.isRunning) {
                    TextButton(onClick = mainViewModel::downloadAppModels) {
                        Text(
                            if (showAvailabilityError) {
                                "Try again"
                            } else if (downloadUiState.errorMessage.isNullOrBlank()) {
                                "Download models"
                            } else {
                                "Retry download"
                            }
                        )
                    }
                }
            },
            dismissButton = {}
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    dismissActionContentColor = MaterialTheme.colorScheme.onError
                )
            }
        },
        topBar = {
            if (appMode != AppMode.ModeSelection) {
                Box(
                    modifier = Modifier.background(color = MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    TopBar(
                        modifier = Modifier,
                        title = when (appMode) {
                            AppMode.Chat -> "Chat Mode"
                            AppMode.Benchmark -> "Benchmark Mode"
                            else -> "Arm On-Device Assistant"
                        },
                        showTtsToggle = appMode == AppMode.Chat,
                        showReset = appMode == AppMode.Chat,
                        onBack = { appMode = AppMode.ModeSelection },
                        resetUserText = mainViewModel::resetUserText,
                        resetPerformanceMetrics = mainViewModel::resetPerformanceMetrics,
                        toggleTTS = mainViewModel::toggleTTS,
                        isTTSEnabled = uiState.isTTSEnabled
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = appMode,
            label = "screen_fade"
        ) { mode ->
            when (mode) {
                AppMode.ModeSelection -> ModeSelectionScaffold(
                    padding = padding,
                    modelsAvailable = modelAvailabilityState == ModelAvailabilityState.Available,
                    onChatSelected = onChatSelected@{
                        when (modelAvailabilityState) {
                            ModelAvailabilityState.Available -> appMode = AppMode.Chat
                            ModelAvailabilityState.Checked -> return@onChatSelected
                            ModelAvailabilityState.Unchecked,
                            ModelAvailabilityState.Downloading -> return@onChatSelected
                        }
                    },
                    onBenchmarkSelected = onBenchmarkSelected@{
                        when (modelAvailabilityState) {
                            ModelAvailabilityState.Available -> appMode = AppMode.Benchmark
                            ModelAvailabilityState.Checked -> return@onBenchmarkSelected
                            ModelAvailabilityState.Unchecked,
                            ModelAvailabilityState.Downloading -> return@onBenchmarkSelected
                        }
                    }
                )

                AppMode.Chat -> ChatScaffold(
                    padding = padding,
                    mainViewModel = mainViewModel,
                    snackbarHostState = snackbarHostState
                )

                AppMode.Benchmark -> BenchmarkScaffold(
                    padding = padding,
                    mainViewModel = mainViewModel
                )
            }
        }
    }

    return mainViewModel
}

/**
 * Renders the Mode Selection UI where the user chooses Chat or Benchmark.
 *
 * @param padding Padding values from the parent [Scaffold]
 * @param modelsAvailable Whether Chat and Benchmark should be selectable
 * @param onChatSelected Invoked when Chat mode is selected
 * @param onBenchmarkSelected Invoked when Benchmark mode is selected
 */
@Composable
private fun ModeSelectionScaffold(
    padding: PaddingValues,
    modelsAvailable: Boolean,
    onChatSelected: () -> Unit,
    onBenchmarkSelected: () -> Unit
) {
    ModeSelectionScreen(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        modelsAvailable = modelsAvailable,
        onChatSelected = onChatSelected,
        onBenchmarkSelected = onBenchmarkSelected
    )
}

/**
 * Renders the Chat UI and handles Chat-specific initialization.
 *
 * Shows a loading indicator while the pipeline is reinitialized and
 * scopes Chat-only state to this composable.
 *
 * @param padding Padding values from the parent [Scaffold]
 * @param mainViewModel Shared [MainViewModel] for pipeline interaction
 * @param snackbarHostState Host state for snackbars
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ChatScaffold(
    padding: PaddingValues,
    mainViewModel: MainViewModel,
    snackbarHostState: SnackbarHostState
) {
    var chatReady by remember { mutableStateOf(false) }
    val statusMessage by mainViewModel.statusMessage.collectAsState()

    LaunchedEffect(Unit) {
        chatReady = false
        withContext(Dispatchers.Default) {
            mainViewModel.reinitializePipelineForChat()
        }
        chatReady = true
    }

    if (!chatReady) {
        ChatLoadingScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            statusMessage = statusMessage
        )
    } else {
        MainScreen(
            viewModel = mainViewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            snackbarHostState = snackbarHostState
        )
    }
}

/**
 * Renders the Benchmark UI.
 *
 * @param padding Padding values from the parent [Scaffold]
 * @param mainViewModel Shared [MainViewModel] providing benchmark data
 */
@Composable
private fun BenchmarkScaffold(
    padding: PaddingValues,
    mainViewModel: MainViewModel
) {
    BenchmarkScreen(
        viewModel = mainViewModel,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    )
}
