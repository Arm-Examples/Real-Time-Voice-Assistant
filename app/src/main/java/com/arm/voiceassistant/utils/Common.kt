/*
 * SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.arm.llm.LlamaConfig
import com.arm.stt.WhisperConfig
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
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

    fun createDefaultConfig(modelPath: String): LlamaConfig
    {
        val llmModelName = "model.gguf"
        val stopWords: List<String> = mutableListOf(
            "Orbita:", "User:", "AI:","<|user|>", "Assistant:","user:",
            "[end of text]", "<|endoftext|>", "model:", "Question:", "\n\n",
            "Consider the following scenario:\n"
        )
        val llmPrefix =
            "Transcript of a dialog, where the User interacts with an AI Assistant named Orbita." +
                    "Orbita is helpful, polite, honest, good at writing and answers honestly and concisely" +
                    "User:"

        val modelTag = " \n Orbita:"
        val modelPointer = "$modelPath/$llmModelName"

        //Default number of thread
        val cores = Runtime.getRuntime().availableProcessors()
        val numThreads = if (cores >= 8) 4 else 2

        return LlamaConfig(
            modelTag,
            stopWords,
            modelPointer,
            llmPrefix,
            numThreads
        )
    }

    /**
     * Check if config file is valid
     */
    fun isValidConfig(file: File): Boolean {
        return try {
            val content = file.readText()
            if (content.isBlank()) return false

            val jsonObject = JSONObject(content)
            // TO-DO: Checks can be amended
            // Check if required keys exist and have valid values.
            jsonObject.has("modelTag") &&
                    jsonObject.has("stopWords") &&
                    jsonObject.has("llmModelName") &&
                    jsonObject.has("llmPrefix") &&
                    jsonObject.has("numThreads") &&
                    jsonObject.getString("llmModelName").isNotEmpty() &&
                    jsonObject.getInt("numThreads") > 0 &&
                    jsonObject.getInt("numThreads") <= Runtime.getRuntime().availableProcessors()
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Invalid configuration file: missing or invalid configuration values.", e)
            false
        }
    }

    /**
     * Read configurations defined by User
     */
    fun readUserConfig(file: File, modelPath: String): LlamaConfig {
        val content = file.readText()
        val jsonObject = JSONObject(content)

        val modelTag = jsonObject.getString("modelTag")

        val stopWordsJsonArray = jsonObject.getJSONArray("stopWords")
        val stopWords = mutableListOf<String>()
        for (i in 0 until stopWordsJsonArray.length()) {
            stopWords.add(stopWordsJsonArray.getString(i))
        }

        val llmModelName = jsonObject.getString("llmModelName")
        val llmPrefix = jsonObject.getString("llmPrefix")
        val numThreads = jsonObject.getInt("numThreads")
        val modelPointer = "$modelPath/$llmModelName"

        return LlamaConfig(modelTag, stopWords, modelPointer, llmPrefix, numThreads)
    }

    /**
     * Check if config file is valid
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
     */
    fun createWhisperDefaultConfig() : WhisperConfig
    {
        val printRealtime = true;
        val printProgress = false;
        val printTimeStamps = true
        val printSpecial = false;
        val translate = false;
        val language = "en";
        val numThreads = 4;
        val offsetMs = 0;
        val noContext = true;
        val singleSegment = false;
        return WhisperConfig(printRealtime, printProgress, printTimeStamps, printSpecial, translate,
            language, numThreads, offsetMs, noContext, singleSegment)
    }

    /**
     * Remove known tags from transcribed string
     */
    fun removeTags(transcribed: String): String {
        val tagsToRemove = "\\[.*?\\]|\\(.*?\\)".toRegex()
        return transcribed.replace(tagsToRemove, "")
    }

    /**
     * Remove characters such as emojis from the text string and return it
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
     */
    fun cleanupLine(lines: ArrayList<String>, currentLine: String) : String {
        var sanitizedWords = removeNonAsciiCharacters(currentLine)
        sanitizedWords = removeSelectCharacters(lines, sanitizedWords)
        return sanitizedWords
    }

    /**
     * Return true if the string lines have a markdown code block
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

    fun responseComplete(token: String) : Boolean {
        return token.contains(Constants.EOS, true) ||
                token.contains(Constants.NEXT_MESSAGE, true)
    }
}
