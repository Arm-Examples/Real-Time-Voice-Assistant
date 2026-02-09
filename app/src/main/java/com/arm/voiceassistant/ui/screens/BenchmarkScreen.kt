/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arm.voiceassistant.BuildConfig
import com.arm.voiceassistant.ui.composables.BaseDropdown
import com.arm.voiceassistant.ui.composables.ModelMetrics
import com.arm.voiceassistant.utils.ToastService
import com.arm.voiceassistant.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    modelOptionsOverride: List<String>? = null, // NEW: test override to avoid CI flakiness
) {
    val cpuCount = remember { Runtime.getRuntime().availableProcessors() }
    val inputSizes = listOf(64, 128, 256, 512)
    val outputSizes = listOf(64, 128, 256, 512)
    val contextSizes = listOf(256, 512, 1024, 2048, 4096)
    val iterationOptions = listOf(1, 3, 5, 10, 20)
    val warmupIteration = (0..5).toList()
    val threadOptions = remember { (1..cpuCount).toList() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Align with Chat/ModeSelection background styling
    val backgroundModifier = if (isSystemInDarkTheme()) {
        Modifier.background(color = MaterialTheme.colorScheme.primary)
    } else {
        Modifier.background(
            brush = Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.inversePrimary
                )
            )
        )
    }

    // Reusable layout styling for the repeated card sections
    val sectionCardModifier = Modifier.fillMaxWidth()
    val sectionCardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    )
    val sectionContentModifier = Modifier.padding(16.dp)
    val sectionSpacingTight = Arrangement.spacedBy(6.dp)
    val sectionSpacing = Arrangement.spacedBy(12.dp)
    val twoColumnRowModifier = Modifier.fillMaxWidth()
    val twoColumnRowSpacing = Arrangement.spacedBy(12.dp)

    val modelOptions = remember(modelOptionsOverride) {
        modelOptionsOverride ?: run {
            val baseDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                BuildConfig.LLM_FRAMEWORK
            )

            val entries = baseDir.listFiles()
                ?.filter { it.isDirectory || it.name.endsWith(".json") }
                ?: emptyList()

            if (entries.isEmpty()) listOf("No models found")
            else entries.map { it.name }.sorted()
        }
    }

    var selectedModel by remember(modelOptions) {
        mutableStateOf(modelOptions.firstOrNull() ?: "No models found")
    }

    var selectedInputSize by remember { mutableIntStateOf(128) }
    var selectedOutputSize by remember { mutableIntStateOf(128) }
    var selectedContextSize by remember { mutableIntStateOf(2048) }
    var selectedThreads by remember { mutableIntStateOf(minOf(4, cpuCount)) }
    var selectedIterations by remember { mutableIntStateOf(5) }
    var selectedWarmup by remember { mutableIntStateOf(1) }

    var isRunning by remember { mutableStateOf(false) }
    var lastRunSummary by remember { mutableStateOf<String?>(null) }
    var lastRunTitle by remember { mutableStateOf<String?>(null) }
    fun coerceContextSize(
        inputSize: Int,
        outputSize: Int,
        preferredContextSize: Int = selectedContextSize,
    ): Int {
        val minimumValidContextSize = inputSize + outputSize + 1
        return contextSizes.firstOrNull { it >= minimumValidContextSize && it >= preferredContextSize }
            ?: contextSizes.first { it >= minimumValidContextSize }
    }
    fun updateContextSizeIfNeeded(inputSize: Int, outputSize: Int) {
        val newContextSize = coerceContextSize(
            inputSize = inputSize,
            outputSize = outputSize
        )

        if (newContextSize != selectedContextSize) {
            selectedContextSize = newContextSize
            ToastService.showToast("Context size updated to minimum valid value ($newContextSize).")
        }
    }
    val isContextSizeOptionEnabled: (Int) -> Boolean = { contextSize ->
        contextSize > (selectedInputSize + selectedOutputSize)
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier),
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top padding requested
            Spacer(Modifier.height(8.dp))

            // Top status strip (icons only) - mirrors chat screen metrics
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    ModelMetrics()
                }
            }

            Spacer(Modifier.height(8.dp))

            // Intro
            ElevatedCard(
                modifier = sectionCardModifier,
                colors = sectionCardColors
            ) {
                Column(
                    modifier = sectionContentModifier,
                    verticalArrangement = sectionSpacingTight
                ) {
                    Text(
                        text = "Run performance tests for your local model.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Tip: Use warmup to stabilize first-run latency and keep threads ≤ CPU cores ($cpuCount).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model
            ElevatedCard(
                modifier = sectionCardModifier,
                colors = sectionCardColors
            ) {
                Column(
                    modifier = sectionContentModifier,
                    verticalArrangement = sectionSpacing
                ) {
                    Text("Model", style = MaterialTheme.typography.titleMedium)
                    BaseDropdown(
                        label = "Select model",
                        options = modelOptions,
                        selected = selectedModel,
                        onSelected = { selectedModel = it },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth(),
                        valueText = { it },
                        itemText = { it }
                    )
                }
            }

            // Settings
            ElevatedCard(
                modifier = sectionCardModifier,
                colors = sectionCardColors
            ) {
                Column(
                    modifier = sectionContentModifier,
                    verticalArrangement = sectionSpacing
                ) {
                    Text("Settings", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = twoColumnRowModifier,
                        horizontalArrangement = twoColumnRowSpacing
                    ) {
                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Input tokens",
                                options = inputSizes,
                                selected = selectedInputSize,
                                onSelected = {
                                    selectedInputSize = it
                                    updateContextSizeIfNeeded(
                                        inputSize = it,
                                        outputSize = selectedOutputSize
                                    )
                                },
                                enabled = !isRunning,
                                fieldTag = "benchmark_input_dropdown",
                                optionTag = { "benchmark_input_option_$it" }
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Output tokens",
                                options = outputSizes,
                                selected = selectedOutputSize,
                                onSelected = {
                                    selectedOutputSize = it
                                    updateContextSizeIfNeeded(
                                        inputSize = selectedInputSize,
                                        outputSize = it
                                    )
                                },
                                enabled = !isRunning,
                                fieldTag = "benchmark_output_dropdown",
                                optionTag = { "benchmark_output_option_$it" }
                            )
                        }
                    }

                    Row(
                        modifier = twoColumnRowModifier,
                        horizontalArrangement = twoColumnRowSpacing
                    ) {
                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Context size",
                                options = contextSizes,
                                selected = selectedContextSize,
                                onSelected = { selectedContextSize = it },
                                enabled = !isRunning,
                                fieldTag = "benchmark_context_dropdown",
                                optionEnabled = isContextSizeOptionEnabled,
                                optionTag = { "benchmark_context_option_$it" }
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Threads",
                                options = threadOptions,
                                selected = selectedThreads,
                                onSelected = { selectedThreads = it },
                                enabled = !isRunning
                            )
                        }
                    }

                    Row(
                        modifier = twoColumnRowModifier,
                        horizontalArrangement = twoColumnRowSpacing
                    ) {
                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Iterations",
                                options = iterationOptions,
                                selected = selectedIterations,
                                onSelected = { selectedIterations = it },
                                enabled = !isRunning
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            BaseDropdown(
                                label = "Warmup",
                                options = warmupIteration,
                                selected = selectedWarmup,
                                onSelected = { selectedWarmup = it },
                                enabled = !isRunning
                            )
                        }
                    }
                }
            }

            // Run area
            ElevatedCard(
                modifier = sectionCardModifier,
                colors = sectionCardColors
            ) {
                Column(
                    modifier = sectionContentModifier,
                    verticalArrangement = sectionSpacing
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Run",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("benchmark_run_header")
                            )
                            Text(
                                text = if (isRunning) "Benchmark in progress…" else "Ready to run.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            modifier = Modifier.testTag("benchmark_run_button"),
                            enabled = !isRunning && selectedModel != "No models found",
                            onClick = {
                                scope.launch {
                                    isRunning = true
                                    lastRunSummary = null
                                    lastRunTitle =
                                        "Model: $selectedModel • In:$selectedInputSize Out:$selectedOutputSize Ctx:$selectedContextSize • Threads:$selectedThreads • Iter:$selectedIterations • Warm:$selectedWarmup"

                                    try {
                                        val summary = withContext(Dispatchers.IO) {
                                            val code = viewModel.runBenchmark(
                                                selectedModel,
                                                selectedInputSize,
                                                selectedOutputSize,
                                                selectedContextSize,
                                                selectedThreads,
                                                selectedIterations,
                                                selectedWarmup
                                            )
                                            val results = viewModel.getBenchmarkResults()
                                            if (code == 0) results
                                            else "Benchmark failed (code=$code)\n$results"
                                        }
                                        lastRunSummary = summary
                                    } catch (t: Throwable) {
                                        lastRunSummary =
                                            "Benchmark threw: ${t.message ?: (t::class.simpleName ?: "Unknown error")}"
                                    } finally {
                                        isRunning = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunning) "Running" else "Run")
                        }
                    }

                    AnimatedVisibility(visible = isRunning) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                text = "This may take a while depending on model size.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Results
            AnimatedVisibility(visible = lastRunSummary != null) {
                ElevatedCard(
                    modifier = sectionCardModifier,
                    colors = sectionCardColors
                ) {
                    Column(
                        modifier = sectionContentModifier,
                        verticalArrangement = sectionSpacing
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Results", style = MaterialTheme.typography.titleMedium)
                                lastRunTitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    lastRunSummary?.let { clipboard.setText(AnnotatedString(it)) }
                                },
                                enabled = lastRunSummary != null
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy results")
                            }
                        }

                        val hScroll = rememberScrollState()
                        Surface(
                            tonalElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastRunSummary.orEmpty(),
                                fontFamily = FontFamily.Monospace,
                                softWrap = false,
                                overflow = TextOverflow.Visible,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .horizontalScroll(hScroll)
                                    .alpha(if (lastRunSummary == null) 0f else 1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
