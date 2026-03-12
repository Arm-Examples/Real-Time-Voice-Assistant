/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.data.benchmark

import android.util.Log
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val BENCHMARK_HISTORY_FILE_NAME = "benchmark_history.json"
private const val BENCHMARK_HISTORY_LIMIT = 20

data class BenchmarkOverheadSummary(
    val javaEncodeTotalMs: Double,
    val coreCppEncodeTotalMs: Double,
    val encodeOverheadMs: Double,
    val javaDecodeTotalMs: Double,
    val coreCppDecodeTotalMs: Double,
    val decodeOverheadMs: Double
)

val BenchmarkOverheadSummary.isValid: Boolean
    get() =
        javaEncodeTotalMs >= 0.0 &&
            coreCppEncodeTotalMs >= 0.0 &&
            encodeOverheadMs >= 0.0 &&
            javaDecodeTotalMs >= 0.0 &&
            coreCppDecodeTotalMs >= 0.0 &&
            decodeOverheadMs >= 0.0

data class BenchmarkHistoryEntry(
    val id: Long,
    val createdAtMs: Long,
    val title: String,
    val summaryJson: String,
    val javaEncodeTotalMs: Double? = null,
    val coreCppEncodeTotalMs: Double? = null,
    val encodeOverheadMs: Double? = null,
    val javaDecodeTotalMs: Double? = null,
    val coreCppDecodeTotalMs: Double? = null,
    val decodeOverheadMs: Double? = null
) {
    init {
        require(id > 0) { "Benchmark history id must be positive." }
        require(createdAtMs > 0) { "Benchmark history timestamp must be positive." }
        require(title.isNotBlank()) { "Benchmark history title must not be blank." }
    }

    /**
     * Converts the saved nullable timing fields into an overhead summary.
     */
    fun toOverheadSummary(): BenchmarkOverheadSummary? {
        val javaEncode = javaEncodeTotalMs ?: return null
        val coreEncode = coreCppEncodeTotalMs ?: return null
        val encodeOverhead = encodeOverheadMs ?: return null
        val javaDecode = javaDecodeTotalMs ?: return null
        val coreDecode = coreCppDecodeTotalMs ?: return null
        val decodeOverhead = decodeOverheadMs ?: return null
        return BenchmarkOverheadSummary(
            javaEncodeTotalMs = javaEncode,
            coreCppEncodeTotalMs = coreEncode,
            encodeOverheadMs = encodeOverhead,
            javaDecodeTotalMs = javaDecode,
            coreCppDecodeTotalMs = coreDecode,
            decodeOverheadMs = decodeOverhead
        )
    }
}

/**
 * Persists benchmark history in app-private storage.
 */
class BenchmarkHistoryRepository(filesDir: File) {
    private val benchmarkHistoryFile = File(filesDir, BENCHMARK_HISTORY_FILE_NAME)
    private val benchmarkHistoryMutex = Mutex()

    /**
     * Returns the locally saved benchmark history entries.
     */
    suspend fun getHistory(): List<BenchmarkHistoryEntry> = withContext(Dispatchers.IO) {
        benchmarkHistoryMutex.withLock {
            readHistoryLocked()
        }
    }

    /**
     * Saves a benchmark run to local history.
     */
    suspend fun saveEntry(
        title: String,
        summaryJson: String,
        overheads: BenchmarkOverheadSummary?
    ) = withContext(Dispatchers.IO) {
        benchmarkHistoryMutex.withLock {
            val timestampMs = System.currentTimeMillis()
            val updatedEntries = buildList {
                add(createHistoryEntry(timestampMs, title, summaryJson, overheads))
                addAll(readHistoryLocked())
            }.take(BENCHMARK_HISTORY_LIMIT)

            writeHistoryLocked(updatedEntries)
        }
    }

    /**
     * Deletes a saved benchmark run from local history.
     */
    suspend fun deleteEntry(entryId: Long) = withContext(Dispatchers.IO) {
        benchmarkHistoryMutex.withLock {
            val updatedEntries = readHistoryLocked().filterNot { it.id == entryId }
            writeHistoryLocked(updatedEntries)
        }
    }

    /**
     * Reads benchmark history entries from app-private storage.
     */
    private fun readHistoryLocked(): List<BenchmarkHistoryEntry> {
        if (!benchmarkHistoryFile.exists()) {
            return emptyList()
        }

        val rawJson = runCatching { benchmarkHistoryFile.readText() }.getOrElse {
            Log.w(VOICE_ASSISTANT_TAG, "Failed to read benchmark history", it)
            return emptyList()
        }
        if (rawJson.isBlank()) {
            return emptyList()
        }

        return runCatching {
            val entries = JSONArray(rawJson)
            buildList {
                for (index in 0 until entries.length()) {
                    val item = entries.optJSONObject(index) ?: continue
                    val entry = runCatching {
                        item.toBenchmarkHistoryEntry()
                    }.getOrElse { error ->
                        Log.w(
                            VOICE_ASSISTANT_TAG,
                            "Skipping invalid benchmark history entry at index $index",
                            error
                        )
                        null
                    }
                    if (entry != null) {
                        add(entry)
                    }
                }
            }.sortedByDescending { it.createdAtMs }
        }.getOrElse {
            Log.w(VOICE_ASSISTANT_TAG, "Failed to parse benchmark history", it)
            emptyList()
        }
    }

    /**
     * Writes benchmark history entries to app-private storage.
     */
    private fun writeHistoryLocked(entries: List<BenchmarkHistoryEntry>) {
        val historyArray = JSONArray()
        entries.forEach { entry ->
            historyArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("createdAtMs", entry.createdAtMs)
                    put("title", entry.title)
                    put("summaryJson", entry.summaryJson)
                    putNullableDouble("javaEncodeTotalMs", entry.javaEncodeTotalMs)
                    putNullableDouble("coreCppEncodeTotalMs", entry.coreCppEncodeTotalMs)
                    putNullableDouble("encodeOverheadMs", entry.encodeOverheadMs)
                    putNullableDouble("javaDecodeTotalMs", entry.javaDecodeTotalMs)
                    putNullableDouble("coreCppDecodeTotalMs", entry.coreCppDecodeTotalMs)
                    putNullableDouble("decodeOverheadMs", entry.decodeOverheadMs)
                }
            )
        }

        runCatching {
            benchmarkHistoryFile.parentFile?.mkdirs()
            benchmarkHistoryFile.writeText(historyArray.toString())
        }.getOrElse { error ->
            Log.e(VOICE_ASSISTANT_TAG, "Failed to write benchmark history", error)
            throw IllegalStateException("Failed to write benchmark history", error)
        }
    }
}

private fun createHistoryEntry(
    timestampMs: Long,
    title: String,
    summaryJson: String,
    overheads: BenchmarkOverheadSummary?
): BenchmarkHistoryEntry {
    return BenchmarkHistoryEntry(
        id = timestampMs,
        createdAtMs = timestampMs,
        title = title,
        summaryJson = summaryJson,
        javaEncodeTotalMs = overheads?.javaEncodeTotalMs,
        coreCppEncodeTotalMs = overheads?.coreCppEncodeTotalMs,
        encodeOverheadMs = overheads?.encodeOverheadMs,
        javaDecodeTotalMs = overheads?.javaDecodeTotalMs,
        coreCppDecodeTotalMs = overheads?.coreCppDecodeTotalMs,
        decodeOverheadMs = overheads?.decodeOverheadMs
    )
}

private fun JSONObject.toBenchmarkHistoryEntry(): BenchmarkHistoryEntry {
    return BenchmarkHistoryEntry(
        id = optLong("id"),
        createdAtMs = optLong("createdAtMs"),
        title = optString("title"),
        summaryJson = optString("summaryJson"),
        javaEncodeTotalMs = optNullableDouble("javaEncodeTotalMs"),
        coreCppEncodeTotalMs = optNullableDouble("coreCppEncodeTotalMs"),
        encodeOverheadMs = optNullableDouble("encodeOverheadMs"),
        javaDecodeTotalMs = optNullableDouble("javaDecodeTotalMs"),
        coreCppDecodeTotalMs = optNullableDouble("coreCppDecodeTotalMs"),
        decodeOverheadMs = optNullableDouble("decodeOverheadMs")
    )
}

/**
 * Writes a nullable double value to JSON, preserving nulls explicitly.
 */
private fun JSONObject.putNullableDouble(key: String, value: Double?) {
    if (value == null) {
        put(key, JSONObject.NULL)
    } else {
        put(key, value)
    }
}

/**
 * Reads a nullable double value from JSON.
 */
private fun JSONObject.optNullableDouble(key: String): Double? {
    return if (has(key) && !isNull(key)) {
        optDouble(key)
    } else {
        null
    }
}
