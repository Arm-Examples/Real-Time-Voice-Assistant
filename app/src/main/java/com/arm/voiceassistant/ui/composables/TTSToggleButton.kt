/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * Toggle button to enable or disable Text-to-Speech (TTS) functionality.
 *
 * @param isEnabled Whether TTS is currently enabled.
 * @param onToggle Lambda function triggered when the switch is toggled.
 */
@Composable
fun TTSToggleButton(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (isEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (isEnabled) "TTS On" else "TTS Off"
            )
        }
    }
}

/**
 * TTS toggle button preview
 */
@Preview(showBackground = true)
@Composable
private fun TTSToggleButtonPreview() {
    var isTtsEnabled by remember { mutableStateOf(true) }

    VoiceAssistantTheme {
        TTSToggleButton(
            isEnabled = isTtsEnabled,
            onToggle = { isTtsEnabled = !isTtsEnabled }
        )
    }
}