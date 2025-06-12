/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Dialog to display for user choices
 * @param modifier Layout modifier
 * @param onDismissRequest action on dismiss
 * @param onConfirmation action on confirmation
 * @param dialogText dialog question to display
 */
@Composable
fun ConfirmationDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogText: String
) {
    AlertDialog(
        modifier = modifier,
        text = {
            Text(
                text = dialogText,
                color = MaterialTheme.colorScheme.onSecondary
            ) },
        onDismissRequest = { onDismissRequest() },
        confirmButton = {
            TextButton(
                modifier = Modifier.semantics { contentDescription = "confirm_button" },
                onClick = { onConfirmation() }
            ) {
                Text(
                    text = "OK",
                    color = MaterialTheme.colorScheme.onSecondary
                )
            } },
        dismissButton = {
            TextButton(
                modifier = Modifier.semantics { contentDescription = "back_button" },
                onClick = { onDismissRequest() }
            ) {
                Text(
                    text = "BACK",
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    )
}
