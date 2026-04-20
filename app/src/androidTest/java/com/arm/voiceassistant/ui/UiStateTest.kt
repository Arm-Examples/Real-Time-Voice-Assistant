/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.arm.voiceassistant.ui.screens.MainScreen
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.ChatMessage
import com.arm.voiceassistant.utils.MainUiState
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import org.junit.Rule
import org.junit.Test
import android.app.Application
import android.content.Context
import org.junit.Before
import org.mockito.Mockito
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.utils.TimingStats
import androidx.compose.ui.test.onNodeWithTag

/**
 * UI State tests for the MainScreen.
 * This class verifies how the UI reacts based on various states of MainUiState.
 */
class UiStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()
    private lateinit var application: Application
    private val startRecordingButton get() = composeTestRule.onNodeWithContentDescription("record")
    private val stopRecordingButton get() = composeTestRule.onNodeWithContentDescription("stop_recording")
    private val cancelRecordingButton get() = composeTestRule.onNodeWithContentDescription("cancel_recording")
    private val cancelPipelineButton get() = composeTestRule.onNodeWithContentDescription("cancel")
    private val cancellingPipelineButton get() = composeTestRule.onNodeWithContentDescription("cancelling")

    /**
     * Sets up a mocked application context before each test.
     */
    @Before
    fun setupAppContext() {
        application = Mockito.mock(Application::class.java)
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Mockito.`when`(application.applicationContext).thenReturn(appContext)
        AppContext.getInstance().context = appContext
    }

    /**
     * Helper method to launch MainScreen with a given UI state.
     */
    @OptIn(ExperimentalPermissionsApi::class)
    private fun launchScreenWithState(state: MainUiState): MainViewModel {
        val viewModel = MainViewModel(application, isTest = true)
        viewModel.setUiStateForTest(state)

        composeTestRule.setContent {
            VoiceAssistantTheme {
                MainScreen(viewModel = viewModel)
            }
        }
        return viewModel
    }

    /**
     * Verify UI components are in the correct state when the app is idle.
     */
    @Test
    fun testInitialIdleState() {
        launchScreenWithState(MainUiState())

        startRecordingButton.assertExists()
        startRecordingButton.assertTextEquals("Press to talk")
        composeTestRule.onNodeWithContentDescription("microphone").assertExists()

        stopRecordingButton.assertDoesNotExist()
        cancelRecordingButton.assertDoesNotExist()
        cancelPipelineButton.assertDoesNotExist()
        cancellingPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify UI elements for the Recording state.
     */
    @Test
    fun testRecordingState() {
        launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Recording))

        stopRecordingButton.assertExists()
        stopRecordingButton.assertTextContains("Recording... press to finish")
        composeTestRule.onNodeWithContentDescription("microphone").assertExists()
        cancelRecordingButton.assertExists()
        composeTestRule.onNodeWithContentDescription("cancel_recording").assertExists()

        startRecordingButton.assertDoesNotExist()
        cancelPipelineButton.assertDoesNotExist()
        cancellingPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify UI for Transcribing state.
     */
    @Test
    fun testTranscribingState() {
        launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Transcribing))

        cancelPipelineButton.assertExists()
        cancelPipelineButton.assertTextEquals("Cancel")
        composeTestRule.onNodeWithContentDescription("cancel").assertExists()

        startRecordingButton.assertDoesNotExist()
        stopRecordingButton.assertDoesNotExist()
        cancelRecordingButton.assertDoesNotExist()
        cancellingPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify UI for Responding state.
     */
    @Test
    fun testRespondingState() {
        launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Responding))

        cancelPipelineButton.assertExists()
        cancelPipelineButton.assertTextEquals("Cancel")
        composeTestRule.onNodeWithContentDescription("cancel").assertExists()

        startRecordingButton.assertDoesNotExist()
        stopRecordingButton.assertDoesNotExist()
        cancelRecordingButton.assertDoesNotExist()
        cancellingPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify UI for Speaking state.
     */
    @Test
    fun testSpeakingState() {
        launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Speaking))

        cancelPipelineButton.assertExists()
        cancelPipelineButton.assertTextEquals("Cancel")
        composeTestRule.onNodeWithContentDescription("cancel").assertExists()

        startRecordingButton.assertDoesNotExist()
        stopRecordingButton.assertDoesNotExist()
        cancelRecordingButton.assertDoesNotExist()
        cancellingPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify UI for Cancelling state.
     */
    @Test
    fun testCancellingState() {
        launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Cancelling))

        cancellingPipelineButton.assertExists()
        cancellingPipelineButton.assertTextEquals("Cancelling...")

        startRecordingButton.assertDoesNotExist()
        stopRecordingButton.assertDoesNotExist()
        cancelRecordingButton.assertDoesNotExist()
        cancelPipelineButton.assertDoesNotExist()
    }

    /**
     * Verify text content visibility in the user and assistant bubbles.
     */
    @Test
    fun testTextVisible() {
        val userText = "Example transcription of speech from user"
        val responseText = "Example response from Voice Assistant"
        val viewModel = launchScreenWithState(MainUiState(contentState = Constants.ContentStates.Responding))

        viewModel.messages.add(ChatMessage.UserText(userText))
        viewModel.messages.add(ChatMessage.AssistantText(responseText))

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(userText).fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText(responseText).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(userText).assertExists()
        composeTestRule.onNodeWithText(responseText).assertExists()
    }

    /**
     * Ensure user text bubble is rendered correctly.
     */
    @Test
    fun testUserTextBubbleRenders() {
        val userText = "This is a user message."
        val viewModel = launchScreenWithState(MainUiState())

        viewModel.messages.add(ChatMessage.UserText(userText))

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("User").assertExists()
        composeTestRule.onNodeWithText(userText).assertExists()
    }

    /**
     * Ensure assistant welcome message is shown on first launch.
     */
    @Test
    fun testInitialWelcomeMessageAppears() {
        val welcome = "I'm your AI assistant. How can I help you?"
        val viewModel = launchScreenWithState(MainUiState())

        viewModel.messages.add(ChatMessage.AssistantText(welcome))

        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("Voice Assistant").onFirst().assertExists()
        composeTestRule.onNodeWithText(welcome).assertExists()
    }

    /**
     * Verifies that performance timing (STT and Encode) is displayed
     * beneath the user message bubble when timing data is present.
     */
    @Test
    fun testUserTimingFooterVisible() {
        val viewModel = launchScreenWithState(MainUiState())
        viewModel.messages.clear()

        viewModel.messages.add(
            ChatMessage.UserText(
                text = "Hello",
                timing = TimingStats(
                    sttTime = "1.2",
                    llmEncodeTps = "10.5",
                    llmDecodeTps = "-"
                )
            )
        )

        // Wait until timing footer is composed
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("STT", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Hello").assertExists()
    }

    /**
     * Verifies that decode performance timing is displayed
     * beneath the assistant message bubble when available.
     */
    @Test
    fun testAssistantDecodeFooterVisible() {
        val viewModel = launchScreenWithState(MainUiState())
        viewModel.messages.clear()

        viewModel.messages.add(
            ChatMessage.AssistantText(
                text = "Hi there",
                timing = TimingStats(
                    sttTime = "-",
                    llmEncodeTps = "-",
                    llmDecodeTps = "6.7"
                )
            )
        )

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText("Decode", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("Hi there").assertExists()
    }

    /**
     * Verifies that the top status strip contains memory and thermal metrics.
     */
    @OptIn(ExperimentalPermissionsApi::class)
    @Test
    fun testModelMetricsRowShowsMemoryAndThermal() {
        val viewModel = MainViewModel(application, isTest = true)

        composeTestRule.setContent {
            VoiceAssistantTheme {
                MainScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithTag("model_metrics_row").assertExists()
        composeTestRule.onNodeWithTag("metric_memory_available").assertExists()
        composeTestRule.onNodeWithTag("metric_memory_used").assertExists()
        composeTestRule.onNodeWithTag("metric_thermal").assertExists()
    }
}
