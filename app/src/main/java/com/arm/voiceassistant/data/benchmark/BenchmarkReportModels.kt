/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.data.benchmark

import java.util.Locale
import org.json.JSONObject

data class BenchmarkUiReport(
    val modelSize: String,
    val framework: String,
    val threads: String,
    val iterations: String,
    val warmup: String,
    val inputTokens: String,
    val outputTokens: String,
    val contextSize: String,
    val encodePerf: String,
    val decodePerf: String,
    val ttft: String,
    val total: String,
)

data class BenchmarkOverheadUiReport(
    val javaEncodeTotal: String,
    val coreCppEncodeTotal: String,
    val encodeOverhead: String,
    val javaDecodeTotal: String,
    val coreCppDecodeTotal: String,
    val decodeOverhead: String,
)

/**
 * Parses the benchmark JSON summary into a UI-ready report.
 */
fun parseBenchmarkReport(jsonString: String): BenchmarkUiReport? = runCatching {
    JSONObject(jsonString).toBenchmarkUiReport()
}.getOrNull()

/**
 * Converts raw benchmark overhead values into formatted UI strings.
 */
fun BenchmarkOverheadSummary.toUiReport(measuredIterations: Int): BenchmarkOverheadUiReport {
    val divisor = measuredIterations.coerceAtLeast(1).toDouble()
    return BenchmarkOverheadUiReport(
        javaEncodeTotal = formatDurationMs(javaEncodeTotalMs / divisor),
        coreCppEncodeTotal = formatDurationMs(coreCppEncodeTotalMs / divisor),
        encodeOverhead = formatDurationMs(encodeOverheadMs / divisor),
        javaDecodeTotal = formatDurationMs(javaDecodeTotalMs / divisor),
        coreCppDecodeTotal = formatDurationMs(coreCppDecodeTotalMs / divisor),
        decodeOverhead = formatDurationMs(decodeOverheadMs / divisor),
    )
}

/**
 * Formats a benchmark metric with mean and standard deviation.
 */
private fun formatBenchmarkMetric(mean: Double, stddev: Double): String =
    String.format(Locale.US, "%.3f ± %.3f", mean, stddev)

/**
 * Formats a duration value in milliseconds.
 */
private fun formatDurationMs(value: Double): String =
    String.format(Locale.US, "%.3f", value)

private fun JSONObject.toBenchmarkUiReport(): BenchmarkUiReport {
    val parameters = getJSONObject("parameters")
    val results = getJSONObject("results")
    val mean = results.getJSONObject("mean")
    val stddev = results.getJSONObject("stddev")

    return BenchmarkUiReport(
        modelSize = parameters.optString("model_size", "Unknown"),
        framework = optString("framework", "Unknown"),
        threads = parameters.opt("num_threads")?.toString() ?: "Unknown",
        iterations = parameters.opt("num_iterations")?.toString() ?: "Unknown",
        warmup = parameters.opt("num_warmup")?.toString() ?: "Unknown",
        inputTokens = parameters.opt("num_input_tokens")?.toString() ?: "Unknown",
        outputTokens = parameters.opt("num_output_tokens")?.toString() ?: "Unknown",
        contextSize = parameters.opt("context_size")?.toString() ?: "Unknown",
        encodePerf = formatBenchmarkMetric(
            mean.optDouble("encode_tokens_per_sec", 0.0),
            stddev.optDouble("encode_tokens_per_sec", 0.0)
        ),
        decodePerf = formatBenchmarkMetric(
            mean.optDouble("decode_tokens_per_sec", 0.0),
            stddev.optDouble("decode_tokens_per_sec", 0.0)
        ),
        ttft = formatBenchmarkMetric(
            mean.optDouble("ttft_ms", 0.0),
            stddev.optDouble("ttft_ms", 0.0)
        ),
        total = formatBenchmarkMetric(
            mean.optDouble("total_ms", 0.0),
            stddev.optDouble("total_ms", 0.0)
        ),
    )
}
