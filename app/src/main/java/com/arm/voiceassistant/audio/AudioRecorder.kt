/*
 * SPDX-FileCopyrightText: Copyright 2023-2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.arm.voiceassistant.audio

import android.media.AudioRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.floor
import java.io.FileOutputStream
import java.io.IOException
import android.util.Log
import com.arm.voiceassistant.utils.Constants
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.ToastService

/**
 * Class to record audio to WAV format.
 * An AudioRecord instance is provided (typically by the SpeechRecorder),
 * as microphone permissions must be handled in the Activity/Service context.
 */
class AudioRecorder(
    private var record: AudioRecord?,
    private val sourceSampleRate: Int = SAMPLE_RATE,
    bufferSizeBytes: Int? = null,
    private val scope: CoroutineScope
) {

    private val isRecording = java.util.concurrent.atomic.AtomicBoolean(false)

    val audioData = arrayListOf<Byte>()   // final WAV data buffer (header + PCM)
    private var recordingJob: Job? = null // job for the background recording coroutine
    private var rawAudio = ShortArray(0)
    private var rawAudioSize = 0

    val audioRecord: AudioRecord?
        get() = record

    // Determine buffer size in frames (16-bit samples) for reading
    private val bufferFrames: Int = bufferSizeBytes?.let {
        // ensure even number of bytes (each sample is 2 bytes)
        if (it % 2 != 0) (it / 2) + 1 else it / 2
    } ?: bufElem2Rec  // default to predefined frames if not specified

    /**
     * Start the recording and begin reading audio data in a background thread.
     * This method returns immediately.
     * @throws IllegalStateException if AudioRecord is not initialized.
     */

    fun startRecording() {
        if (record == null || record?.state != AudioRecord.STATE_INITIALIZED) {
            Log.d(VOICE_ASSISTANT_TAG, "AudioRecord not initialized or already released; cannot start recording")
            ToastService.showToast(Constants.AUD_REC_START_FAILED)
            return
        }

        // Reject re-entrance if already recording
        if (!isRecording.compareAndSet(false, true)) {
            Log.w(VOICE_ASSISTANT_TAG, "startRecording called while already recording; ignoring")
            return
        }

        // Clear any old data
        audioData.clear()
        rawAudioSize = 0

        // Start audio capture
        try {
            record?.startRecording()
        } catch (e: IllegalStateException) {
            // If start fails, revert state so caller can retry safely
            isRecording.set(false)
            throw e
        }

        recordingJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(bufferFrames)
            try {
                while (isRecording.get()) {
                    val currentRecordState = record ?: break
                    val numShorts = currentRecordState.read(shortBuffer, 0, shortBuffer.size)
                    if (numShorts < 0) {
                        Log.d(VOICE_ASSISTANT_TAG, "AudioRecord.read() returned error code $numShorts; stopping recording loop")
                        ToastService.showToast(Constants.AUD_REC_INPUT_ERROR)
                        break
                    }
                    if (numShorts > 0) {
                        ensureRawCapacity(rawAudioSize + numShorts)
                        // Copy only the valid portion of shortBuffer
                        System.arraycopy(shortBuffer, 0, rawAudio, rawAudioSize, numShorts)
                        rawAudioSize += numShorts
                    }
                }
            } catch (e: IOException) {
                Log.e(VOICE_ASSISTANT_TAG, "Unexpected I/O error during AudioRecord.read()", e)
            } finally {
                // Ensure state is consistent even if loop exits unexpectedly
                isRecording.set(false)
            }
        }
    }

    /**
     * Stop the recording and prepare the recorded data.
     * This will signal the background read loop to end, wait for it to finish,
     * and then finalize the WAV header and audio data.
     */
    fun stopRecording(save: Boolean) {
        if (!isRecording.compareAndSet(true, false)) {
            Log.d(VOICE_ASSISTANT_TAG, "stopRecording called but no recording was in progress")
            return
        }

        try {
            record?.stop()
            Log.d(VOICE_ASSISTANT_TAG, "AudioRecord has been stopped.")
        } catch (e: IllegalStateException) {
            Log.w(VOICE_ASSISTANT_TAG, "AudioRecord stop called when it was not recording: ${e.message}")
        }

        // Wait for background recording job to finish
        // Note:
        // - AudioRecord.read() is a blocking call and coroutine cancellation does not guarantee it unblocks.
        // - record?.stop() is what actually unblocks read(); after that, join is safe.
        // - This avoids potential hangs and should make shutdown deterministic.
        recordingJob?.let { job ->
            runBlocking {
                try {
                    job.join()   // Wait for read-loop to exit after record?.stop() unblocks it
                } catch (e: InterruptedException) {
                }
            }
            recordingJob = null
        }
        // If not saving, clear buffers and return (cancel flow)
        if (!save) {
            audioData.clear()
            rawAudioSize = 0
            return
        }
        // Prepare final WAV data (with resampling if needed)
        val recordedShorts: ShortArray = rawAudio.copyOf(rawAudioSize)
        val finalShorts: ShortArray = if (sourceSampleRate != SAMPLE_RATE) {
            // Resample to 16 kHz target sample rate
            resamplePCM(recordedShorts, sourceSampleRate, SAMPLE_RATE)
        } else {
            recordedShorts
        }
        audioData.clear()
        val audioBytes = shortToByte(finalShorts)          // PCM 16-bit little-endian data
        val headerBytes = createWavHeader(audioBytes.size) // 44-byte WAV header for mono 16-bit PCM
        audioData.addAll(headerBytes.toList())
        audioData.addAll(audioBytes.toList())
        rawAudioSize = 0
    }
    // Overloads for convenience:
    fun stopRecording() = stopRecording(save = true)
    fun cancelRecording() = stopRecording(save = false)

    /**
     * Release the underlying AudioRecord and associated resources.
     *
     * Safe to call multiple times. If a recording is in progress, it will be
     * stopped without saving before releasing the AudioRecord.
     */
    fun release() {
        try {
            // If a recording is in progress, stop it without saving
            if (isRecording.get()) {
                stopRecording(save = false)
            } else {
                // If not recording, still ensure the job is not lingering
                recordingJob?.let { job ->
                    runBlocking {
                        try { job.join() } catch (_: InterruptedException) {}
                    }
                    recordingJob = null
                }
            }

            record?.let { audioRecord ->
                try {
                    if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                } catch (_: IllegalStateException) {
                    // Safe to ignore; recorder may already be stopped.
                }

                try {
                    audioRecord.release()
                } catch (e: Exception) {
                    Log.i(VOICE_ASSISTANT_TAG, "AudioRecord release failed: ${e.message}")
                } finally {
                    record = null
                }
            }

        } catch (e: Exception) {
            Log.i(
                VOICE_ASSISTANT_TAG,
                "AudioRecorder.release encountered an error: ${e.message}"
            )
        }
    }

    /**
     * Write the recorded audio data to a file in WAV format.
     * Note this function expects that concurrent access to audioData will not occur.
     * We treat audioData as a final buffer prepared on the main thread after recording which is immediately written to file.
     * @param path The file path where the WAV file should be saved.
     * @throws RuntimeException if an I/O error occurs during file writing e.g. no memory.
     */
    fun writeToFile(path: String) {
        // Take a snapshot of the audio data buffer (header + samples)
        val copy: ByteArray = audioData.toList().toByteArray()

        try {
            FileOutputStream(path).use { os ->
                os.write(copy)
                os.flush()
            }
        } catch (e: IOException) {
            // Wrap I/O errors in a runtime exception to indicate failure
            Log.e(VOICE_ASSISTANT_TAG, "Recording failed: ${e.message}", e)
            ToastService.showToast(Constants.AUD_REC_SAVE_FAILED)
        }
    }

    /**
     * Convert an array of PCM 16-bit samples (Shorts) to a ByteArray (little-endian).
     * This is required for WAV output, which stores PCM audio as little-endian byte pairs
     * regardless of the platform's native endianness.
     * @param shortData The PCM audio samples as 16-bit values.
     * @return A ByteArray containing the interleaved little-endian bytes of the input samples.
     */
    private fun shortToByte(shortData: ShortArray): ByteArray {
        val bytes = ByteArray(shortData.size * 2)
        var j = 0
        for (i in shortData.indices) {
            val s = shortData[i].toInt()
            bytes[j++] = (s and 0xFF).toByte()         // little-endian low byte
            bytes[j++] = ((s shr 8) and 0xFF).toByte() // little-endian high byte
        }
        return bytes
    }

    /**
     * Resample PCM audio from one sample rate to another using linear interpolation.
     * @param inputSamples The input PCM samples at the source rate.
     * @param srcRate The source sample rate (Hz).
     * @param dstRate The target sample rate (Hz).
     * @return A new ShortArray containing the audio resampled to the target rate.
     */
    private fun resamplePCM(inputSamples: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (inputSamples.isEmpty() || srcRate == dstRate) {
            return inputSamples  // no resampling needed
        }
        val ratio = srcRate.toDouble() / dstRate
        // Compute length of output buffer
        val outputLength = floor(inputSamples.size / ratio).toInt()

        // Linear PCM resampling: map each output sample back into input space
        // using srcRate/dstRate and interpolate between adjacent samples.
        val outputSamples = ShortArray(outputLength)
        var pos = 0.0
        for (i in 0 until outputLength) {
            val index = pos.toInt()
            val frac = pos - index  // fractional part for interpolation
            outputSamples[i] = if (index >= inputSamples.size - 1) {
                // At end, use last sample
                inputSamples[inputSamples.size - 1]
            } else {
                // Linear interpolate between adjacent samples
                val sample1 = inputSamples[index]
                val sample2 = inputSamples[index + 1]
                val interpolated = sample1 + ((sample2 - sample1) * frac).toInt()
                interpolated.toShort()
            }
            pos += ratio
        }
        return outputSamples
    }

    /**
     * Ensures that the internal raw PCM buffer has sufficient capacity for upcoming appends.
     *
     * The buffer grows geometrically (doubling in size) to avoid frequent reallocations and
     * array copies while recording. This provides amortized O(1) append behavior, similar
     * to ArrayList growth semantics, but using a primitive ShortArray to avoid boxing.
     *
     * The initial capacity is chosen to comfortably hold several AudioRecord read buffers
     * (~256 ms at 16 kHz mono), reducing allocation churn for short recordings.
     *
     * @param required The total number of samples that must fit in the buffer after appending.
     */
    private fun ensureRawCapacity(required: Int) {
        // Initial allocation size for the PCM buffer (in samples).
        // 4096 samples ≈ 256 ms at 16 kHz mono.
        val INITIAL_PCM_BUFFER_SAMPLES = 4096

        if (rawAudio.size >= required) return

        var newCap = if (rawAudio.isEmpty()) {
            INITIAL_PCM_BUFFER_SAMPLES
        } else {
            rawAudio.size
        }

        while (newCap < required) {
            newCap *= 2
        }

        rawAudio = rawAudio.copyOf(newCap)
    }

    /**
     * Create a WAV file header for the given audio data size.
     * @param dataSizeBytes The size of the raw audio data in bytes (not including the header).
     * @return A 44-byte array representing the WAV file header for a PCM mono 16-bit audio.
     */
    private fun createWavHeader(dataSizeBytes: Int): ByteArray {
        val wavHeader = ByteArray(WAV_HEADER_SIZE)
        val totalDataLen = 36 + dataSizeBytes  // total data length for header (file size - 8)

        wavHeader[0] = 'R'.code.toByte()
        wavHeader[1] = 'I'.code.toByte()
        wavHeader[2] = 'F'.code.toByte()
        wavHeader[3] = 'F'.code.toByte()

        // ChunkSize (file size - 8 bytes)
        wavHeader[4] = (totalDataLen and 0xFF).toByte()
        wavHeader[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        wavHeader[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        wavHeader[7] = ((totalDataLen shr 24) and 0xFF).toByte()

        wavHeader[8] = 'W'.code.toByte()
        wavHeader[9] = 'A'.code.toByte()
        wavHeader[10] = 'V'.code.toByte()
        wavHeader[11] = 'E'.code.toByte()

        wavHeader[12] = 'f'.code.toByte()
        wavHeader[13] = 'm'.code.toByte()
        wavHeader[14] = 't'.code.toByte()
        wavHeader[15] = ' '.code.toByte()

        // Subchunk1 size (PCM header size = 16)
        wavHeader[16] = 16
        wavHeader[17] = 0
        wavHeader[18] = 0
        wavHeader[19] = 0

        // Audio format (1 = PCM)
        wavHeader[20] = 1
        wavHeader[21] = 0

        // Number of channels (1 = mono). Note as data is multi-byte we need the second byte to be 0
        wavHeader[22] = NUMBER_CHANNELS.toByte()
        wavHeader[23] = 0

        // Sample rate (little-endian 32-bit)
        wavHeader[24] = (SAMPLE_RATE and 0xFF).toByte()
        wavHeader[25] = ((SAMPLE_RATE shr 8) and 0xFF).toByte()
        wavHeader[26] = ((SAMPLE_RATE shr 16) and 0xFF).toByte()
        wavHeader[27] = ((SAMPLE_RATE shr 24) and 0xFF).toByte()

        // Byte rate = sampleRate * numChannels * bitsPerSample/8
        wavHeader[28] = (BYTE_RATE and 0xFF).toByte()
        wavHeader[29] = ((BYTE_RATE shr 8) and 0xFF).toByte()
        wavHeader[30] = ((BYTE_RATE shr 16) and 0xFF).toByte()
        wavHeader[31] = ((BYTE_RATE shr 24) and 0xFF).toByte()

        // Block align = numChannels * bitsPerSample/8
        wavHeader[32] = ((NUMBER_CHANNELS * BITS_PER_SAMPLE / 8) and 0xFF).toByte()
        wavHeader[33] = 0

        wavHeader[34] = BITS_PER_SAMPLE.toByte()
        wavHeader[35] = 0

        wavHeader[36] = 'd'.code.toByte()
        wavHeader[37] = 'a'.code.toByte()
        wavHeader[38] = 't'.code.toByte()
        wavHeader[39] = 'a'.code.toByte()

        wavHeader[40] = (dataSizeBytes and 0xFF).toByte()
        wavHeader[41] = ((dataSizeBytes shr 8) and 0xFF).toByte()
        wavHeader[42] = ((dataSizeBytes shr 16) and 0xFF).toByte()
        wavHeader[43] = ((dataSizeBytes shr 24) and 0xFF).toByte()

        return wavHeader
    }

    companion object {
        const val WAV_HEADER_SIZE = 44
        const val SAMPLE_RATE = 16000  // target sample rate for output (16 kHz)
        const val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_MONO
        const val RECORDER_AUDIO_ENCODING: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE: Short = 16
        const val NUMBER_CHANNELS: Short = 1
        const val BYTE_RATE = SAMPLE_RATE * NUMBER_CHANNELS * BITS_PER_SAMPLE / 8
        var bufElem2Rec = 1024  // default buffer size in samples (legacy default)
    }
}
