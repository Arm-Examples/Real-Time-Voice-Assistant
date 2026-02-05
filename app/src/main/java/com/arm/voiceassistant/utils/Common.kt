/*
 * SPDX-FileCopyrightText: Copyright 2024-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
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
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicReference

/**
 * Global Toast Service which can be used to display messages to user from anywhere in the application
 */
object ToastService {
    @Volatile
    private var appContext: Context? = null
    private val queue: BlockingQueue<String> = LinkedBlockingQueue(10)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var testInterceptor: ((String) -> Unit)? = null
    private data class LastToast(val msg: String, val timeMs: Long)
    private val lastToast = AtomicReference<LastToast?>(null)
    private val consumerStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (consumerStarted.compareAndSet(false, true)) {
            startConsumer()
        }
    }

    /** Enqueue a toast message to be displayed */
    fun showToast(message: String) {
        if (appContext == null) {
            Log.w("ToastService", "ToastService not initialized. Dropping toast: \"$message\"")
            return
        }
        if (message.isBlank()) return

        val now = System.currentTimeMillis()

        while (true) {
            val prev = lastToast.get()
            if (prev != null && prev.msg == message && now - prev.timeMs < 2000) {
                Log.d("ToastService", "Duplicate toast suppressed within 2s: $message")
                return
            }
            val next = LastToast(message, now)
            if (lastToast.compareAndSet(prev, next)) break
        }

        testInterceptor?.invoke(message)
        // Avoid blocking in the event toast queue is full
        if (!queue.offer(message)) {
            Log.w("ToastService", "Toast queue full; dropping toast: \"$message\"")
        }
    }


    /** Display the next toast in the queue (called on main thread) */
    private fun startConsumer() {
        scope.launch {
            try {
                while (isActive) {
                    val msg = queue.take()
                    mainHandler.post {
                        appContext?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
                    }
                    delay(3_000)
                }
            } catch (_: CancellationException) {
                // normal shutdown
            }
        }
    }

/** Call to cleanly cancel the coroutine consumer */
fun shutdown() {
    scope.cancel()
    consumerStarted.set(false)
}
}

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
    data class ChatConfig(
        val systemPrompt: String,
        val applyDefaultChatTemplate: Boolean,
        val systemTemplate: String,
        val userTemplate: String
    )

    data class ModelConfig(
        val llmModelName: String,
        val isVision: Boolean,
        val projModelName: String? = null
    )

    data class RuntimeConfig(
        val batchSize: Int,
        val numThreads: Int,
        val contextSize: Int
    )

    data class UserLlmConfig(
        val chat: ChatConfig,
        val model: ModelConfig,
        val runtime: RuntimeConfig,
        val stopWords: List<String>
            )


    /**
     * Creates a default [UserLlmConfig] object for the given model path and framework.
     * @param modelPath The file system path to the model
     * @param framework The LLM backend framework to use (e.g., "llama.cpp", "onnx")
     * @return A default-configured [UserLlmConfig] instance
     */
    fun createLlmDefaultConfig(modelPath: String, framework: String): UserLlmConfig
        {
        var applyDefaultChatTemplate = false
        var llmMmProjModelName: String
        var isVision = true
        val systemPrompt = "You are a helpful and factual AI assistant named Orbita. Orbita answers with maximum of four sentences."
        var systemTemplate = ""
        var userTemplate = ""
        var stopWords:List<String> = mutableListOf(
                "Orbita:", "User:", "AI:", "<|user|>", "Assistant:", "user:",
            "[end of text]", "<|endoftext|>", "model:", "Question:", "\n\n",
            "Consider the following scenario:\n", "<|im_end|>"
            )
        var llmModelName = ""
        var modelPointer = ""
        var projPointer = ""
        var batchSize = 1
        val contextSize =2048
        when (framework) {
            "llama.cpp" -> {
                llmModelName = "llama.cpp/qwen2vl-2b/qwen2vl-2b_Q4_0.gguf"
                llmMmProjModelName = "llama.cpp/qwen2vl-2b/qwen2vl-2b_Q8_0_proj.gguf"
                isVision = true
                systemTemplate = "<|im_start|>system\n%s<|im_end|>\n"
                userTemplate =  "<|im_start|>user\n%s<|im_end|>\n<|im_start|>assistant\n"
                batchSize = 256
                projPointer = "$modelPath/$llmMmProjModelName"
            }
            "onnxruntime-genai" -> {
                llmModelName = "onnxruntime-genai/phi-4-mini"
                stopWords= stopWords.plus("<|end|>")
                isVision = false
                systemTemplate = "<|system|>%s<|end|>"
                userTemplate =  "<|user|>%s<|end|><|assistant|>"
                batchSize = 1
            }
            "mnn" -> {
                llmModelName = "mnn/qwen25vl-3b/"
                isVision = true
                systemTemplate = "<|im_start|>system\n%s<|im_end|>"
                userTemplate = "<|im_start|>user\n%s<|im_end|>\n<|im_start|>assistant\n"
                batchSize = 1
            }
            "mediapipe" -> {
                llmModelName = "mediapipe/gemma-2b/gemma-2b-it-cpu-int4.tflite"
                isVision = false
                systemTemplate = "<start_of_turn>system:%s<end_of_turn>"
                userTemplate = "\n<start_of_turn>user:%s<end_of_turn>\n<start_of_turn>model:"
                batchSize = 1
                applyDefaultChatTemplate = true
            }
        }
            modelPointer = "$modelPath/$llmModelName"

        //Default number of thread
        val cores = Runtime.getRuntime().availableProcessors()
        val numThreads = if (cores >= 8) 4 else 2
        return UserLlmConfig(
            ChatConfig(systemPrompt,applyDefaultChatTemplate,systemTemplate,userTemplate),
            ModelConfig(modelPointer,isVision,projPointer),
            RuntimeConfig(batchSize,numThreads,contextSize),
            stopWords)
    }

    fun isValidLlmConfig(file: File): Boolean {
        return try {
            val content = file.readText()
            if (content.isBlank()) return false
            val config = Gson().fromJson(content, UserLlmConfig::class.java)
            config != null &&
                    config.chat.systemPrompt.isNotBlank() &&
                    config.model.llmModelName.isNotBlank() &&
                    config.chat.systemTemplate.isNotBlank() &&
                    config.chat.userTemplate.isNotBlank() &&
                    config.runtime.numThreads > 0 &&
                    config.runtime.numThreads <= Runtime.getRuntime().availableProcessors() &&
                    config.runtime.batchSize > 0 &&
                    config.runtime.contextSize > 0 &&
                    config.stopWords.isNotEmpty()
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

            // Update model paths

            val modelObj = configJson.getJSONObject("model")
            modelObj.put("llmModelName", "$modelPath/${modelObj.getString("llmModelName")}")

            if (!modelObj.isNull("projModelName")) {
                modelObj.put(
                    "projModelName",
                    "$modelPath/${modelObj.getString("projModelName")}"
                )
                Log.i(VOICE_ASSISTANT_TAG, modelObj.getString("projModelName"))
            }


            Log.d(VOICE_ASSISTANT_TAG, modelObj.getString("llmModelName"))


            configJson.put("model", modelObj)

            return configJson
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "LLM configuration invalid: Exception: $e")
        }

        return null

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

    /**
     * Retrieves the config file for given the llmFramework
     * @param llmFramework The execution framework used to run the llm model
     * @return Filename of user provided configurations file for the framework
     */
    fun getLlmConfig(llmFramework: String):String
    {
        return when (llmFramework) {
            "llama.cpp" -> "llamaVisionConfigUser.json"
            "onnxruntime-genai" -> "onnxTextConfigUser.json"
            "mnn" -> "mnnVisionConfigUser.json"
            "mediapipe" -> "mediapipeTextConfigUser.json"
            else -> "llamaVisionConfigUser.json"
        }
    }
}
