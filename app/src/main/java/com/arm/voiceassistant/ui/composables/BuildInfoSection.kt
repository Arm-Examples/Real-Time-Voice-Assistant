/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.BuildConfig
import com.arm.voiceassistant.utils.Constants

@Composable
fun BuildInfoSection(
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val valueStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showTitle) {
            Text(
                text = Constants.BUILD_INFO_TITLE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        BuildInfoRow(
            label = Constants.BUILD_INFO_APP_REVISION_LABEL,
            value = BuildConfig.REPO_REVISION,
            labelStyle = labelStyle,
            valueStyle = valueStyle
        )
        BuildInfoRow(
            label = Constants.BUILD_INFO_KLEIDI_LABEL,
            value = if (BuildConfig.KLEIDIAI_ENABLED) "enabled" else "disabled",
            labelStyle = labelStyle,
            valueStyle = valueStyle
        )
        BuildInfoRow(
            label = Constants.BUILD_INFO_LLM_FRAMEWORK_LABEL,
            value = BuildConfig.LLM_FRAMEWORK,
            labelStyle = labelStyle,
            valueStyle = valueStyle
        )
        BuildInfoRow(
            label = Constants.BUILD_INFO_LLM_REVISION_LABEL,
            value = BuildConfig.LLM_REVISION,
            labelStyle = labelStyle,
            valueStyle = valueStyle
        )
        BuildInfoRow(
            label = Constants.BUILD_INFO_STT_REVISION_LABEL,
            value = BuildConfig.STT_REVISION,
            labelStyle = labelStyle,
            valueStyle = valueStyle
        )
    }
}

@Composable
private fun BuildInfoRow(
    label: String,
    value: String,
    labelStyle: androidx.compose.ui.text.TextStyle,
    valueStyle: androidx.compose.ui.text.TextStyle,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = labelStyle,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = valueStyle,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
