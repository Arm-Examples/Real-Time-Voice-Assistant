/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arm.voiceassistant.ui.screens.ModeSelectionScreen
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for [ModeSelectionScreen].
 *
 * Verifies that the mode selection UI is displayed correctly and that
 * selecting Chat or Benchmark invokes the corresponding callbacks.
 */
class ModeSelectionScreenUITest {
    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Sets the ModeSelectionScreen content and advances animations
     * so all UI elements are fully visible and interactive.
     */
    private fun setModeSelectionContent(
        onChatSelected: () -> Unit = {},
        onBenchmarkSelected: () -> Unit = {},
        onModelDownloadSelected: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            VoiceAssistantTheme {
                ModeSelectionScreen(
                    onChatSelected = onChatSelected,
                    onBenchmarkSelected = onBenchmarkSelected,
                    onModelDownloadSelected = onModelDownloadSelected
                )
            }
        }

        // Your screen fades/slides in with staggered delays.
        // Advance time so the nodes are fully visible and clickable.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(2500L)
        composeTestRule.mainClock.autoAdvance = true
    }

    /**
     * Verifies that the mode selection title and buttons are displayed.
     */
    @Test
    fun testShowsTitleAndButtons() {
        setModeSelectionContent()

        composeTestRule.onNodeWithText("Choose mode").assertExists()
        composeTestRule.onNodeWithText("Chat").assertExists()
        composeTestRule.onNodeWithText("Benchmark").assertExists()
    }

    /**
     * Verifies that selecting Chat invokes the chat selection callback.
     */
    @Test
    fun testClickChat() {
        var clicked = false
        setModeSelectionContent(onChatSelected = { clicked = true })

        composeTestRule.onNodeWithText("Chat").performClick()
        composeTestRule.runOnIdle { assertTrue(clicked) }
    }

    /**
     * Verifies that selecting Benchmark invokes the benchmark selection callback.
     */
    @Test
    fun testClickBenchmark() {
        var clicked = false
        setModeSelectionContent(onBenchmarkSelected = { clicked = true })

        composeTestRule.onNodeWithText("Benchmark").performClick()
        composeTestRule.runOnIdle { assertTrue(clicked) }
    }
}

