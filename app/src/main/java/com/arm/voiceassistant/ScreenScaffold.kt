/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.annotation.VisibleForTesting
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
import com.arm.voiceassistant.ui.composables.TopBar
import com.arm.voiceassistant.ui.screens.BenchmarkScreen
import com.arm.voiceassistant.ui.screens.MainScreen
import com.arm.voiceassistant.ui.screens.ModeSelectionScreen
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

    var appMode by remember { mutableStateOf(AppMode.ModeSelection) }
    var previousMode by remember { mutableStateOf(AppMode.ModeSelection) }

    LaunchedEffect(uiState.ttsWarningMessage) {
        uiState.ttsWarningMessage?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearTtsWarningMessage()
        }
    }

    // Transition handling using switch/when
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
                    onChatSelected = { appMode = AppMode.Chat },
                    onBenchmarkSelected = { appMode = AppMode.Benchmark }
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
 * @param onChatSelected Invoked when Chat mode is selected
 * @param onBenchmarkSelected Invoked when Benchmark mode is selected
 */
@Composable
private fun ModeSelectionScaffold(
    padding: PaddingValues,
    onChatSelected: () -> Unit,
    onBenchmarkSelected: () -> Unit
) {
    ModeSelectionScreen(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
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
    // Local to Chat only; gets reset automatically when leaving Chat (composable disposed)
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
