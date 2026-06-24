/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import kotlinx.coroutines.launch

/**
 * Small info icon that toggles a plain tooltip on tap.
 *
 * @param text Tooltip message to display.
 * @param modifier Optional modifier for positioning or layout.
 * @param testTag Optional test tag applied to the icon surface.
 * @param offsetX Horizontal offset applied to the tooltip anchor.
 * @param offsetY Vertical offset applied to the tooltip anchor.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun InfoTooltipButton(
    text: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
) {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier.offset(x = offsetX, y = offsetY)
    ) {
        Surface(
            modifier = Modifier
                .size(24.dp)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
                .clickable {
                    scope.launch {
                        if (tooltipState.isVisible) {
                            tooltipState.dismiss()
                        } else {
                            tooltipState.show()
                        }
                    }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = if (tooltipState.isVisible) "Hide help" else "Show help",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InfoTooltipButtonPreview() {
    VoiceAssistantTheme(darkTheme = false) {
        InfoTooltipButton(text = "Benchmark input tokens help text.")
    }
}
