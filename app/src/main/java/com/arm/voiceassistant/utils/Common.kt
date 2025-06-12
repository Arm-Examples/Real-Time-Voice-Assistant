/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arm.voiceassistant.utils
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlin.concurrent.Volatile
import java.io.File
import org.json.JSONObject

/**
 * Container for the context which is needed by one of the LLMs
 */
class AppContext private constructor() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AppContext? = null
        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: AppContext().also { instance = it }
            }
    }
    var context: Context? = null
}
object Utils {

    data class UserLlmConfig(
        val modelTag: String,
        val userTag: String,
        val endTag: String,
        val llmPrefix: String,
        val mediaTag: String,
        val stopWords: List<String>,
        val inputModalities : List<String>,
        val outputModalities : List<String>,
        val llmModelName: String,
        val llmMmProjModelName: String,
        val batchSize: Int,
        val numThreads: Int
    )

    /**
     * Creates a default [UserLlmConfig] object for the given model path and framework.
     * @param modelPath The file system path to the model
     * @param framework The LLM backend framework to use (e.g., "llama.cpp", "onnx")
     * @return A default-configured [UserLlmConfig] instance
     */
    fun createLlmDefaultConfig(modelPath: String, framework: String): UserLlmConfig
    {
        val llmModelName: String
        var llmMmProjModelName: String
        var userTag = ""
        var endTag = ""
        var stopWords:List<String> = mutableListOf(
            "Orbita:", "User:", "AI:", "<|user|>", "Assistant:", "user:",
            "[end of text]", "<|endoftext|>", "model:", "Question:", "\n\n",
            "Consider the following scenario:\n"
        )
        var inputModalities:List<String> = mutableListOf()
        var outputModalities:List<String> = mutableListOf()
        var llmPrefix = ""
        var modelTag = ""
        var modelPointer = ""
        var mediaTag = ""
        var batchSize = 1
        llmMmProjModelName = ""
        val transcript = "Transcript of a dialog, where the User interacts with an AI Assistant named Orbita."
        if (framework == "llama.cpp") {
            llmModelName = "llama.cpp/mmModel.gguf"
            llmMmProjModelName = "llama.cpp/mmproj.gguf"
            llmPrefix = transcript + "Orbita is helpful, polite, honest, good at writing and answers honestly with a maximum of two sentences" + "User:"
            modelTag = " \n Orbita:"
            mediaTag = "<__media__>"
            batchSize = 256
            inputModalities = listOf("text", "image")
            outputModalities = listOf("text")
            modelPointer = "$modelPath/$llmModelName"
        }
        else if (framework == "onnxruntime-genai")
        {
            llmModelName = "onnxruntime-genai"
            stopWords= stopWords.plus("<|end|>")
            llmPrefix =
                "<|system|>${transcript}Orbita is helpful, polite, honest, good at writing and answers honestly with a maximum of two sentences<|end|><|user|>"
            modelTag = "<|assistant|>"
            userTag = "<|user|>"
            endTag = "<|end|>"
            modelPointer = "$modelPath/$llmModelName"
            batchSize = 1
            inputModalities = listOf("text", "image")
            outputModalities = listOf("text")
        }
        //Default number of thread
        val cores = Runtime.getRuntime().availableProcessors()
        val numThreads = if (cores >= 8) 4 else 2
        return UserLlmConfig(
            modelTag,
            userTag,
            endTag,
            llmPrefix,
            mediaTag,
            stopWords,
            inputModalities,
            outputModalities,
            modelPointer,
            llmMmProjModelName,
            batchSize,
            numThreads
        )
    }

    /**
     * Check if config file is valid
     */
    fun isValidLlmConfig(file: File): Boolean {
        return try {
            val content = file.readText()
            if (content.isBlank()) return false
            val config = Gson().fromJson(content, UserLlmConfig::class.java)
            config != null &&
                    config.modelTag.isNotBlank() &&
                    config.llmModelName.isNotBlank() &&
                    config.inputModalities.isNotEmpty() &&
                    config.outputModalities.isNotEmpty() &&
                    config.llmPrefix.isNotBlank() &&
                    config.stopWords.isNotEmpty() &&
                    config.numThreads > 0 &&
                    config.numThreads <= Runtime.getRuntime().availableProcessors()
        } catch (e: JsonSyntaxException) {
            Log.e(VOICE_ASSISTANT_TAG, "Invalid configuration JSON syntax", e)
            false
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Invalid configuration file", e)
            false
        }
    }

    /**
     * Read LLM configurations defined by User
     * @param file The user configuration file to read
     * @param modelPath The path to the model, included in the resulting config
     * @return An [UserLlmConfig] constructed from the file's contents
     */
    fun readLlmUserConfig(file: File, modelPath: String): JSONObject? {
        try {
            val content = file.readText()
            val gson = Gson()
            val userLlmConfig: UserLlmConfig = gson.fromJson(content, UserLlmConfig::class.java)
            val configJson = JSONObject(gson.toJson(userLlmConfig))
            configJson.put("llmModelName", modelPath + "/" + configJson.getString("llmModelName"))
            if(configJson.has("llmMmProjModelName")) {
                configJson.put(
                    "llmMmProjModelName",
                    modelPath + "/" + configJson.getString("llmMmProjModelName")
                )
            }
            return configJson
        } catch (e : Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "LLM configuration invalid: Exception: $e")
            return null
        }
    }

    /**
     * Check if config file is valid
     * @param file The configuration file to validate
     * @return true if the file contains valid and non-empty JSON content, false otherwise
     */
    fun isValidWhisperConfig(file: File): Boolean {
        return try {
            // Read file contents
            val content = file.readText()
            if (content.isBlank()) return false
            // Parse into JSON
            val jsonObject = JSONObject(content)
            // Example checks: ensure required keys are present and valid.
            // Adjust these checks depending on which fields you consider "required".
            jsonObject.has("printRealtime") &&
                    jsonObject.has("printProgress") &&
                    jsonObject.has("printTimeStamps") &&
                    jsonObject.has("printSpecial") &&
                    jsonObject.has("translate") &&
                    jsonObject.has("language") &&
                    jsonObject.has("numThreads") &&
                    jsonObject.has("offsetMs") &&
                    jsonObject.has("noContext") &&
                    jsonObject.has("singleSegment") &&
                    // Check that the language field is not empty
                    jsonObject.getString("language").isNotEmpty() &&
                    // Validate number of threads
                    (jsonObject.getInt("numThreads") > 0) &&
                    (jsonObject.getInt("numThreads") <= Runtime.getRuntime().availableProcessors())
        } catch (e: Exception) {
            // Log or handle the exception as you see fit
            Log.e(VOICE_ASSISTANT_TAG, "Invalid configuration file: missing or invalid configuration values.", e)
            false
        }
    }

    /**
     * Reads a JSON file containing Whisper configuration and returns a WhisperConfig object.
     * @param file The Whisper configuration file to read
     * @return A [WhisperConfig] object parsed from the JSON content
     */
    fun readWhisperUserConfig(file: File): WhisperConfig {
        // Read the file content
        val content = file.readText()
        val jsonObject = JSONObject(content)
        // Extract each field from the JSON, with sensible defaults if missing
        val printRealtime   = jsonObject.optBoolean("printRealtime",   true)
        val printProgress   = jsonObject.optBoolean("printProgress",   false)
        val printTimeStamps = jsonObject.optBoolean("printTimeStamps", true)
        val printSpecial    = jsonObject.optBoolean("printSpecial",    false)
        val translate       = jsonObject.optBoolean("translate",       false)
        val language        = jsonObject.optString("language",         "en")
        val numThreads      = jsonObject.optInt("numThreads",          4)
        val offsetMs        = jsonObject.optInt("offsetMs",            0)
        val noContext       = jsonObject.optBoolean("noContext",       true)
        val singleSegment   = jsonObject.optBoolean("singleSegment",   false)
        return WhisperConfig(
            printRealtime,
            printProgress,
            printTimeStamps,
            printSpecial,
            translate,
            language,
            numThreads,
            offsetMs,
            noContext,
            singleSegment
        )
    }

    /**
     * Create default configurations for whisper
     * @return A [WhisperConfig] populated with preset values for common use cases
     */
    fun createWhisperDefaultConfig() : WhisperConfig
    {
        val printRealtime = true
        val printProgress = false
        val printTimeStamps = true
        val printSpecial = false
        val translate = false
        val language = "en"
        val numThreads = 4
        val offsetMs = 0
        val noContext = true
        val singleSegment = false
        return WhisperConfig(printRealtime, printProgress, printTimeStamps, printSpecial, translate,
            language, numThreads, offsetMs, noContext, singleSegment)
    }

    /**
     * Remove known tags from transcribed string
     * @param transcribed The transcribed text to clean
     * @return The cleaned string without tags
     */
    fun removeTags(transcribed: String): String {
        val tagsToRemove = "\\[.*?\\]|\\(.*?\\)".toRegex()
        return transcribed.replace(tagsToRemove, "")
    }

    /**
     * Remove characters such as emojis from the text string and return it
     * @param text The input string to sanitize
     * @return The ASCII-only version of the input string
     */
    private fun removeNonAsciiCharacters(text: String) : String {
        // Remove any character not between 0x0 and 0x7E. On Linux,
        // you can run "man -7 ascii" to see what will be included
        val regex = Regex("[^\\x0-\\x7E]")
        return text.replace(regex, "")
    }

    /**
     * Remove select characters from the current string. Previous lines passed
     * to determine context.
     * @param currentLine The line to sanitize
     * @return The sanitized string with unnecessary characters removed
     */
    private fun removeSelectCharacters(lines: ArrayList<String>, currentLine: String) : String {
        var sanitizedWords = currentLine
        if (! linesContainMarkdownCodeBlock(lines)) {
            sanitizedWords = sanitizedWords.replace("*", "")
        }
        return sanitizedWords
    }

    /**
     * Cleanup current line
     * @param lines Previous lines used for context (e.g., markdown)
     * @param currentLine The line to sanitize
     * @return A cleaned-up version of the input line
     */
    fun cleanupLine(lines: ArrayList<String>, currentLine: String) : String {
        var sanitizedWords = removeNonAsciiCharacters(currentLine)
        sanitizedWords = removeSelectCharacters(lines, sanitizedWords)
        return sanitizedWords
    }

    /**
     * Return true if the string lines have a markdown code block
     * @param lines List of previous lines
     * @return true if a markdown code block is detected, false otherwise
     */
    private fun linesContainMarkdownCodeBlock(lines: ArrayList<String>) : Boolean {
        for (line in lines) {
            if (line.startsWith(Constants.MARKDOWN_CODE))
                return true
        }
        return false
    }

    /**
     * Return true if the sentence should be broken for generated audio
     * @param tokens The latest tokens received
     * @param currentLine The sentence accumulated so far
     * @return true if the sentence should be broken, false otherwise
     */
    fun breakSentence(tokens: String, currentLine: String): Boolean {
        var result = false
        // English sentence on average has 15-20 words. We go past the average
        // to avoid breaks towards the end of sentences
        val averageNumberOfWordsInALine = 23
        val stopCharacters = arrayOf("!", "?")
        if (stopCharacters.contains(tokens)) {
            result = true
        } else if (currentLine.count { it == ' ' } > averageNumberOfWordsInALine) {
            result = true
        }
        return result
    }

    /**
     * Return true if the sentence should be broken for generated audio
     * @param responses List of previously spoken or generated segments
     * @param tokens The next token to evaluate
     * @return true if the sentence should be split, false otherwise
     */
    fun breakSentenceAtPeriod(responses: List<String>, tokens: String) : Boolean {
        var result = false
        val endsWithPeriod = responses.last().endsWith(".")
        if (endsWithPeriod && tokens.startsWith(" ")) {
            // Avoid splitting speech synthesis on period if we have text such as "Washington D.C."
            result = true
        }
        return result
    }

    /**
     * Checks whether the given token indicates the end of a response.
     * @param token The text token to evaluate
     * @return true if the token represents the end of a response, false otherwise
     */
    fun responseComplete(token: String) : Boolean {
        return token.contains(Constants.EOS, true) ||
                token.contains(Constants.NEXT_MESSAGE, true)
    }
}
