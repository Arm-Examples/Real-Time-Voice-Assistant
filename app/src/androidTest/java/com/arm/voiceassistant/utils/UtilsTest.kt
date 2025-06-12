/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.util.Log
import com.arm.voiceassistant.utils.Utils.createLlmDefaultConfig
import com.arm.voiceassistant.utils.Utils.isValidLlmConfig
import com.arm.voiceassistant.utils.Utils.readLlmUserConfig
import com.arm.voiceassistant.utils.Utils.removeTags
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test common utilities methods found in utils package
 */
class UtilsTest {

    private val modelPath = "/opt/models"

    private val stopWords:List<String> = mutableListOf(
        "Orbita:", "User:", "AI:", "<|user|>", "Assistant:", "user:",
        "[end of text]", "<|endoftext|>", "model:", "Question:", "\n\n",
        "Consider the following scenario:\n"
    )

    private fun setupConfigJson() : JSONObject{
        return JSONObject().apply {
            put("modelTag", "Assistant")
            put("userTag", "User:")
            put("endTag", "<|end|>")
            put("llmPrefix",
                "Assistant is helpful, polite, honest, good at writing and answers honestly with a maximum of two sentences:"
            )
            put("stopWords", JSONArray(stopWords))
            put("llmModelName", "custom.bin")
            put("batchSize", 5)
            put("numThreads", 6)
        }
    }

    /**
     *  Test remove tags method which removes the known tags
     *  from the transcription
     */
    @Test
    fun testRemoveTags() {
        val sampleTranscriptions = listOf("[BEEP]", "[Coughing]")
        val sampleNonFinishedTranscriptions =  listOf("Coughing", "[BEEP", "Laughing]")

        for( transcription in sampleTranscriptions ) {
            assertEquals("", removeTags(transcription))
        }

        for( transcription in sampleNonFinishedTranscriptions ) {
            assertEquals(transcription, removeTags(transcription))
        }
    }


    /**
     *  Test create default config method for llama.cpp framework
     */
    @Test
    fun testCreateDefaultConfigForLlamaFramework() {
        val expectedLlmPrefix = "Transcript of a dialog, where the User interacts with an AI Assistant named Orbita." +
                "Orbita is helpful, polite, honest, good at writing and answers honestly with a maximum of two sentences" +
                "User:"
        val defaultConfig = createLlmDefaultConfig(modelPath, "llama.cpp")


        assertEquals(expectedLlmPrefix, defaultConfig.llmPrefix)
        assertEquals("", defaultConfig.userTag)
        assertEquals("", defaultConfig.endTag)
        assertEquals(stopWords, defaultConfig.stopWords)
        assertEquals(256, defaultConfig.batchSize)
        assertEquals("$modelPath/llama.cpp/mmModel.gguf", defaultConfig.llmModelName)
    }

    /**
     *  Test create default config method for onnxruntime-genai framework
     */
    @Test
    fun testCreateDefaultConfigForOnnxrtGenaiFramework() {
        val defaultConfig = createLlmDefaultConfig(modelPath, "onnxruntime-genai")

        val expectedLlmPrefix = "<|system|>Transcript of a dialog, where the User interacts with an AI Assistant named Orbita." +
                "Orbita is helpful, polite, honest, good at writing and answers honestly with a maximum of two sentences<|end|><|user|>"
        val stopWordsOnnx = stopWords.plus("<|end|>")

        assertEquals(expectedLlmPrefix, defaultConfig.llmPrefix)
        assertEquals("<|user|>", defaultConfig.userTag)
        assertEquals("<|end|>", defaultConfig.endTag)
        assertEquals(stopWordsOnnx, defaultConfig.stopWords)
        assertEquals(1, defaultConfig.batchSize)
        assertEquals("$modelPath/onnxruntime-genai", defaultConfig.llmModelName)
    }

    @Test
    fun parseValidConfig() {
        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }

        file.writeText(setupConfigJson().toString())
        Log.i("tag", file.toString())
        val cfg = readLlmUserConfig(file, modelPath)

        val gson = Gson()
        val llmConfig = gson.fromJson(cfg.toString(), Utils.UserLlmConfig::class.java)

        // Structure/values
        assertEquals("Assistant", llmConfig.modelTag)
        assertEquals("User:", llmConfig.userTag)
        assertEquals("<|end|>", llmConfig.endTag)
        assertEquals(stopWords, llmConfig.stopWords)
        assertEquals(6, llmConfig.numThreads)
    }

    @Test
    fun handleEmptyStopWords() {
        val jsonConfig = setupConfigJson()
        jsonConfig.put("stopWords", JSONArray("[]"))
        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }

        file.writeText(jsonConfig.toString())
        val cfg = readLlmUserConfig(file, modelPath)

        val gson = Gson()
        val llmConfig = gson.fromJson(cfg.toString(), Utils.UserLlmConfig::class.java)

        assertTrue(llmConfig.stopWords.isEmpty())
    }

    @Test
    fun ignoreExtraJsonFields() {
        val configJson = setupConfigJson()
        configJson.append("extraParam", "extraValue")
        configJson.append("anotherParam", "anotherValue")

        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }
        file.writeText(configJson.toString())

        val cfg = readLlmUserConfig(file, modelPath)

        val gson = Gson()
        val llmConfig = gson.fromJson(cfg.toString(), Utils.UserLlmConfig::class.java)
        assertEquals("Assistant", llmConfig.modelTag)
        assertEquals("User:", llmConfig.userTag)
        assertEquals("<|end|>", llmConfig.endTag)
        assertFalse(cfg.toString().contains("extraParam"))
        assertFalse(cfg.toString().contains("anotherParam"))
    }

    @Test
    fun checkRequiredFieldMissing() {
        val jsonMissingNumThreads = setupConfigJson()
        jsonMissingNumThreads.remove("numThreads")

        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }
        file.writeText(jsonMissingNumThreads.toString())

        assertFalse(isValidLlmConfig(file))
    }

    @Test
    fun checkWrongDataTypePassedIn() {
        val configJson = setupConfigJson()
        configJson.remove("numThreads")

        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }
        file.writeText(configJson.toString())

        assertFalse(isValidLlmConfig(file))
    }

    @Test
    fun checkInvalidJsonContent() {
        val notValidJson = "{ not: valid json }"
        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }
        file.writeText(notValidJson)
        assertFalse(isValidLlmConfig(file))
    }
}
