/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.composables.BuildInfoSection
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants

/**
 * Screen allowing the user to select the application mode.
 *
 * Presents animated buttons for entering chat or benchmark modes
 * with a themed background and staggered entrance animations.
 *
 * @param modifier Optional modifier for layout customization
 * @param onChatSelected Callback invoked when Chat mode is selected
 * @param onBenchmarkSelected Callback invoked when Benchmark mode is selected
 */
@Composable
fun ModeSelectionScreen(
    modifier: Modifier = Modifier,
    onChatSelected: () -> Unit,
    onBenchmarkSelected: () -> Unit
) {
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
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        Text(
            text = Constants.APP_TITLE,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Choose mode",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            FilledTonalButton(
                onClick = onChatSelected,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = "Chat"
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Chat")
            }

            FilledTonalButton(
                onClick = onBenchmarkSelected,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = "Benchmark"
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Benchmark")
            }

        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.large
        ) {
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                BuildInfoSection(showTitle = true)
            }
        }
    }
}

/**
 * Mode selection screen preview
 */
@Preview(showBackground = true)
@Composable
private fun ModeSelectionScreenPreview() {
    VoiceAssistantTheme {
        ModeSelectionScreen(
            onChatSelected = {},
            onBenchmarkSelected = {}
        )
    }
}
