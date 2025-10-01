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
        val json = """
        {
          "chat" : {
            "systemPrompt" : "You are a helpful and factual AI assistant named Orbita. Orbita answers with maximum of two sentences.",
            "applyDefaultChatTemplate" : true,
            "systemTemplate" : "<start_of_turn>system:%s<end_of_turn>",
            "userTemplate"   : "\n<start_of_turn>user:%s<end_of_turn>\n<start_of_turn>model:"
          },
          "model" : {
            "llmModelName" : "mediapipe/gemma-2b/gemma-2b-it-cpu-int4.tflite",
            "isVision" : false
          },
          "runtime" : {
            "batchSize"    : 256,
            "numThreads"   : 5,
            "contextSize"  : 2048
          },
          "stopWords": [
            "Orbita:",
            "User:",
            "AI:",
            "<|endoftext|>",
            "Assistant:",
            "user:",
            "[end of text]",
            "model:",
            "Question:",
            "<|end|>"
          ]
        }
        """.trimIndent()

        return JSONObject(json)
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

        assertEquals(true, defaultConfig.model.isVision)
        assertEquals("<|im_end|>", defaultConfig.stopWords.last())
        assertEquals("<|im_start|>user\n%s<|im_end|>\n<|im_start|>assistant\n", defaultConfig.chat.userTemplate)
        assertEquals( "<|im_start|>system\n%s<|im_end|>\n", defaultConfig.chat.systemTemplate)
        assertEquals("<|im_end|>", defaultConfig.stopWords.last())
        assertEquals(256, defaultConfig.runtime.batchSize)
        assertEquals("$modelPath/llama.cpp/qwen2vl-2b/qwen2vl-2b_Q4_0.gguf", defaultConfig.model.llmModelName)

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

        assertEquals(false, defaultConfig.model.isVision)
        assertEquals("<|im_start|>user\n%s<|im_end|>\n<|im_start|>assistant\n", defaultConfig.chat.userTemplate)
        assertEquals("<|end|>", defaultConfig.stopWords.last())
        assertEquals( "<|system|>%s<|end|>", defaultConfig.chat.systemTemplate)
        assertEquals("<|end|>", defaultConfig.stopWords.last())
        assertEquals(1, defaultConfig.runtime.batchSize)
        assertEquals("$modelPath/onnxruntime-genai/phi-4-mini", defaultConfig.model.llmModelName)
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
        assertEquals("$modelPath/mediapipe/gemma-2b/gemma-2b-it-cpu-int4.tflite", llmConfig.model.llmModelName)
        assertEquals("\n<start_of_turn>user:%s<end_of_turn>\n<start_of_turn>model:", llmConfig.chat.userTemplate)
        assertEquals("<|end|>", llmConfig.stopWords.last())
        assertEquals(256, llmConfig.runtime.batchSize)
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
        assertEquals("$modelPath/mediapipe/gemma-2b/gemma-2b-it-cpu-int4.tflite", llmConfig.model.llmModelName)
        assertEquals("<|end|>", llmConfig.stopWords.last())
        assertFalse(cfg.toString().contains("extraParam"))
        assertFalse(cfg.toString().contains("anotherParam"))
    }

    @Test
    fun checkRequiredFieldMissing() {
        val jsonMissingNumThreads = setupConfigJson()
        jsonMissingNumThreads.remove("runtime")

        val file = File.createTempFile("user-config", ".json").apply { deleteOnExit() }
        file.writeText(jsonMissingNumThreads.toString())

        assertFalse(isValidLlmConfig(file))
    }

    @Test
    fun checkWrongDataTypePassedIn() {
        val configJson = setupConfigJson()
        configJson.remove("runtime")

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
