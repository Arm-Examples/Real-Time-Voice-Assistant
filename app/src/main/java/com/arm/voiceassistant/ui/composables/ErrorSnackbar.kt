/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Displays an error snackbar with optional dismiss action.
 * @param snackbarHostState The SnackbarHostState to control the snackbar
 * @param message The error message to show
 * @param withDismissAction Whether to show a dismiss button
 * @param duration Duration to show the snackbar (default: Indefinite)
 * @param onDismiss Callback triggered when snackbar is dismissed
 */
@Composable
fun ErrorSnackbar(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    message: String,
    withDismissAction: Boolean = true,
    duration: SnackbarDuration = SnackbarDuration.Indefinite,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            val result = snackbarHostState
                .showSnackbar(
                    message = message,
                    withDismissAction = withDismissAction,
                    duration = duration
                )
            if (result == SnackbarResult.Dismissed) { onDismiss() }
        }
    }
}
