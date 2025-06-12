/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.net.Uri

/**
 * Represents a message in the chat conversation.
 */
sealed class ChatMessage {
    data class UserText(val text: String) : ChatMessage()
    data class UserImage(val uri: Uri) : ChatMessage()
    data class AssistantText(val text: String) : ChatMessage()
}
