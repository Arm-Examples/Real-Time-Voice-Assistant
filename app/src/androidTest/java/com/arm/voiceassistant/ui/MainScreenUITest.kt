/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.viewmodels.MainUiState
import com.arm.voiceassistant.viewmodels.MainViewModel
import com.arm.voiceassistant.ScreenScaffold
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

/**
 * UI test class for verifying interactions on the Main Screen.
 */
@RunWith(MockitoJUnitRunner::class)
class MainScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var mainViewModel: MainViewModel? = null
    private var mainUiState: StateFlow<MainUiState>? = null

    /**
     * Initializes the ViewModel with a mocked application context before each test.
     */
    @Before
    fun setupViewModel() {
        val application: Application = Mockito.mock(Application::class.java)
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Mockito.`when`(application.applicationContext).thenReturn(appContext)
        AppContext.getInstance().context = appContext

        mainViewModel = MainViewModel(application, true)
        mainUiState = mainViewModel?.uiState
    }

    /**
     * Sets up the Composable UI content including navigation and top bar.
     */
    @Before
    fun setContent() {
        composeTestRule.setContent {
        VoiceAssistantTheme {
            ScreenScaffold(
                mainViewModel = mainViewModel!!
            )
        }
    }
}


    /**
     * Cleans up the ViewModel and application context after each test.
     */
    @After
    fun tearDown() {
        mainViewModel = null
        mainUiState = null
        AppContext.getInstance().context = null
    }

    /**
     * Verifies that the cancel button returns the content state to Idle from Responding.
     */
    @Test
    fun testCancelPipeline() {
        mainViewModel?.setContentState(Constants.ContentStates.Responding)
        assert(Constants.ContentStates.Responding == mainUiState?.value?.contentState)
        composeTestRule.onNodeWithContentDescription("cancel").performClick()
        assert(Constants.ContentStates.Idle == mainUiState?.value?.contentState)
    }

    /**
     * Verifies that canceling a recording displays a confirmation dialog and resets state to Idle.
     */
    @Test
    fun testCancelRecording() {
        mainViewModel?.pipeline!!.initRecorder()
        mainViewModel?.setContentState(Constants.ContentStates.Recording)
        assert(Constants.ContentStates.Recording == mainUiState?.value?.contentState)
        composeTestRule.onNodeWithContentDescription("cancel_recording").performClick()
        composeTestRule.onNodeWithContentDescription("confirm_button").performClick()
        assert(Constants.ContentStates.Idle == mainUiState?.value?.contentState)
        assert("" == mainUiState?.value?.responseText)
    }

    /**
     * Verifies that clicking the reset button clears the user input text.
     */
    @Test
    fun testClearScreen() {
        mainViewModel?.setUserText("Populating user text box")
        composeTestRule.onNodeWithContentDescription("reset_context").performClick()
        assert("" == mainUiState?.value?.userText)
    }
}
