/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * Animated container that fades, slides, and scales its content into view
 * with a staggered delay based on the given index.
 *
 * Used to create smooth sequential entrance animations for UI elements.
 *
 * @param visible Whether the content should be visible
 * @param index Position used to compute the stagger delay
 * @param modifier Optional modifier for layout customization
 * @param baseDelayMs Base delay (in ms) applied per index
 * @param durationMs Animation duration in milliseconds
 * @param slidePx Vertical slide distance in pixels
 * @param content Composable content to animate
 */


@Composable
private fun StaggerIn(
    visible: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    baseDelayMs: Int = 220,     // a bit slower so it feels smoother
    durationMs: Int = 900,
    slidePx: Float = 30f,
    content: @Composable () -> Unit
) {
    val target = if (visible) 1f else 0f

    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = durationMs,
            delayMillis = index * baseDelayMs,
            easing = FastOutSlowInEasing
        ),
        label = "stagger-$index"
    )

    Box(
        modifier = modifier
            .alpha(progress)
            .graphicsLayer {
                translationY = (1f - progress) * slidePx
                val scale = 0.96f + (0.04f * progress)
                scaleX = scale
                scaleY = scale
            }
    ) {
        content()
    }
}

private const val chatButtonText = "Chat"
private const val benchmarkButtonText = "Benchmark"
private const val downloadModelsButtonText = "Download models"

/**
 * Screen allowing the user to select the application mode.
 *
 * Presents animated buttons for entering chat or benchmark modes
 * with a themed background and staggered entrance animations.
 *
 * @param modifier Optional modifier for layout customization
 * @param onChatSelected Callback invoked when Chat mode is selected
 * @param onBenchmarkSelected Callback invoked when Benchmark mode is selected
 * @param onModelDownloadSelected Callback invoked when models download mode is selected
 */
@Composable
fun ModeSelectionScreen(
    modifier: Modifier = Modifier,
    onChatSelected: () -> Unit,
    onBenchmarkSelected: () -> Unit,
    onModelDownloadSelected: () -> Unit
) {
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { start = true }

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
            .then(backgroundModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StaggerIn(visible = start, index = 0) {
                Text(
                    text = "Choose mode",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            StaggerIn(visible = start, index = 1) {
                FilledTonalButton(
                    onClick = onChatSelected,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = chatButtonText
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(chatButtonText)
                }
            }

            StaggerIn(visible = start, index = 2) {
                FilledTonalButton(
                    onClick = onBenchmarkSelected,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Speed,
                        contentDescription = benchmarkButtonText
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(benchmarkButtonText)
                }
            }

            StaggerIn(visible = start, index = 3) {
                FilledTonalButton(
                    onClick = onModelDownloadSelected,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = downloadModelsButtonText
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(downloadModelsButtonText)
                }
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
            onBenchmarkSelected = {},
            onModelDownloadSelected = {}
        )
    }
}
