/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.huggingface

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class HuggingFaceApiServiceTest {
    private val revision = "c".repeat(40)

    @Test
    fun matchingSha256Succeeds() {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("matching-sha")
        try {
            val file = File(dir, "model.bin")
            val bytes = "model contents".toByteArray()
            file.writeBytes(bytes)

            assertTrue(repository.verifyIfNeeded(file, sha256(bytes)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun mismatchingSha256Fails() {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("mismatching-sha")
        try {
            val file = File(dir, "model.bin")
            file.writeBytes("model contents".toByteArray())

            assertFalse(repository.verifyIfNeeded(file, "0".repeat(64)))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun invalidDownloadedFileIsNotRetainedAsValidCachedModel() = runBlocking {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("invalid-download")
        val expectedBytes = "expected contents".toByteArray()
        val downloadedBytes = "corrupt contents".toByteArray()
        val interceptor = FixtureInterceptor(downloadedBytes)
        try {
            val spec = testSpec("model.bin", sha256(expectedBytes))
            val result = repository.downloadModelFile(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                modelInfo = testModel("model.bin"),
                downloadPath = dir.absolutePath,
                spec = spec
            )

            assertTrue(result.isFailure)
            assertFalse(File(dir, "model.bin").exists())
            assertFalse(File(dir, "model.bin.part").exists())
            assertEquals(1, interceptor.requestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun existingCachedFileWithCorrectSha256IsReusedWithoutDownload() = runBlocking {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("reuse-cache")
        val cachedBytes = "cached contents".toByteArray()
        val interceptor = FixtureInterceptor("network contents".toByteArray())
        try {
            File(dir, "model.bin").writeBytes(cachedBytes)
            val result = repository.downloadModelFile(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                modelInfo = testModel("model.bin"),
                downloadPath = dir.absolutePath,
                spec = testSpec("model.bin", sha256(cachedBytes))
            )

            assertTrue(result.isSuccess)
            assertArrayEquals(cachedBytes, result.getOrThrow().readBytes())
            assertEquals(0, interceptor.requestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun existingCachedFileWithIncorrectSha256IsRedownloaded() = runBlocking {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("redownload-cache")
        val cachedBytes = "stale contents".toByteArray()
        val downloadedBytes = "fresh contents".toByteArray()
        val interceptor = FixtureInterceptor(downloadedBytes)
        try {
            File(dir, "model.bin").writeBytes(cachedBytes)
            val result = repository.downloadModelFile(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                modelInfo = testModel("model.bin"),
                downloadPath = dir.absolutePath,
                spec = testSpec("model.bin", sha256(downloadedBytes))
            )

            assertTrue(result.isSuccess)
            assertArrayEquals(downloadedBytes, result.getOrThrow().readBytes())
            assertEquals(1, interceptor.requestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingSha256CanBeResolvedFromHuggingFaceMetadata() = runBlocking {
        val repository = HuggingFaceApiService()
        val expectedSha256 = "d".repeat(64)
        val interceptor = LambdaInterceptor { chain ->
            val request = chain.request()
            assertTrue(request.url.encodedPath.endsWith("/api/models/owner/repo/paths-info/$revision"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    [{"type":"file","path":"nested/model.bin","lfs":{"oid":"$expectedSha256","size":14}}]
                    """.trimIndent().toResponseBody("application/json".toMediaType())
                )
                .build()
        }

        val resolved = repository.resolveSha256IfNeeded(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            spec = testSpecWithoutSha256(
                fileName = "model.bin",
                sourcePath = "nested/model.bin"
            )
        )

        assertTrue(resolved.isSuccess)
        assertEquals(expectedSha256, resolved.getOrThrow().sha256)
    }

    @Test
    fun blankBearerTokenIsOmittedFromMetadataRequest() = runBlocking {
        val repository = HuggingFaceApiService()
        val expectedSha256 = "d".repeat(64)
        val interceptor = LambdaInterceptor { chain ->
            val request = chain.request()
            assertNull(request.header("Authorization"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    """
                    [{"type":"file","path":"model.bin","lfs":{"oid":"$expectedSha256","size":14}}]
                    """.trimIndent().toResponseBody("application/json".toMediaType())
                )
                .build()
        }

        val resolved = repository.resolveSha256IfNeeded(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            spec = testSpecWithoutSha256("model.bin").copy(bearerToken = "   ")
        )

        assertTrue(resolved.isSuccess)
        assertEquals(expectedSha256, resolved.getOrThrow().sha256)
    }

    @Test
    fun missingSha256FailsWhenHuggingFaceMetadataHasNoSha256() = runBlocking {
        val repository = HuggingFaceApiService()
        val interceptor = FixtureInterceptor(
            """
            [{"type":"file","path":"model.bin","oid":"1234567890123456789012345678901234567890"}]
            """.trimIndent().toByteArray()
        )

        val resolved = repository.resolveSha256IfNeeded(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            spec = testSpecWithoutSha256("model.bin")
        )

        assertTrue(resolved.isFailure)
        assertEquals(
            "Hugging Face did not provide a SHA-256 checksum for model.bin",
            resolved.exceptionOrNull()?.message
        )
    }

    @Test
    fun downloadedFileCanUseSha256ResolvedFromHuggingFaceMetadata() = runBlocking {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("metadata-sha-download")
        val downloadedBytes = "fresh contents".toByteArray()
        val expectedSha256 = sha256(downloadedBytes)
        val interceptor = LambdaInterceptor { chain ->
            val request = chain.request()
            val responseBytes = if (request.url.encodedPath.contains("/paths-info/")) {
                """
                [{"type":"file","path":"model.bin","lfs":{"oid":"$expectedSha256","size":${downloadedBytes.size}}}]
                """.trimIndent().toByteArray()
            } else {
                downloadedBytes
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBytes.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        try {
            val resolvedSpec = repository.resolveSha256IfNeeded(
                client = client,
                spec = testSpecWithoutSha256("model.bin")
            ).getOrThrow()
            val result = repository.downloadModelFile(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                client = client,
                modelInfo = testModel("model.bin"),
                downloadPath = dir.absolutePath,
                spec = resolvedSpec
            )

            assertTrue(result.isSuccess)
            assertArrayEquals(downloadedBytes, result.getOrThrow().readBytes())
            assertEquals(2, interceptor.requestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankBearerTokenIsOmittedFromDownloadRequest() = runBlocking {
        val repository = HuggingFaceApiService()
        val dir = createTempModelDir("blank-token-download")
        val downloadedBytes = "fresh contents".toByteArray()
        val interceptor = LambdaInterceptor { chain ->
            val request = chain.request()
            assertNull(request.header("Authorization"))
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(downloadedBytes.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
        try {
            val result = repository.downloadModelFile(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
                modelInfo = testModel("model.bin"),
                downloadPath = dir.absolutePath,
                spec = testSpec("model.bin", sha256(downloadedBytes)).copy(bearerToken = "   ")
            )

            assertTrue(result.isSuccess)
            assertArrayEquals(downloadedBytes, result.getOrThrow().readBytes())
            assertEquals(1, interceptor.requestCount)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun testModel(fileName: String) = HuggingFaceModel(
        id = "owner",
        modelId = "repo",
        filename = fileName,
        revision = revision
    )

    private fun testSpec(fileName: String, expectedSha256: String) = ModelDownloadSpec(
        url = "https://huggingface.co/owner/repo/resolve/$revision/$fileName",
        fileName = fileName,
        destination = fileName,
        sha256 = expectedSha256,
        huggingFaceSource = HuggingFaceSource(
            repository = "owner/repo",
            revision = revision,
            path = fileName
        )
    )

    private fun testSpecWithoutSha256(
        fileName: String,
        sourcePath: String = fileName
    ) = ModelDownloadSpec(
        url = "https://huggingface.co/owner/repo/resolve/$revision/$sourcePath",
        fileName = fileName,
        destination = fileName,
        huggingFaceSource = HuggingFaceSource(
            repository = "owner/repo",
            revision = revision,
            path = sourcePath
        )
    )

    private fun createTempModelDir(name: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "hf-$name-${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private class FixtureInterceptor(
        private val responseBytes: ByteArray
    ) : Interceptor {
        var requestCount = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            requestCount += 1
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBytes.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }
    }

    private class LambdaInterceptor(
        private val responder: (Interceptor.Chain) -> Response
    ) : Interceptor {
        var requestCount = 0
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            requestCount += 1
            return responder(chain)
        }
    }
}
