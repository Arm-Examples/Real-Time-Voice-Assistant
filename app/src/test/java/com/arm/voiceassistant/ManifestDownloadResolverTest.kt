/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestDownloadResolverTest {
    private val revisionA = "a".repeat(40)
    private val revisionB = "b".repeat(40)

    @Test
    fun urlGenerationUsesConfiguredCommitSha() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "test model",
                    framework = "llama.cpp",
                    destination = "llama.cpp/test",
                    repoId = "owner/repo",
                    revision = revisionA,
                    artifacts = listOf(
                        RemoteArtifactSpec(
                            path = "nested/model.bin",
                            destination = "model.bin"
                        )
                    )
                )
            )
        )

        val specs = resolveManifestDownloadSpecs(manifest, "llama.cpp").getOrThrow()

        assertEquals(
            "https://huggingface.co/owner/repo/resolve/$revisionA/nested/model.bin",
            specs.single().url
        )
    }

    @Test
    fun mainIsNotSubstitutedForConfiguredRevision() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "test model",
                    framework = "llama.cpp",
                    destination = "llama.cpp/test",
                    repoId = "owner/repo",
                    revision = revisionA,
                    artifacts = listOf(
                        RemoteArtifactSpec(
                            path = "model.bin",
                            url = "https://huggingface.co/owner/repo/resolve/main/model.bin"
                        )
                    )
                )
            )
        )

        val spec = resolveManifestDownloadSpecs(manifest, "llama.cpp").getOrThrow().single()

        assertEquals("https://huggingface.co/owner/repo/resolve/$revisionA/model.bin", spec.url)
        assertFalse(spec.url.contains("/resolve/main/"))
    }

    @Test
    fun branchRevisionFailsValidation() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "test model",
                    framework = "llama.cpp",
                    repoId = "owner/repo",
                    revision = "main",
                    artifacts = listOf(
                        RemoteArtifactSpec(path = "model.bin")
                    )
                )
            )
        )

        val error = resolveManifestDownloadSpecs(manifest, "llama.cpp").exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("repository=owner/repo"))
        assertTrue(error?.message.orEmpty().contains("revision=main"))
        assertTrue(error?.message.orEmpty().contains("file=model.bin"))
    }

    @Test
    fun pinnedLegacyUrlKeepsNestedRepositoryPath() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "legacy model",
                    framework = "onnxruntime-genai",
                    destination = "onnxruntime-genai/model",
                    artifacts = listOf(
                        RemoteArtifactSpec(
                            filename = "model.onnx",
                            url = "https://huggingface.co/owner/repo/resolve/$revisionA/nested/model.onnx"
                        )
                    )
                )
            )
        )

        val spec = resolveManifestDownloadSpecs(manifest, "onnxruntime-genai").getOrThrow().single()

        assertEquals("https://huggingface.co/owner/repo/resolve/$revisionA/nested/model.onnx", spec.url)
        assertEquals("onnxruntime-genai/model/model.onnx", spec.destination)
    }

    @Test
    fun manifestSpecDoesNotContainSha256BeforeHuggingFaceMetadataResolution() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "test model",
                    framework = "llama.cpp",
                    repoId = "owner/repo",
                    revision = revisionA,
                    artifacts = listOf(
                        RemoteArtifactSpec(path = "model.bin")
                    )
                )
            )
        )

        val spec = resolveManifestDownloadSpecs(manifest, "llama.cpp").getOrThrow().single()

        assertNull(spec.sha256)
        assertEquals("owner/repo", spec.huggingFaceSource?.repository)
        assertEquals(revisionA, spec.huggingFaceSource?.revision)
        assertEquals("model.bin", spec.huggingFaceSource?.path)
    }

    @Test
    fun manifestArtifactSha256IsPassedThroughWhenConfigured() {
        val expectedSha256 = "d".repeat(64)
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "test model",
                    framework = "llama.cpp",
                    repoId = "owner/repo",
                    revision = revisionA,
                    artifacts = listOf(
                        RemoteArtifactSpec(
                            path = "model.bin",
                            sha256 = " $expectedSha256 "
                        )
                    )
                )
            )
        )

        val spec = resolveManifestDownloadSpecs(manifest, "llama.cpp").getOrThrow().single()

        assertEquals(expectedSha256, spec.sha256)
    }

    @Test
    fun differentBackendsCanUseDifferentRevisions() {
        val manifest = Manifest(
            models = listOf(
                RemoteModelSpec(
                    name = "llama model",
                    framework = "llama.cpp",
                    repoId = "owner/llama-repo",
                    revision = revisionA,
                    artifacts = listOf(
                        RemoteArtifactSpec(path = "llama.bin")
                    )
                ),
                RemoteModelSpec(
                    name = "mnn model",
                    framework = "mnn",
                    repoId = "owner/mnn-repo",
                    revision = revisionB,
                    artifacts = listOf(
                        RemoteArtifactSpec(path = "mnn.bin")
                    )
                )
            )
        )

        val llamaSpec = resolveManifestDownloadSpecs(manifest, "llama.cpp").getOrThrow().single()
        val mnnSpec = resolveManifestDownloadSpecs(manifest, "mnn").getOrThrow().single()

        assertEquals(revisionA, llamaSpec.huggingFaceSource?.revision)
        assertEquals("https://huggingface.co/owner/llama-repo/resolve/$revisionA/llama.bin", llamaSpec.url)
        assertEquals(revisionB, mnnSpec.huggingFaceSource?.revision)
        assertEquals("https://huggingface.co/owner/mnn-repo/resolve/$revisionB/mnn.bin", mnnSpec.url)
    }
}
