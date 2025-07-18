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

@RunWith(MockitoJUnitRunner::class)
class MainScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var mainViewModel: MainViewModel? = null
    private var mainUiState: StateFlow<MainUiState>? = null

    @Before
    fun setupViewModel() {
        val application: Application = Mockito.mock(Application::class.java)
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        Mockito.`when`(application.applicationContext).thenReturn(appContext)
        AppContext.getInstance().context = appContext

        mainViewModel = MainViewModel(application, true)
        mainUiState = mainViewModel?.uiState
    }

    @Before
    fun setContent() {
    composeTestRule.setContent()
    {
        VoiceAssistantTheme {
            ScreenScaffold(
                mainViewModel = mainViewModel!!
            )
        }
    }
}
    @After
    fun tearDown() {
        mainViewModel = null
        mainUiState = null
        AppContext.getInstance().context = null
    }

    @Test
    fun testCancelPipeline() {
        mainViewModel?.setContentState(Constants.ContentStates.Responding)
        assert(Constants.ContentStates.Responding == mainUiState?.value?.contentState)
        composeTestRule.onNodeWithContentDescription("cancel").performClick()
        assert(Constants.ContentStates.Idle == mainUiState?.value?.contentState)
    }

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

    @Test
    fun testClearScreen() {
        mainViewModel?.setUserText("Populating user text box")
        composeTestRule.onNodeWithContentDescription("reset_context").performClick()
        assert("" == mainUiState?.value?.userText)
    }
}
