/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.Pipeline
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * Global reference to the active [Pipeline] instance.
 *
 * Used by the top app bar to trigger context reset actions.
 * This reference is managed by the screen scaffold lifecycle.
 */
var pipeline: Pipeline? = null

/**
 * Displays the top app bar with title and action buttons.
 * @param modifier Layout modifier
 * @param onBack Callback to go back (Mode selection)
 * @param resetUserText Callback to clear user input and context
 * @param resetPerformanceMetrics Callback to reset perf metrics
 * @param toggleTTS Callback to toggle TTS
 * @param isTTSEnabled Current TTS state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String = "Arm On-Device Assistant",
    onBack: () -> Unit = {},
    resetUserText: () -> Unit = {},
    resetPerformanceMetrics: () -> Unit = {},
    toggleTTS: () -> Unit = {},
    isTTSEnabled: Boolean = true,
    showTtsToggle: Boolean = true,
    showReset: Boolean = true
) {
    CenterAlignedTopAppBar(
        modifier = modifier.height(40.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondary
        ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back_to_mode_selection",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        actions = {
            if (showTtsToggle) {
                TTSToggleButton(
                    isEnabled = isTTSEnabled,
                    onToggle = toggleTTS
                )
            }

            if (showReset) {
                IconButton(
                    onClick = {
                        pipeline?.resetContext()
                        resetUserText()
                        resetPerformanceMetrics()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cached,
                        contentDescription = "reset_context",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}

/**
 * Preview of the [TopBar] composable using the app theme.
 */
@Preview
@Composable
private fun TopBarPreview() {
    VoiceAssistantTheme {
        TopBar(
            onBack = {},
            resetUserText = {},
            toggleTTS = {},
            isTTSEnabled = true
        )
    }
}
