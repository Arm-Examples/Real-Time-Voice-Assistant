/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.viewmodels.MainViewModel

/**
 * Download screen for the voice assistant.
 *
 * Displays a simple download UI currently used to test models download
 *
 * @param viewModel [MainViewModel] providing chat state and actions
 * @param modifier Optional modifier for layout customization
 */
@Composable
fun DownloadScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val ui by viewModel.downloadUiState.collectAsState()
    val modelListUi by viewModel.modelListUiState.collectAsState()
    val modelDetailsUi by viewModel.modelDetailsUiState.collectAsState()
    val context = LocalContext.current
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
               ) { uri ->
           if (uri != null) {
                   context.contentResolver.takePersistableUriPermission(
                           uri,
                           Intent.FLAG_GRANT_READ_URI_PERMISSION
                               )
                   viewModel.importModel(uri)
               }
       }

    LaunchedEffect(Unit) {
        viewModel.loadHuggingFaceModels()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Download model",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                enabled = !ui.isRunning
            ) {
                Text("Import model")
            }
        }

        if (ui.finishedOk) {
            Text(text = "Download finished.")
        }

        Text(
            text = "Top downloads models",
            style = MaterialTheme.typography.titleMedium
        )

        if (modelListUi.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (modelListUi.error != null) {
            Text(
                text = modelListUi.error ?: "Failed to load models.",
                color = MaterialTheme.colorScheme.error
            )
        }

        if (modelListUi.models.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(modelListUi.models) { model ->
                    val modelKey = "${model.id}/${model.modelId}"
                    val isCurrentModel = ui.isRunning && ui.currentModelKey == modelKey
                    Button(
                        onClick = { viewModel.selectModelForDetails(model) },
                        colors = if (isCurrentModel) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = modelKey)
                    }
                }
            }
        }

        modelDetailsUi.selectedModel?.let { model ->
            AlertDialog(
                onDismissRequest = { viewModel.clearSelectedModel() },
                title = { Text(text = model.modelId) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(text = "Owner: ${model.id}")
                        val expectedExtensions = viewModel.getExpectedModelExtensions()
                        val filteredFiles = if (expectedExtensions.isEmpty()) {
                            modelDetailsUi.files
                        } else {
                            modelDetailsUi.files.filter { filename ->
                                expectedExtensions.any { expected ->
                                    filename.endsWith(expected, ignoreCase = true)
                                }
                            }
                        }
                        Text(text = "Files:")
                        when {
                            modelDetailsUi.isLoading -> Text(text = "Loading files...")
                            modelDetailsUi.error != null -> Text(
                                text = modelDetailsUi.error ?: "Failed to load files.",
                                color = MaterialTheme.colorScheme.error
                            )
                            filteredFiles.isEmpty() -> Text(text = "No model files found.")
                            else -> {
                                filteredFiles.forEach { filename ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isDownloaded = viewModel.isModelFileDownloaded(model, filename)
                                        Text(
                                            text = filename,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        val isCurrentFile =
                                            ui.isRunning && ui.currentFile == filename
                                        Column(horizontalAlignment = Alignment.End) {
                                            OutlinedButton(
                                                onClick = {
                                                    if (isCurrentFile) {
                                                        viewModel.cancelModelDownload()
                                                    } else if (isDownloaded) {
                                                        viewModel.deleteModelFile(model, filename)
                                                    } else {
                                                        viewModel.downloadModelFile(model, filename)
                                                    }
                                                },
                                                enabled = if (isCurrentFile) {
                                                    ui.canCancel
                                                } else {
                                                    !ui.isRunning && ui.canStart
                                                }
                                            ) {
                                                Text(
                                                    when {
                                                        isCurrentFile -> "Cancel"
                                                        isDownloaded -> "Delete"
                                                        else -> "Download"
                                                    }
                                                )
                                            }
                                            if (isCurrentFile) {
                                                val progressText = when {
                                                    ui.fileProgress in 0..100 -> "${ui.fileProgress}%"
                                                    else -> "Downloading..."
                                                }
                                                Text(
                                                    text = progressText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!isCurrentFile && isDownloaded) {
                                                Text(
                                                    text = "Saved",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSelectedModel() }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
