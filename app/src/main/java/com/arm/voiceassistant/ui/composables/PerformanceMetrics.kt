/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants.INITIAL_METRICS_VALUE

/**
 * Displays model performance metrics in a vertical column.
 * @param modifier Modifier for layout customization
 * @param model1metric Value for speech recognition time
 * @param model2metric Value for LLM encode tokens per second
 * @param model3metric Value for LLM decode tokens per second
 */
@Composable
fun ModelMetrics(
    modifier: Modifier = Modifier,
    model1metric: String = INITIAL_METRICS_VALUE,
    model2metric: String = INITIAL_METRICS_VALUE,
    model3metric: String = INITIAL_METRICS_VALUE
) {
    val metricMap = mapOf(
        "Speech recognition time" to model1metric,
        "LLM encode tokens/s" to model2metric,
        "LLM decode tokens/s" to model3metric
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 5.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        metricMap.forEach { (title, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .semantics { contentDescription = title }
            ) {
                Text(
                    text = "$title:",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondary
                )
                Spacer(modifier.weight(1f))
                Text(
                    text = value,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }
    }
}

/**
 * Displays a toggle switch to show or hide performance metrics.
 * @param modifier Modifier for layout styling
 * @param togglePerformance Callback triggered when the switch is toggled
 * @param displayPerformance Current toggle state (true = show metrics)
 */
@Composable
fun TogglePerformance(
    modifier: Modifier = Modifier,
    togglePerformance: () -> Unit = {},
    displayPerformance: Boolean = false
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondary)
            .fillMaxWidth()
            .height(45.dp)
            .padding(PaddingValues(start = 15.dp, end = 15.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Toggle performance metrics",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Switch(
            modifier = Modifier.semantics { contentDescription = "toggle_performance" },
            checked = displayPerformance,
            onCheckedChange = { togglePerformance() },
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.inversePrimary,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = MaterialTheme.colorScheme.inversePrimary,
                checkedThumbColor = MaterialTheme.colorScheme.inversePrimary,
                checkedTrackColor = Color.Transparent,
                checkedBorderColor = MaterialTheme.colorScheme.inversePrimary
            )
        )
    }
}

/**
 * Toggle performance preview
 */
@Preview(showBackground = true)
@Composable
private fun TogglePerformancePreview() {
    VoiceAssistantTheme {
        TogglePerformance()
        ModelMetrics()
    }
}
