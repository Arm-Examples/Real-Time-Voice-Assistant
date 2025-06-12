/*
 * SPDX-FileCopyrightText: Copyright 2023-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.audio

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Class to read data from WAV format
 */
class AudioReader {
    companion object {
        // Normalisation factor used by libsndfile
        const val NORMALISATION_FACTOR = (1.0 / 0x8000).toFloat()
    }

    /**
     * Read and convert wav data to usable format
     * @param inputStream The input stream pointing to a WAV file.
     * @return A [FloatArray] containing normalized PCM audio data.
     */
    fun readWavData(inputStream: InputStream): FloatArray {
        val dataLength = inputStream.available() - AudioRecorder.WAV_HEADER_SIZE

        // 2 bytes map to a single float value in the output
        val audioData = FloatBuffer.allocate(dataLength / 2)
        val buffer = ByteArray(2)
        val bufferedFileStream = BufferedInputStream(inputStream)
        bufferedFileStream.use {
            it.skip(AudioRecorder.WAV_HEADER_SIZE.toLong())
            while (it.available() > 0) {
                it.read(buffer)
                val sample = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).short
                audioData.put(sample.toFloat() * NORMALISATION_FACTOR)
            }
        }

        return audioData.array()
    }
}
