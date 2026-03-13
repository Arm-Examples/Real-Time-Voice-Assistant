/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CpuFeaturesUtilityTest {

    @Test
    fun parseCpuInfoLines_keepsFirstOccurrence() {
        val lines = sequenceOf(
            "processor : 0",
            "Features : fp asimd neon",
            "processor : 1",
            "Features : fp asimd"
        )

        val result = CpuFeaturesUtility.parseCpuInfoLines(lines)

        assertEquals("fp asimd neon", result["Features"])
    }

    @Test
    fun extractFeatures_normalizesAndSplits() {
        val map = mapOf("Features" to "  FP   asimd\tNeOn  ")

        val features = CpuFeaturesUtility.extractFeatures(map)

        assertEquals(setOf("fp", "asimd", "neon"), features)
    }

    @Test
    fun extractFeatures_supportsAlternateKeys() {
        val map = mapOf("CPU features" to "sve2 SME")

        val features = CpuFeaturesUtility.extractFeatures(map)

        assertEquals(setOf("sve2", "sme"), features)
    }

    @Test
    fun hasFeature_matchesCaseAndWhitespace() {
        val info = CpuInfo(coresAvailable = 4, features = setOf("neon", "sve2"))

        assertTrue(CpuFeaturesUtility.hasFeature(info, "  NEON  "))
    }
}
