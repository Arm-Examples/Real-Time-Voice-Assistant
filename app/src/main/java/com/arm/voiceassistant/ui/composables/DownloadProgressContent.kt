/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.utils.DownloadUiState
import java.util.Locale

private fun formatDownloadBytes(bytes: Long): String {
    val mib = bytes / (1024.0 * 1024.0)
    return when {
        mib < 1.0 -> String.format(Locale.US, "%.1f KiB", bytes / 1024.0)
        mib >= 1024.0 -> String.format(Locale.US, "%.2f GB", mib / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", mib)
    }
}

private fun formatDownloadSpeed(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) {
        return "calculating"
    }
    return "${formatDownloadBytes(bytesPerSecond)}/s"
}

private fun formatDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val secs = safeSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

private fun downloadFileLabel(ui: DownloadUiState): String {
    val fileName = ui.currentFile ?: "models"
    val filePosition = if (ui.currentFileIndex > 0 && ui.fileCount > 0) {
        "File ${ui.currentFileIndex} of ${ui.fileCount}: "
    } else {
        ""
    }
    return "$filePosition$fileName"
}

private fun downloadBytesLabel(ui: DownloadUiState): String? {
    return when {
        ui.totalBytes != null && ui.totalBytes > 0L ->
            "${formatDownloadBytes(ui.doneBytes)} of ${formatDownloadBytes(ui.totalBytes)}"

        ui.doneBytes > 0L -> formatDownloadBytes(ui.doneBytes)
        else -> null
    }
}

private fun downloadButtonLabel(
    ui: DownloadUiState,
    idleText: String,
    retryText: String
): String {
    val fileProgress = ui.fileProgress
    return when {
        ui.isRunning && fileProgress != null -> "Downloading $fileProgress%"
        ui.isRunning -> "Downloading"
        !ui.errorMessage.isNullOrBlank() -> retryText
        else -> idleText
    }
}

/**
 * Button content that keeps download progress visible on the action itself.
 */
@Composable
fun DownloadProgressButtonContent(
    ui: DownloadUiState,
    idleText: String,
    modifier: Modifier = Modifier,
    retryText: String = idleText,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ui.isRunning) {
            val fileProgress = ui.fileProgress
            if (fileProgress == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                CircularProgressIndicator(
                    progress = { fileProgress / 100f },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        } else {
            leadingIcon?.invoke()
        }
        Text(downloadButtonLabel(ui, idleText, retryText))
    }
}

/**
 * Shared model download status UI for the startup dialog and download screen.
 */
@Composable
fun DownloadProgressContent(
    ui: DownloadUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (ui.isRunning) {
            val fileProgress = ui.fileProgress
            Text(
                text = "Downloading ${downloadFileLabel(ui)}",
                style = MaterialTheme.typography.bodyMedium
            )
            downloadBytesLabel(ui)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            if (fileProgress == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            } else {
                LinearProgressIndicator(
                    progress = { fileProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Text(text = "$fileProgress%", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Speed: ${formatDownloadSpeed(ui.bytesPerSecond)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Elapsed: ${formatDuration(ui.elapsedSeconds)}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Time remaining: ${ui.estimatedSecondsRemaining?.let(::formatDuration) ?: "calculating"}",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (!ui.errorMessage.isNullOrBlank()) {
            Text(
                text = "Download failed: ${ui.errorMessage}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (ui.finishedOk) {
            Text(
                text = "Download complete.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
