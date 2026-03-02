/*
 * SPDX-FileCopyrightText: Copyright 2025-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import android.content.Context
import android.os.PowerManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.arm.voiceassistant.utils.SystemStatusMonitor

/**
 * Creates and manages a [SystemStatusMonitor] instance scoped to this composable lifecycle.
 *
 * This helper remembers a single monitor instance for the given application context and polling interval,
 * starts monitoring when the composable enters the composition, and shuts it down when it leaves.
 *
 * @param context Android context used to create the monitor. Defaults to the current composition context.
 *                The application context is used internally to avoid leaking an Activity.
 * @param intervalSeconds Polling interval (in seconds) used by the monitor to refresh system metrics.
 *
 * @return A [SystemStatusMonitor] that exposes system metrics as reactive flows/state.
 */
@Composable
fun systemMetricsMonitor(
    context: Context = LocalContext.current,
    intervalSeconds: Long = 1L
): SystemStatusMonitor {
    val monitor = remember(context.applicationContext, intervalSeconds) {
        SystemStatusMonitor(context.applicationContext, intervalSeconds)
    }

    // Start/stop tied to the composable's lifecycle
    DisposableEffect(monitor) {
        monitor.start()
        onDispose { monitor.shutdown() }
    }

    return monitor
}

/**
 * Displays the current memory usage in gigabytes as a text label.
 *
 * @param textStyle Style applied to the rendered text
 * @param modifier Optional modifier applied to the Text (e.g. test tags)
 * @param intervalSeconds Polling interval (in seconds) used to refresh the memory usage metric
 */
@Composable
fun MemoryUsageText(
    textStyle: TextStyle = TextStyle(),
    modifier: Modifier = Modifier,
    intervalSeconds: Long = 3L
) {
    val context = LocalContext.current
    val monitor = systemMetricsMonitor(context, intervalSeconds)

    val memoryGb by monitor.memoryUsageGb.collectAsState()

    Text(
        text = "$memoryGb GB",
        style = textStyle,
        modifier = modifier
    )
}

/**
 * Displays the available system memory in gigabytes as a text label.
 *
 * @param textStyle Style applied to the rendered text
 * @param modifier Optional modifier applied to the Text (e.g. test tags)
 * @param intervalSeconds Polling interval (in seconds) used to refresh the available memory metric
 */
@Composable
fun MemoryAvailableText(
    textStyle: TextStyle = TextStyle(),
    modifier: Modifier,
    intervalSeconds: Long = 3L
) {
    val context = LocalContext.current
    val monitor = systemMetricsMonitor(context, intervalSeconds)

    val memoryGb by monitor.memoryAvailableGb.collectAsState()

    Text(
        text = "$memoryGb GB",
        style = textStyle,
        modifier = modifier
    )
}

/**
 * Displays the current device thermal status as a human-readable text label.
 *
 * @param textStyle Style applied to the rendered text
 * @param modifier Optional modifier applied to the Text (e.g. test tags)
 * @param intervalSeconds Polling interval (in seconds) used to refresh the thermal status metric
 */
@Composable
fun ThermalStatusText(
    textStyle: TextStyle = TextStyle(),
    modifier: Modifier = Modifier,
    intervalSeconds: Long = 3L
) {
    val context = LocalContext.current
    val monitor = systemMetricsMonitor(context, intervalSeconds)

    val thermalStatus by monitor.thermalStatus.collectAsState()
    val thermalStatusDescription = thermalStatusToText(thermalStatus)

    Text(
        text = thermalStatusDescription,
        style = textStyle,
        modifier = modifier
    )
}

/**
 * Displays the current battery level as a percentage label.
 *
 * @param textStyle Style applied to the rendered text
 * @param modifier Optional modifier applied to the Text (e.g. test tags)
 * @param intervalSeconds Polling interval (in seconds) used to refresh the battery metric
 */
@Composable
fun BatteryPercentageText(
    textStyle: TextStyle = TextStyle(),
    modifier: Modifier = Modifier,
    intervalSeconds: Long = 3L
) {
    val context = LocalContext.current
    val monitor = systemMetricsMonitor(context, intervalSeconds)

    val batteryPercentage by monitor.batteryPercentage.collectAsState()

    Text(
        text = "$batteryPercentage%",
        style = textStyle,
        modifier = modifier
    )
}

/**
 * Converts a [PowerManager] thermal status constant into a user-friendly description.
 *
 * @param status Thermal status value, typically one of [PowerManager.THERMAL_STATUS_NONE],
 *               [PowerManager.THERMAL_STATUS_LIGHT], [PowerManager.THERMAL_STATUS_MODERATE],
 *               [PowerManager.THERMAL_STATUS_SEVERE], [PowerManager.THERMAL_STATUS_CRITICAL],
 *               [PowerManager.THERMAL_STATUS_EMERGENCY], [PowerManager.THERMAL_STATUS_SHUTDOWN].
 *
 * @return A human-readable string describing the thermal state (or "Unknown" if not recognized).
 */
fun thermalStatusToText(status: Int): String = when (status) {
    PowerManager.THERMAL_STATUS_NONE ->
        "None (normal)"
    PowerManager.THERMAL_STATUS_LIGHT ->
        "Light (slight throttling)"
    PowerManager.THERMAL_STATUS_MODERATE ->
        "Moderate (performance may be reduced)"
    PowerManager.THERMAL_STATUS_SEVERE ->
        "Severe (significant throttling)"
    PowerManager.THERMAL_STATUS_CRITICAL ->
        "Critical (device very hot)"
    PowerManager.THERMAL_STATUS_EMERGENCY ->
        "Emergency (shutdown imminent)"
    PowerManager.THERMAL_STATUS_SHUTDOWN ->
        "Shutdown (shutting down)"
    else ->
        "Unknown"
}
