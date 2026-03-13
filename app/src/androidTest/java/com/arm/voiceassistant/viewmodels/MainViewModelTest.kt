/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import android.app.Application
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Constants.ContentStates
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for [MainViewModel] to verify the correctness of
 * error handling, UI state management, and metric toggling.
 */
@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {
    private var mainViewModel: MainViewModel? = null
    private var mainUiState: StateFlow<MainUiState>? = null

    /**
     * Initializes the [MainViewModel] before each test,
     * and sets up a mock application context.
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
     * Cleans up after each test by resetting the view model and UI state.
     */
    @After
    fun tearDown() {
        mainViewModel = null
        mainUiState = null

    }

    /**
     * Verifies that the initial UI state is correctly set
     * when the view model is initialized.
     */
    @Test
    fun testInitialState() {
        assertEquals(ContentStates.Idle, mainUiState?.value?.contentState)
        assertEquals(Error(state = false, message = ""), mainUiState?.value?.error)
        assertEquals("", mainUiState?.value?.userText)
        assertEquals("", mainUiState?.value?.responseText)
        assertEquals("00:00", mainUiState?.value?.recTime)
        assertEquals(Constants.INITIAL_METRICS_VALUE, mainUiState?.value?.sttTime)
        assertEquals(Constants.INITIAL_METRICS_VALUE, mainUiState?.value?.llmEncodeTPS)
        assertEquals(Constants.INITIAL_METRICS_VALUE, mainUiState?.value?.llmDecodeTPS)
    }

    /**
     * Verifies that [MainViewModel.onError] correctly sets the error state and message.
     */
    @Test
    fun testOnError() {
        mainViewModel?.onError("error message")
        assertEquals(true, mainUiState?.value?.error?.state)
        assertEquals("error message", mainUiState?.value?.error?.message)
    }

    /**
     * Verifies that [MainViewModel.clearError] resets the error state and message.
     */
    @Test
    fun testClearError() {
        testOnError()
        mainViewModel?.clearError()
        assertEquals(false, mainUiState?.value?.error?.state)
        assertEquals("", mainUiState?.value?.error?.message)
    }

}
