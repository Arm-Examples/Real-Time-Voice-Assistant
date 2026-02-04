/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.viewmodels

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arm.voiceassistant.BuildConfig
import com.arm.voiceassistant.utils.AppContext
import com.arm.voiceassistant.utils.Constants.LLM_CONTEXT_CAPACITY_ERROR
import com.arm.voiceassistant.utils.Constants.LLM_INITIALIZATION_ERROR
import com.arm.voiceassistant.utils.Utils
import com.arm.voiceassistant.utils.Utils.UserLlmConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumentation coverage for [MainViewModel] error handling on wrong/missing configs, context overflow, and context reset.
 */
@RunWith(AndroidJUnit4::class)
class ErrorMessageTest {

    private var mainViewModel: MainViewModel? = null
    private var mainUiState: StateFlow<MainUiState>? = null
    private lateinit var testEnvDir: File
    private lateinit var llmConfig: String
    private lateinit var tmpDirPath: File
    private val maxIterations = 20

    @Before
    fun setupLLMModel() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val application = targetContext.applicationContext as Application

        AppContext.getInstance().context = application

        val downloadDir = targetContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!

        tmpDirPath = targetContext.cacheDir
        testEnvDir = File(downloadDir, "test_env")

        if (testEnvDir.exists()) testEnvDir.deleteRecursively()
        testEnvDir.mkdirs()

        llmConfig = Utils.getLlmConfig(BuildConfig.LLM_FRAMEWORK)

        val requiredFiles = listOf(
            llmConfig,
            BuildConfig.LLM_FRAMEWORK,
            "whisperConfigUser.json",
            "couple.bmp"
        )

        requiredFiles.forEach { name ->
            val src = File(downloadDir, name)
            val dst = File(testEnvDir, name)

            if (!src.exists()) {
                throw AssertionError("Missing required test file or directory: ${src.absolutePath}")
            }

            if (src.isDirectory) src.copyRecursively(dst, overwrite = true)
            else src.copyTo(dst, overwrite = true)
        }

        Log.d(TAG, "test_env contents:")
        logDirectoryContents(testEnvDir)

        mainViewModel = MainViewModel(application, true)
        mainUiState = mainViewModel?.uiState
    }

    @After
    fun tearDown() {
        mainViewModel = null
        mainUiState = null
        if (testEnvDir.exists()) testEnvDir.deleteRecursively()
    }

    /**
     * Ensures initializeLLM surfaces an error when the config/model asset is missing.
     */
    @Test
    fun testInitialLlm() = runBlocking {
        if (testEnvDir.exists()) {
            File(testEnvDir, llmConfig).delete()
        }

        mainViewModel?.pipeline?.initializeLLM(testEnvDir.toString())

        val state = awaitErrorState(true)
        Assert.assertEquals(LLM_INITIALIZATION_ERROR, state.error.message)
    }

    /**
     * Verifies malformed LLM config (empty model name) triggers the expected error message.
     */
    @Test
    fun testConfigError() = runBlocking {
        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val configFile = File(testEnvDir, llmConfig)

        val config = Gson().fromJson(configFile.readText(), UserLlmConfig::class.java)

        val updatedModel = config.model.copy(
            llmModelName = "",
            projModelName = config.model.projModelName,
            isVision = config.model.isVision
        )

        val updatedConfig = config.copy(model = updatedModel)

        configFile.writeText(gson.toJson(updatedConfig))

        mainViewModel?.pipeline?.initializeLLM(testEnvDir.toString())

        val state = awaitErrorState(true)
        Assert.assertEquals(LLM_INITIALIZATION_ERROR, state.error.message)
    }

    /**
     * Confirms overflowing the configured context size surfaces a context-capacity error.
     * Also verifies usual query response after context reset.
     */
    @Test
    fun testContextFlowError() = runBlocking {
        var question = "tell me a rhyme in hundred words"

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val configFile = File(testEnvDir, llmConfig)

        val config = Gson().fromJson(configFile.readText(), UserLlmConfig::class.java)

        val updatedRuntime = config.runtime.copy(contextSize = 80, batchSize = 64)
        val updatedConfig = config.copy(runtime = updatedRuntime)

        configFile.writeText(gson.toJson(updatedConfig))

        mainViewModel?.pipeline?.initializeLLM(testEnvDir.toString())
        var circuitBreaker = 0
        mainViewModel?.llm?.chatProgress?.let {
            while (it < 100) {
                runBlocking {

                    mainViewModel?.pipeline?.generateResponseTokens(question.repeat(20))
                }
                mainViewModel?.cancelLLMResponseGenerationJob()
                if (mainUiState?.value?.error?.state == true) break
                if (++circuitBreaker > maxIterations) break
            }
        }
        val state = awaitErrorState(true)
        Assert.assertEquals(
            LLM_CONTEXT_CAPACITY_ERROR,
            state.error.message
        )
        mainViewModel?.pipeline?.resetContext()
        mainViewModel?.resetUserText()
        mainViewModel?.pipeline?.generateResponseTokens(question)
        val cleared = awaitErrorState(false)
        Assert.assertFalse(cleared.error.state)
    }

    /**
     * Exercises image dialog inputs to ensure they contribute to context overflow signaling.
     */
    @Test
    fun testImageEmbedding() = runBlocking {
        var question = "Describe the image briefly"
        val testImage = "couple.bmp"

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val configFile = File(testEnvDir, llmConfig)
        val imageFile = File(testEnvDir, testImage)

        val config = Gson().fromJson(configFile.readText(), UserLlmConfig::class.java)
        val updatedRuntime = config.runtime.copy(contextSize = 80, batchSize = 64)
        val updatedConfig = config.copy(runtime = updatedRuntime)

        configFile.writeText(gson.toJson(updatedConfig))

        mainViewModel?.pipeline?.initializeLLM(testEnvDir.toString())

        if (mainViewModel?.llm?.supportsImageInput() == true) {
            var circuitBreaker = 0
            mainViewModel?.llm?.chatProgress?.let {
                while (it < 100) {
                    mainViewModel?.pipeline?.addImageToLLmDialog(
                        imageFile,
                        tempDirPath = tmpDirPath.toString()
                    )
                    mainViewModel?.pipeline?.generateResponseTokens(question)

                    if (mainUiState?.value?.error?.state == true) {
                        break
                    }
                    if (++circuitBreaker > maxIterations) break
                }
            }

            awaitErrorState(true)
            val state = awaitErrorState(true)

            Assert.assertEquals(
                LLM_CONTEXT_CAPACITY_ERROR,
                state.error.message
            )
            mainViewModel?.pipeline?.resetContext()
            mainViewModel?.resetUserText()

            mainViewModel?.pipeline?.generateResponseTokens(question)

            awaitErrorState(false)
        }
    }

    /**
     * Waits for the UI state's error flag to match the expected value, with a timeout to prevent hangs.
     */
    private suspend fun awaitErrorState(expected: Boolean): MainUiState =
        withTimeout(ERROR_STATE_TIMEOUT_MS) {
            mainUiState!!.first { it.error.state == expected }
        }

    private fun logDirectoryContents(dir: File, indent: String = "") {
        if (!dir.exists() || !dir.canRead()) {
            Log.d(TAG, "${indent}${dir.name} EMPTY or unreadable! (${dir.absolutePath})")
            return
        }

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                Log.d(TAG, "${indent}- ${file.name}/")
                logDirectoryContents(file, "$indent  ")
            } else {
                Log.d(TAG, "${indent}- ${file.name} (size=${file.length()} bytes)")
            }
        } ?: Log.d(TAG, "${indent}(empty)")
    }

    companion object {
        private const val TAG = "ErrorToastTest"
        private const val ERROR_STATE_TIMEOUT_MS = 2_000L
    }
}
