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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.data.benchmark.BenchmarkHistoryEntry
import com.arm.voiceassistant.ui.screens.BenchmarkScreen
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.ToastService
import com.arm.voiceassistant.viewmodels.MainViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
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
        ToastService.initialize(appContext)

        viewModel = MainViewModel(application, isTest = true)
    }

    /**
     * Clears test state after each test run.
     */
    @After
    fun tearDown() {
        ToastService.shutdown()
        ToastService.testInterceptor = null
        viewModel = null
        AppContext.getInstance().context = null
    }

    /**
     * Renders [BenchmarkScreen] with a deterministic set of model options.
     *
     * @param modelOptionsOverride List of model names to inject for testing.
     */
    private fun setBenchmarkContent(
        modelOptionsOverride: List<String>,
        historyEntriesOverride: List<BenchmarkHistoryEntry>? = null
    ) {
        composeTestRule.setContent {
            VoiceAssistantTheme {
                BenchmarkScreen(
                    viewModel = viewModel!!,
                    modelOptionsOverride = modelOptionsOverride,
                    historyEntriesOverride = historyEntriesOverride
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

    /**
     * Verifies that saved benchmark history is shown and can be reopened.
     */
    @Test
    fun testSavedHistoryCanBeOpened() {
        val historyEntry = BenchmarkHistoryEntry(
            id = 1L,
            createdAtMs = 1_710_000_000_000L,
            title = "Model: test-model • In:128 Out:128 Ctx:2048 • Threads:4 • Iter:5 • Warm:1",
            summaryJson = """
                {
                  "framework":"mnn",
                  "parameters":{
                    "model_size":"1.00 GB",
                    "num_threads":4,
                    "num_iterations":5,
                    "num_warmup":1,
                    "num_input_tokens":128,
                    "num_output_tokens":128,
                    "context_size":2048
                  },
                  "results":{
                    "mean":{
                      "encode_tokens_per_sec":100.0,
                      "decode_tokens_per_sec":50.0,
                      "ttft_ms":10.0,
                      "total_ms":20.0
                    },
                    "stddev":{
                      "encode_tokens_per_sec":1.0,
                      "decode_tokens_per_sec":2.0,
                      "ttft_ms":0.5,
                      "total_ms":1.5
                    }
                  }
                }
            """.trimIndent()
        )

        setBenchmarkContent(
            modelOptionsOverride = listOf("test-model"),
            historyEntriesOverride = listOf(historyEntry)
        )

        composeTestRule.onNodeWithText("Results").assertExists()
        composeTestRule.onNodeWithTag("benchmark_open_saved_run").performClick()
        composeTestRule.onNodeWithTag("benchmark_saved_result_sheet").assertExists()
    }

    /**
     * Verifies that a saved result can be removed after swiping left.
     */
    @Test
    fun testSavedHistoryCanBeDeleted() {
        val historyEntry = BenchmarkHistoryEntry(
            id = 1L,
            createdAtMs = 1_710_000_000_000L,
            title = "Model: delete-me • In:128 Out:128 Ctx:2048 • Threads:4 • Iter:5 • Warm:1",
            summaryJson = "{}"
        )

        setBenchmarkContent(
            modelOptionsOverride = listOf("test-model"),
            historyEntriesOverride = listOf(historyEntry)
        )

        composeTestRule.onNodeWithTag("benchmark_history_item_1").performTouchInput {
            swipeLeft()
        }
        composeTestRule.onNodeWithTag("benchmark_delete_saved_run_1").performClick()
        composeTestRule.onNodeWithText("Model: delete-me • In:128 Out:128 Ctx:2048 • Threads:4 • Iter:5 • Warm:1")
            .assertDoesNotExist()
    }

    /**
     * Verifies that context sizes below the minimum valid benchmark size are not offered.
     */
    @Test
    fun testContextDropdownOmitsImpossibleSmallOptions() {
        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        composeTestRule.onNodeWithTag("benchmark_context_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_context_option_64").assertDoesNotExist()
        composeTestRule.onNodeWithTag("benchmark_context_option_128").assertDoesNotExist()
        composeTestRule.onNodeWithTag("benchmark_context_option_256").assertExists()
    }

    /**
     * Verifies that context sizes which cannot fit the selected input/output tokens are disabled.
     */
    @Test
    fun testInvalidContextOptionsAreDisabled() {
        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        composeTestRule.onNodeWithTag("benchmark_input_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_input_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_output_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_output_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_context_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_context_option_1024").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("benchmark_context_option_2048").assertIsEnabled()
    }

    /**
     * Verifies that increasing input/output tokens automatically moves context to a valid size.
     */
    @Test
    fun testContextAutomaticallyAdjustsToInputAndOutput() {
        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        composeTestRule.onNodeWithTag("benchmark_context_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_context_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_input_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_input_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_output_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_output_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_context_dropdown").assertTextContains("2048")
        runButton().assertIsEnabled()
    }

    /**
     * Verifies that auto-adjusting context size emits a toast with the new minimum value.
     */
    @Test
    fun testContextAutoAdjustShowsToast() {
        var toastMessage: String? = null
        ToastService.testInterceptor = { toastMessage = it }

        setBenchmarkContent(modelOptionsOverride = listOf("test-model"))

        composeTestRule.onNodeWithTag("benchmark_context_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_context_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_input_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_input_option_512").performClick()

        composeTestRule.onNodeWithTag("benchmark_output_dropdown").performClick()
        composeTestRule.onNodeWithTag("benchmark_output_option_512").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Context size updated to minimum valid value (2048).", toastMessage)
        }
    }
}
