/*
 * SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.ui.composables

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arm.voiceassistant.ui.theme.VoiceAssistantTheme
import androidx.core.net.toUri

/**
 * Displays a user-uploaded image as a chat bubble aligned to the right.
 * @param uri The Uri of the image file to display.
 */
@Composable
fun UserImageBubble(uri: Uri) {
    Column(
        modifier = Modifier.fillMaxWidth(), // Ensures full width so align End works
        horizontalAlignment = Alignment.End
    ) {
        //  Image card aligned to the right
        Card(
            modifier = Modifier
                .padding(end = 8.dp)
                .width(260.dp), // Fixed width
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {

                Spacer(modifier = Modifier.height(8.dp))

                val imagePath = uri.path ?: ""
                val bitmap = remember(imagePath) {
                    BitmapFactory.decodeFile(imagePath)
                }

                bitmap?.let { bmp ->
                    Image(
                        painter = BitmapPainter(bmp.asImageBitmap()),
                        contentDescription = "User uploaded image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .height(240.dp) // Or .size(240.dp) for square
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }
        }
    }
}

/**
 * User image bubble preview without rendering image
 */
@Preview(showBackground = true)
@Composable
fun UserImageBubblePreview() {
    // This is a fake Uri; preview won't render the image but will test layout
    val placeholderUri = "file:///path/to/test_image.jpg".toUri()

    VoiceAssistantTheme {
        UserImageBubble(uri = placeholderUri)
    }
}
