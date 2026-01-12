/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.utils

import android.util.Log
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import java.io.File
import java.util.Locale

data class CpuInfo(
    val coresAvailable: Int,
    val features: Set<String>
)

const val FEATURE_SME = "sme"

/**
 * CPU Features Utility
 */
object CpuFeaturesUtility {

    @Volatile
    private var cachedInfo: CpuInfo? = null

    /**
     * Use the CpuFeaturesUtility to query for SME feature, log if this feature was found or not.
     * @return True if device has SME feature, false otherwise
     */
    fun hasSME() : Boolean {
        val cpu = getCpuInfo()
        val hasSME = hasFeature(cpu, FEATURE_SME)
        Log.d(VOICE_ASSISTANT_TAG, "SME feature found on device: $hasSME")
        return hasSME
    }

    /**
     * Get CPU information including CPU features and cores available
     */
    private fun getCpuInfo(): CpuInfo {
        cachedInfo?.let { return it }
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val cpuInfoMap = readProcCpuInfoKeyValues()
        val features = extractFeatures(cpuInfoMap)

        val info = CpuInfo(
            coresAvailable = cores,
            features = features
        )
        cachedInfo = info
        return info
    }

    /**
     * Check if feature (sme/neon, etc) is present in CPU info
     */
    fun hasFeature(info: CpuInfo, feature: String): Boolean {
        val f = feature.trim().lowercase(Locale.US)
        return info.features.contains(f)
    }

    internal fun extractFeatures(map: Map<String, String>): Set<String> {
        val raw = map["Features"] ?: map["features"] ?: map["CPU features"] ?: ""
        return raw
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase(Locale.US) }
            .toSet()
    }

    private fun readProcCpuInfoKeyValues(): Map<String, String> {
        // /proc/cpuinfo can contain repeated blocks per core.
        // Keep the first occurrence per key.
        val lines = readLinesOrNull("/proc/cpuinfo") ?: return emptyMap()
        return parseCpuInfoLines(lines)
    }

    private fun readLinesOrNull(path: String): Sequence<String>? =
        runCatching { File(path).bufferedReader().lineSequence() }.getOrNull()

    internal fun parseCpuInfoLines(lines: Sequence<String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in lines) {
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty() && !out.containsKey(key)) {
                out[key] = value
            }
        }
        return out
    }
}
