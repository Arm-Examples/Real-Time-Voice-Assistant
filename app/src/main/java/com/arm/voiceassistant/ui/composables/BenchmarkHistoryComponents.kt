/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.data.benchmark.BenchmarkHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Formats a saved benchmark timestamp for display.
 */
internal fun formatBenchmarkTimestamp(timestampMs: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}

/**
 * Renders the list of saved benchmark results.
 */
@Composable
internal fun BenchmarkHistorySection(
    entries: List<BenchmarkHistoryEntry>,
    onOpenEntry: (BenchmarkHistoryEntry) -> Unit,
    onDeleteEntry: (BenchmarkHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var deletingEntryIds by remember { mutableStateOf(setOf<Long>()) }
    var openEntryId by remember { mutableStateOf<Long?>(null) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Results", style = MaterialTheme.typography.titleMedium)
        entries.forEach { entry ->
            key(entry.id) {
                AnimatedVisibility(
                    visible = entry.id !in deletingEntryIds,
                    exit = slideOutHorizontally(
                        animationSpec = tween(durationMillis = 220),
                        targetOffsetX = { -it }
                    ) + shrinkVertically(
                        animationSpec = tween(durationMillis = 220)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 180)
                    )
                ) {
                    SwipeRevealHistoryItem(
                        entry = entry,
                        isOpen = openEntryId == entry.id,
                        onOpenChange = { isOpen ->
                            openEntryId = if (isOpen) entry.id else null
                        },
                        onOpenEntry = {
                            openEntryId = null
                            onOpenEntry(entry)
                        },
                        onDeleteEntry = {
                            openEntryId = null
                            deletingEntryIds = deletingEntryIds + entry.id
                            scope.launch {
                                kotlinx.coroutines.delay(220)
                                onDeleteEntry(entry)
                                deletingEntryIds = deletingEntryIds - entry.id
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays a saved benchmark row with swipe-to-reveal delete behaviour.
 */
@Composable
internal fun SwipeRevealHistoryItem(
    entry: BenchmarkHistoryEntry,
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onOpenEntry: () -> Unit,
    onDeleteEntry: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val actionWidth = 64.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember(entry.id) { Animatable(0f) }
    val revealProgress = (abs(offsetX.value) / actionWidthPx).coerceIn(0f, 1f)
    val removeTranslationX = (1f - revealProgress) * with(density) { 16.dp.toPx() }

    LaunchedEffect(isOpen) {
        val target = if (isOpen) -actionWidthPx else 0f
        offsetX.animateTo(target, animationSpec = tween(durationMillis = 180))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .alpha(revealProgress)
                .graphicsLayer {
                    translationX = removeTranslationX
                }
                .clickable {
                    scope.launch {
                        offsetX.animateTo(-actionWidthPx, animationSpec = tween(durationMillis = 120))
                        onDeleteEntry()
                    }
                }
                .testTag("benchmark_delete_saved_run_${entry.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(entry.id, actionWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val nextOffset = (offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                            scope.launch {
                                offsetX.snapTo(nextOffset)
                            }
                        },
                        onDragEnd = {
                            val shouldOpen = offsetX.value <= (-actionWidthPx * 0.4f)
                            onOpenChange(shouldOpen)
                        },
                        onDragCancel = {
                            onOpenChange(isOpen)
                        }
                    )
                }
                .testTag("benchmark_history_item_${entry.id}"),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatBenchmarkTimestamp(entry.createdAtMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = onOpenEntry,
                    modifier = Modifier.testTag("benchmark_open_saved_run")
                ) {
                    Text("Open saved run")
                }
            }
        }
    }
}
