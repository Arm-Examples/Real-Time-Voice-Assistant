/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.ui.screens.BenchmarkScreen
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.viewmodels.MainViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito


/**
 * UI tests for [BenchmarkScreen].
 *
 * Verifies that core benchmark UI sections are rendered correctly and that
 * the Run button enablement behaves as expected based on model availability.
 * Uses injected model options to avoid filesystem-dependent flakiness in CI.
 */
class BenchmarkScreenUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var viewModel: MainViewModel? = null
    private lateinit var application: Application

    /**
     * Sets up a test [MainViewModel] with a mocked application context.
     */
    @Before
    fun setup() {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        application = Mockito.mock(Application::class.java)
        Mockito.`when`(application.applicationContext).thenReturn(appContext)
        AppContext.getInstance().context = appContext

        viewModel = MainViewModel(application, isTest = true)
    }

    /**
     * Clears test state after each test run.
     */
    @After
    fun tearDown() {
        viewModel = null
        AppContext.getInstance().context = null
    }

    /**
     * Renders [BenchmarkScreen] with a deterministic set of model options.
     *
     * @param modelOptionsOverride List of model names to inject for testing.
     */
    private fun setBenchmarkContent(modelOptionsOverride: List<String>) {
        composeTestRule.setContent {
            VoiceAssistantTheme {
                BenchmarkScreen(
                    viewModel = viewModel!!,
                    modelOptionsOverride = modelOptionsOverride
                )
            }
        }
    }

    /**
     * Returns the Run button node using a stable test tag.
     */
    private fun runButton() = composeTestRule.onNodeWithTag("benchmark_run_button")

    /**
     * Verifies that the benchmark screen renders its main sections.
     */
    @Test
    fun testShowsBenchmarkSections() {
        setBenchmarkContent(modelOptionsOverride = listOf("No models found"))

        composeTestRule.onNodeWithTag("benchmark_run_header").assertExists()
        runButton().assertExists()
        composeTestRule.onNodeWithTag("metric_battery").assertExists()
        composeTestRule.onNodeWithTag("metric_memory_available").assertExists()
        composeTestRule.onNodeWithTag("metric_memory_used").assertExists()
        composeTestRule.onNodeWithTag("metric_thermal").assertExists()
    }

    /**
     * Verifies that the Run button is disabled when no benchmark models are available.
     */
    @Test
    fun testRunDisabledWhenNoModelsFound() {
        setBenchmarkContent(modelOptionsOverride = listOf("No models found"))

        runButton().assertIsNotEnabled()
    }

    /**
     * Verifies that the Run button is enabled when at least one model exists.
     */
    @Test
    fun testRunEnabledWhenModelExists() {
        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        runButton().assertIsEnabled()
    }

    /**
     * Verifies that the Results section is not shown before running a benchmark.
     */
    @Test
    fun testResultsNotVisibleInitially() {
        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        composeTestRule.onNodeWithText("Results").assertDoesNotExist()
    }
}
