/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests backend selection for manifest-declared model files.
 */
class ManifestTest {
    @Test
    fun testManifestEntryWithoutFrameworkAppliesToAllBackends() {
        val spec = RemoteModelSpec()

        assertTrue(spec.appliesToFramework("llama.cpp"))
        assertTrue(spec.appliesToFramework("mnn"))
    }

    @Test
    fun testManifestEntryWithBlankFrameworkAppliesToAllBackends() {
        val spec = RemoteModelSpec(framework = "   ")

        assertTrue(spec.appliesToFramework("llama.cpp"))
        assertTrue(spec.appliesToFramework("mnn"))
    }

    @Test
    fun testCommonManifestEntryAppliesToAllBackends() {
        val spec = RemoteModelSpec(framework = "common")

        assertTrue(spec.appliesToFramework("onnxruntime-genai"))
        assertTrue(spec.appliesToFramework("mediapipe"))
    }

    @Test
    fun testManifestEntryAppliesOnlyToMatchingBackend() {
        val spec = RemoteModelSpec(framework = "mnn")

        assertTrue(spec.appliesToFramework("mnn"))
        assertFalse(spec.appliesToFramework("llama.cpp"))
    }

    @Test
    fun testCommaSeparatedFrameworksAreSupported() {
        val spec = RemoteModelSpec(framework = "llama.cpp, onnxruntime-genai")

        assertTrue(spec.appliesToFramework("llama.cpp"))
        assertTrue(spec.appliesToFramework("onnxruntime-genai"))
        assertFalse(spec.appliesToFramework("mnn"))
    }
}
