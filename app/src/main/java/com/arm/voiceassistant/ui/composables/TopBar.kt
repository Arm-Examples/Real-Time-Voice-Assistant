/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SsidChart
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

var pipeline: Pipeline? = null

/**
 * Displays the top app bar with title and action buttons.
 * @param modifier Layout modifier
 * @param resetUserText Callback to clear user input and context
 * @param togglePerformance Callback to toggle performance metrics
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    resetUserText: () -> Unit = {},
    togglePerformance: () -> Unit = {},
    resetPerformanceMetrics: () -> Unit = {},
    toggleTTS: () -> Unit = {},
    isTTSEnabled: Boolean = true
) {
    CenterAlignedTopAppBar(
        modifier = modifier.height(40.dp),
        title = {
            Row(modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                text = "Arm On-Device Assistant",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondary),
        navigationIcon = {
            IconButton(onClick = {
                togglePerformance()
            }) {
                Icon(
                    imageVector = Icons.Outlined.SsidChart,
                    contentDescription = "toggle_performance",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        actions = {
            TTSToggleButton(
                isEnabled = isTTSEnabled,
                onToggle = toggleTTS
            )

            IconButton(
                onClick = {
                    pipeline?.resetContext()
                    resetUserText()
                    resetPerformanceMetrics()}
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cached,
                    contentDescription = "reset_context",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    )
}

/**
 * Top bar home preview
 */
@Preview
@Composable
private fun TopBarHomePreview() {
    VoiceAssistantTheme {
        TopBar(
            resetUserText = {},
            togglePerformance = {},
            toggleTTS = {},
            isTTSEnabled = true
        )
    }
}

/**
 * Top bar settings preview
 */
@Preview
@Composable
private fun TopBarSettingsPreview() {
    VoiceAssistantTheme {
        TopBar(
            resetUserText = {},
            togglePerformance = {},
            toggleTTS = {},
            isTTSEnabled = true
        )
    }
}
