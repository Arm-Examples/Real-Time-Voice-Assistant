/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.data.benchmark.BenchmarkHistoryEntry
import com.arm.voiceassistant.data.benchmark.BenchmarkOverheadUiReport
import com.arm.voiceassistant.data.benchmark.BenchmarkUiReport
import com.arm.voiceassistant.data.benchmark.parseBenchmarkReport
import com.arm.voiceassistant.data.benchmark.toUiReport

private data class BenchmarkMetric(
    val label: String,
    val value: String,
)

/**
 * Renders a single benchmark metric tile.
 */
@Composable
private fun BenchmarkMetricTile(
    metric: BenchmarkMetric,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = metric.value,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun BenchmarkMetricRow(
    metrics: List<BenchmarkMetric>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.forEach { metric ->
            BenchmarkMetricTile(
                metric = metric,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Renders the formatted benchmark result summary.
 */
@Composable
internal fun BenchmarkResultsSection(
    report: BenchmarkUiReport,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Run profile", style = MaterialTheme.typography.titleSmall)
                BenchmarkMetricRow(
                    metrics = listOf(
                        BenchmarkMetric("Model size (GB)", report.modelSize.removeSuffix(" GB")),
                        BenchmarkMetric("Framework", report.framework)
                    )
                )
                BenchmarkMetricRow(
                    metrics = listOf(
                        BenchmarkMetric("Threads", report.threads),
                        BenchmarkMetric("Iterations", report.iterations)
                    )
                )
                BenchmarkMetricRow(
                    metrics = listOf(
                        BenchmarkMetric("Warmup", report.warmup),
                        BenchmarkMetric("Context (tokens)", report.contextSize)
                    )
                )
                BenchmarkMetricRow(
                    metrics = listOf(
                        BenchmarkMetric("Input (tokens)", report.inputTokens),
                        BenchmarkMetric("Output (tokens)", report.outputTokens)
                    )
                )
            }
        }

        Text("Performance", style = MaterialTheme.typography.titleSmall)

        BenchmarkMetricRow(
            metrics = listOf(
                BenchmarkMetric("Encode (tp/s)", report.encodePerf),
                BenchmarkMetric("Decode (tp/s)", report.decodePerf)
            )
        )

        BenchmarkMetricRow(
            metrics = listOf(
                BenchmarkMetric("TTFT (ms)", report.ttft),
                BenchmarkMetric("Total (ms)", report.total)
            )
        )
    }
}

/**
 * Renders JNI overhead metrics for a benchmark run.
 */
@Composable
internal fun BenchmarkOverheadsSection(
    report: BenchmarkOverheadUiReport,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("JNI overheads", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Average per measured iteration. Warmup runs are excluded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BenchmarkMetricRow(
                metrics = listOf(
                    BenchmarkMetric("Avg Java encode (ms)", report.javaEncodeTotal),
                    BenchmarkMetric("Avg Core C++ encode (ms)", report.coreCppEncodeTotal)
                )
            )
            BenchmarkMetricTile(
                metric = BenchmarkMetric("Avg encode overhead (ms)", report.encodeOverhead),
                modifier = Modifier.fillMaxWidth()
            )
            BenchmarkMetricRow(
                metrics = listOf(
                    BenchmarkMetric("Avg Java decode (ms)", report.javaDecodeTotal),
                    BenchmarkMetric("Avg Core C++ decode (ms)", report.coreCppDecodeTotal)
                )
            )
            BenchmarkMetricTile(
                metric = BenchmarkMetric("Avg decode overhead (ms)", report.decodeOverhead),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Displays the saved benchmark result bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BenchmarkSavedResultSheet(
    entry: BenchmarkHistoryEntry,
    clipboard: ClipboardManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entryReport = parseBenchmarkReport(entry.summaryJson)
    val entryOverheads = entryReport?.let { report ->
        entry.toOverheadSummary()?.toUiReport(report.iterations.toIntOrNull() ?: 1)
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Benchmark result",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("benchmark_saved_result_sheet")
                    )
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatBenchmarkTimestamp(entry.createdAtMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(entry.summaryJson))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy saved result")
                }
            }

            if (entryReport != null) {
                BenchmarkResultsSection(
                    report = entryReport,
                    modifier = Modifier.fillMaxWidth()
                )
                entryOverheads?.let { overheadReport ->
                    BenchmarkOverheadsSection(
                        report = overheadReport,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                val hScroll = rememberScrollState()
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.summaryJson,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(12.dp)
                            .horizontalScroll(hScroll)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
