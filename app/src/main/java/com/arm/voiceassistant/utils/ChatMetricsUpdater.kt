/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Keeps UI metrics + message timing footers in sync with LLM performance numbers.
 *
 * ViewModel calls this helper to:
 *  - update encode TPS once per turn (and reflect it on the last user message)
 *  - update decode TPS at completion (and reflect it on the last assistant message)
 */
class ChatMetricsUpdater(
    private val uiState: MutableStateFlow<MainUiState>,
    private val messages: SnapshotStateList<ChatMessage>,
) {
    /**
     * Updates encode performance metrics for the current turn.
     *
     * Sets the encode TPS in UI state and updates the timing footer
     * of the most recent user message.
     */
    fun onEncodeAvailableOncePerTurn(encodeTps: String) {
        uiState.update { it.copy(llmEncodeTPS = encodeTps) }

        val lastUserIndex = messages.indexOfLast { it is ChatMessage.UserText }
        if (lastUserIndex != -1) {
            val userMsg = messages[lastUserIndex] as ChatMessage.UserText
            val updatedTiming = TimingStats(
                sttTime = userMsg.timing?.sttTime ?: uiState.value.sttTime,
                llmEncodeTps = encodeTps,
                llmDecodeTps = "-"
            )
            messages[lastUserIndex] = userMsg.copy(timing = updatedTiming)
        }
    }

    /**
     * Updates decode performance metrics at the end of a response.
     *
     * Sets the decode TPS in UI state and updates the timing footer
     * of the most recent assistant message.
     */
    fun onDecodeAvailableAtEnd(decodeTps: String) {
        uiState.update { it.copy(llmDecodeTPS = decodeTps) }

        val lastAssistantIndex = messages.indexOfLast { it is ChatMessage.AssistantText }
        if (lastAssistantIndex != -1) {
            val assistantMsg = messages[lastAssistantIndex] as ChatMessage.AssistantText
            val updatedTiming = TimingStats(
                sttTime = "-",
                llmEncodeTps = "-",
                llmDecodeTps = decodeTps
            )
            messages[lastAssistantIndex] = assistantMsg.copy(timing = updatedTiming)
        }
    }
}