/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arm.voiceassistant.ChatLoadingScreen
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * UI test class for verifying Chat loading screen status messaging.
 */
@RunWith(MockitoJUnitRunner::class)
class ChatLoadingScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Verifies that status messages render on the chat loading screen.
     */
    @Test
    fun testLoadingStatusMessage() {
        composeTestRule.setContent {
            VoiceAssistantTheme {
                ChatLoadingScreen(statusMessage = "Loading speech-to-text model...")
            }
        }
        composeTestRule.onNodeWithText("Loading speech-to-text model...").assertExists()
    }
}
