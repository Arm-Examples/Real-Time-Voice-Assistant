/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.huggingface

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * Test to ensure model download functionality works for predefined model.
 */
class HuggingFaceApiServiceTest {

    @Test
    @Ignore
    fun downloadModelFileTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = OkHttpClient()

        val repository = HuggingFaceApiService()
        val spec = ModelDownloadSpec(
            url = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF/resolve/main/mmproj-F16.gguf",
            fileName = "mmproj-F16.gguf",
            sha256 = "cd88edcf8d031894960bb0c9c5b9b7e1fea6ebee02b9f7ce925a00d12891f864"
        )
        val modelInfo = HuggingFaceModel(
            id = "unsloth",
            modelId = "Qwen3.5-4B-GGUF",
            filename = spec.fileName
        )
        val progressListener = object : ProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
                Log.i("tag", "downloaded $downloadedBytes / ${totalBytes ?: -1}")
            }
        }
        val modelsDir = File(context.filesDir, "models")
        val finalFile = File(modelsDir, spec.fileName)
        val partFile = File(modelsDir, "${spec.fileName}.part")
        if (finalFile.exists()) finalFile.delete()
        if (partFile.exists()) partFile.delete()

        val first = repository.downloadModelFile(
            context, client, modelInfo,
            modelsDir.absolutePath, spec, progressListener
        )
        Assert.assertTrue(first.exists())
        Assert.assertTrue(first.length() > 0)
    }
}