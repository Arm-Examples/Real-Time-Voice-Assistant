/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme

/**
 * Displays a message bubble for the user, aligned to the right.
 * @param text The user’s message text to display inside the bubble.
 */
@Composable
fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .widthIn(max = 280.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * User bubble preview
 */
@Preview(showBackground = true)
@Composable
fun UserBubblePreview() {
    VoiceAssistantTheme {
        UserBubble(text = "This is a sample user message.")
    }
}
