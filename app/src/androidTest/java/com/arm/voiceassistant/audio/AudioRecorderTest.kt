/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Method
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val PCM_NORMALIZE = (Short.MAX_VALUE + 1).toFloat()

/**
 * Instrumented tests for AudioRecorder resampling and WAV generation.
 * Verifies PCM resampling behavior, output similarity, and 16 kHz mono WAV formatting.
 */
@RunWith(AndroidJUnit4::class)
class AudioRecorderInstrumentedTest {

    /*
    Note: 12000 is approx 36% of peak value; this should maintain strong SNR while being low enough to avoid clipping
    after math operations. If amplitudes were very low (e.g., ±500), quantization error becomes a bigger proportion of the signal.
    */

    /**
     * Generates a mono PCM 16-bit sine wave.
     *
     * @param samples Number of samples to generate.
     * @param fHz Frequency of the tone in Hz.
     * @param sr Sample rate in samples per second.
     * @param amp Peak amplitude of the sine wave in PCM units (default ~36% of full scale).
     * @return ShortArray containing the generated sine wave samples.
     */
    private fun sine(samples: Int, fHz: Double, sr: Int, amp: Int = 12000): ShortArray {
        val out = ShortArray(samples)
        val w = 2.0 * Math.PI * fHz / sr
        var i = 0
        while (i < samples) {
            out[i] = (amp * kotlin.math.sin(w * i)).toInt().toShort()
            i++
        }
        return out
    }

    /**
     * Computes the normalized cross-correlation between two PCM signals.
     *
     * Signals are first normalized to approximately [-1, 1) and then compared.
     * The result lies in the range [-1, 1], where 1 indicates perfect similarity.
     *
     * @param a First PCM signal.
     * @param b Second PCM signal.
     * @return Normalized correlation coefficient, or 0.0 if either signal has zero energy.
     */
    private fun correlation(a: ShortArray, b: ShortArray): Double {
        val n = min(a.size, b.size)
        if (n == 0) return 0.0

        var sum1 = 0.0
        var sum2 = 0.0
        var sum12 = 0.0

        var i = 0
        while (i < n) {
            // Normalize each sample to roughly [-1, 1)
            val v1 = a[i] / PCM_NORMALIZE
            val v2 = b[i] / PCM_NORMALIZE

            // Energy of each signal
            sum1 += v1 * v1
            sum2 += v2 * v2

            // Dot product between them
            sum12 += v1 * v2

            i++
        }

        if (sum1 == 0.0 || sum2 == 0.0) return 0.0

        // Normalized cross-correlation in [-1, 1]
        return sum12 / sqrt(sum1 * sum2)
    }

    /**
     * Invokes the private resamplePCM method on AudioRecorder via reflection.
     *
     * @param ar AudioRecorder instance on which to invoke resamplePCM.
     * @param input Input PCM data.
     * @param src Source sample rate.
     * @param dst Destination sample rate.
     * @return Resampled PCM data as a ShortArray.
     */
    private fun callResamplePCM(
        ar: AudioRecorder,
        input: ShortArray,
        src: Int,
        dst: Int
    ): ShortArray {
        val m: Method = AudioRecorder::class.java.getDeclaredMethod(
            "resamplePCM",
            ShortArray::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        m.isAccessible = true
        return m.invoke(ar, input, src, dst) as ShortArray
    }

    /**
     * Reads a 32-bit little-endian integer from a byte array.
     *
     * @param b Source byte array.
     * @param off Starting offset in the array.
     * @return 32-bit signed integer value interpreted as little-endian.
     */
    private fun le32(b: ByteArray, off: Int): Int {
        var v = 0
        v = v or (b[off].toInt() and 0xFF)
        v = v or ((b[off + 1].toInt() and 0xFF) shl 8)
        v = v or ((b[off + 2].toInt() and 0xFF) shl 16)
        v = v or ((b[off + 3].toInt() and 0xFF) shl 24)
        return v
    }

    /**
     * Verifies that resampling down then up preserves length ratios
     * and that the round-trip signal remains highly correlated.
     *
     * Downsamples a 48 kHz tone to 16 kHz and back to 48 kHz,
     * checks expected output sizes, and asserts correlation above 0.97.
     */
    @Test
    fun resample_roundTrip_similarityAndLengths() {
        val src = 48000
        val dst = 16000
        val tone = sine(samples = src, fHz = 440.0, sr = src)

        val ar = AudioRecorder(
            record = null,
            sourceSampleRate = src,
            bufferSizeBytes = 2048,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )


        val down = callResamplePCM(ar, tone, src, dst)
        val up = callResamplePCM(ar, down, dst, src)

        assertEquals((tone.size * dst) / src, down.size)
        assertEquals((down.size * src) / dst, up.size)

        // Allow small interpolation loss
        assertTrue(correlation(tone, up) > 0.97)
    }

    /**
     * Verifies that stopRecording() produces a valid 16 kHz mono WAV file
     * with consistent RIFF headers and data sizes after resampling.
     *
     * Feeds synthetic PCM into AudioRecorder, triggers stopRecording(),
     * and asserts sample rate, data chunk size, and RIFF container size.
     */
    @Test
    fun stopRecording_buildsValid16kMonoWav_withExpectedSize() {
        val sourceSampleRate = 44100
        val targetSampleRate = AudioRecorder.SAMPLE_RATE // 16000
        val sampleDurationSeconds = 2

        // frequencyHz is somewhat arbitrary here, we just need a value less than SAMPLE_RATE/2
        val inputSignal =
            sine(
                samples = sourceSampleRate * sampleDurationSeconds,
                fHz = 440.0,
                sr = sourceSampleRate
            )

        val audioRecorder = AudioRecorder(
            record = null,
            sourceSampleRate = sourceSampleRate,
            bufferSizeBytes = 1024,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        )


        val rawField = AudioRecorder::class.java.getDeclaredField("rawAudio")
        rawField.isAccessible = true
        rawField.set(audioRecorder, inputSignal.copyOf())  // replace internal ShortArray buffer

        val rawSizeField = AudioRecorder::class.java.getDeclaredField("rawAudioSize")
        rawSizeField.isAccessible = true
        rawSizeField.setInt(audioRecorder, inputSignal.size)  // tell AudioRecorder how many samples are valid


        val isRecordingField = AudioRecorder::class.java.getDeclaredField("isRecording")
        isRecordingField.isAccessible = true
        val atomic = isRecordingField.get(audioRecorder) as java.util.concurrent.atomic.AtomicBoolean
        atomic.set(true)

        audioRecorder.stopRecording()

        val audioDataField = AudioRecorder::class.java.getDeclaredField("audioData")
        audioDataField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val audioDataBytes = audioDataField.get(audioRecorder) as ArrayList<Byte>
        val wav = ByteArray(audioDataBytes.size)
        var i = 0
        while (i < audioDataBytes.size) {
            wav[i] = audioDataBytes[i]
            i++
        }

        // "RIFF" and "WAVE" markers
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])

        val headerSampleRate = le32(wav, 24)
        assertEquals(targetSampleRate, headerSampleRate)

        val dataChunkSizeBytes = le32(wav, 40)
        val actualOutputSamples = dataChunkSizeBytes / Short.SIZE_BYTES
        val expectedOutputSamples =
            floor(inputSignal.size.toDouble() * targetSampleRate / sourceSampleRate).toInt()
        assertTrue(abs(actualOutputSamples - expectedOutputSamples) <= 1)

        val riffChunkSize = le32(wav, 4)
        assertEquals(wav.size - 8, riffChunkSize)
        assertEquals(44 + dataChunkSizeBytes, wav.size)
    }
}