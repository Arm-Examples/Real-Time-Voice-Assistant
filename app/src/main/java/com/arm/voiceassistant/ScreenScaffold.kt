/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arm.voiceassistant.ui.composables.TopBar
import com.arm.voiceassistant.ui.screens.MainScreen
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.arm.voiceassistant.viewmodels.ViewModelProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi

/**
 * Top-level scaffold that sets up the main app structure.
@OptIn(ExperimentalPermissionsApi::class)
 * @param snackbarHostState Host state for showing snackbars
 * @param mainViewModel Shared ViewModel passed to navigation and top bar
 * @return The provided or created [MainViewModel] instance
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScreenScaffold(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    mainViewModel: MainViewModel = viewModel(factory = ViewModelProvider.Factory)
): MainViewModel {
    val uiState = mainViewModel.uiState.collectAsState().value

    LaunchedEffect(uiState.TTSWarningMessage) {
        uiState.TTSWarningMessage?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearTTSWarningMessage()
        }
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
            Column(
                modifier = Modifier
                    .background(color = MaterialTheme.colorScheme.secondary),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopBar(
                    modifier = Modifier,
                    resetUserText = mainViewModel::resetUserText,
                    togglePerformance = mainViewModel::togglePerformanceMetrics,
                    resetPerformanceMetrics = mainViewModel::resetPerformanceMetrics,
                    toggleTTS = mainViewModel::toggleTTS,
                    isTTSEnabled = uiState.isTTSEnabled
                )
            }
        },
        content = { padding ->
            MainScreen(
                viewModel = mainViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                snackbarHostState = snackbarHostState
            )
        }

    )
    return mainViewModel
}
