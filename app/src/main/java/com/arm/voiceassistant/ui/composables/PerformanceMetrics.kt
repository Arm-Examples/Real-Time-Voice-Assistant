/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

/**
 * Top-of-page system stats:
 *  - RTVA memory usage
 *  - Available system memory
 *  - Thermal status
 *
 * @param modifier Layout modifier
 */
@Composable
fun ModelMetrics(
    modifier: Modifier = Modifier
) {
    val valueStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("model_metrics_row"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Memory: RTVA used / available
        StatusItem(icon = Icons.Outlined.Memory) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryUsageText(
                    textStyle = valueStyle,
                    modifier = Modifier.testTag("metric_memory_used")
                )
                Text(
                    text = " / ",
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MemoryAvailableText(
                    textStyle = valueStyle,
                    modifier = Modifier.testTag("metric_memory_available")
                )
            }
        }

        // Thermal
        StatusItem(icon = Icons.Outlined.DeviceThermostat) {
            ThermalStatusText(
                textStyle = valueStyle,
                modifier = Modifier.testTag("metric_thermal")
            )
        }

        // Battery
        StatusItem(icon = Icons.Outlined.BatteryFull) {
            BatteryPercentageText(
                textStyle = valueStyle,
                modifier = Modifier.testTag("metric_battery")
            )
        }
    }
}

/**
 * Small inline status item consisting of an icon and custom content.
 *
 * Used as a building block for compact system metrics displayed inline.
 *
 * @param icon Icon representing the metric type
 * @param modifier Optional layout modifier
 * @param content Composable displaying the metric value
 */
@Composable
private fun StatusItem(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        content()
    }
}
