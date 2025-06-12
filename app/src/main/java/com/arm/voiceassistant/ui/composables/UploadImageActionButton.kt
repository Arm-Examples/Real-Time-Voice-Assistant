/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

/**
 * Displays an image upload button with file picker support.
 * @param onAddImage Callback triggered with the selected image URI
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun UploadImageActionButton(
    onAddImage: (Uri) -> Unit = {}
) {
    // A surface container using the 'background' color from the theme
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showImageDialog by remember { mutableStateOf(false) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showImageDialog = true
        }
    }

    if (showImageDialog && selectedImageUri != null) {
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            modifier = Modifier.semantics { contentDescription= "imagePickerDialog" },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImageDialog = false
                        onAddImage(selectedImageUri!!)
                    }
                ) {
                    Text(
                        text = "Add Image",
                        color = Color.White,
                        fontWeight = FontWeight.Bold)
                }
            },
            text = {
                GlideImage(
                    model = selectedImageUri,
                    contentDescription = "selected_image",
                    modifier = Modifier
                        .height(300.dp)
                        .fillMaxWidth()
                )
            }
        )
    }

    // MIME-type filter wildcard matching any image subtype (e.g. jpeg, png, gif)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(35)) // Match ActionButton shape
            .background(MaterialTheme.colorScheme.secondary) // Match background
    ) {
        IconButton(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "upload_image_button" },
            onClick = { imagePickerLauncher.launch("image/*") }
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "upload_image",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Upload image action button preview
 */
@Preview(showBackground = true)
@Composable
private fun UploadImageActionButtonPreview() {
    VoiceAssistantTheme {
        UploadImageActionButton()
    }
}
