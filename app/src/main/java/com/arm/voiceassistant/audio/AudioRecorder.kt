/*
 * SPDX-FileCopyrightText: Copyright 2023-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.audio

import android.media.AudioRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.experimental.and

/**
 * Class to record data to WAV format
 * AudioRecord is passed in as permissions need to be invoked in the parent Activity
 * and this is not possible within the class
 */
class AudioRecorder(private var record: AudioRecord?) {
    private var isRecording = false
    private lateinit var os: FileOutputStream
    val audioData = arrayListOf<Byte>()
    private var recordingThread: Thread? = null

    /**
     * Start the recording and set the appropriate flags
     */
    suspend fun startRecording() {
        audioData.clear()
        record?.startRecording()
        isRecording = true

        storeToBuffer()
    }

    /**
     * Writes the OutputStream to a File at the provided path
     * @param path The file path where the WAV file should be saved.
     * @throws RuntimeException if the file cannot be created or written.
     */
    @Synchronized
    fun writeToFile(path: String) {
        val copy: ByteArray
        synchronized(audioData) {
            copy = audioData.toList().toByteArray()
        }

        FileOutputStream(path).use { os ->
            os.write(copy)
            os.flush()
        }
    }

    /**
     * Stop the recording and set the appropriate flags
     */
    fun stopRecording() {
        record?.run {
            isRecording = false
            if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                stop()
                release()
            }
            recordingThread = null
            record = null
        }
    }

    /**
     * Convert Short to byte
     * @param shortData The PCM short values to convert.
     * @return The encoded byte array.
     */
    private fun shortToByte(shortData: ShortArray): ByteArray {
        val arraySize = shortData.size
        val bytes = ByteArray(arraySize * 2)
        for (i in 0 until arraySize) {
            bytes[i * 2] = (shortData[i] and 0x00FF).toByte()
            bytes[i * 2 + 1] = (shortData[i].toInt() shr 8).toByte()
            shortData[i] = 0
        }
        return bytes
    }

    /**
     * Writes the OutputStream to audioData buffer
     */
    private suspend fun storeToBuffer() = withContext(Dispatchers.IO) {
        val shortData = ShortArray(bufElem2Rec)
        for (byte in header()) {
            audioData.add(byte)
        }

        while (isRecording) {
            record?.read(shortData, 0, bufElem2Rec)
            try {
                val byteData = shortToByte(shortData)
                for (byte in byteData)
                    audioData.add(byte)
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    /**
     * Set the WAV header
     * see: https://docs.fileformat.com/audio/wav/
     * @return Byte array representing the WAV header.
     */
    private fun header(): ByteArray {
        val wavHeader = ByteArray(WAV_HEADER_SIZE)

        wavHeader[0] = 'R'.code.toByte()
        wavHeader[1] = 'I'.code.toByte()
        wavHeader[2] = 'F'.code.toByte()
        wavHeader[3] = 'F'.code.toByte()

        wavHeader[4] = (0 and 0xff).toByte()
        wavHeader[5] = (0 shr 8 and 0xff).toByte()
        wavHeader[6] = (0 shr 16 and 0xff).toByte()
        wavHeader[7] = (0 shr 24 and 0xff).toByte()

        wavHeader[8] = 'W'.code.toByte()
        wavHeader[9] = 'A'.code.toByte()
        wavHeader[10] = 'V'.code.toByte()
        wavHeader[11] = 'E'.code.toByte()

        wavHeader[12] = 'f'.code.toByte()
        wavHeader[13] = 'm'.code.toByte()
        wavHeader[14] = 't'.code.toByte()
        wavHeader[15] = ' '.code.toByte()

        wavHeader[16] = 16
        wavHeader[17] = 0
        wavHeader[18] = 0
        wavHeader[19] = 0

        wavHeader[20] = 1
        wavHeader[21] = 0

        wavHeader[22] = NUMBER_CHANNELS.toByte()
        wavHeader[23] = 0

        wavHeader[24] = (SAMPLE_RATE and 0xff).toByte()
        wavHeader[25] = (SAMPLE_RATE shr 8 and 0xff).toByte()
        wavHeader[26] = (SAMPLE_RATE shr 16 and 0xff).toByte()
        wavHeader[27] = (SAMPLE_RATE shr 24 and 0xff).toByte()

        wavHeader[28] =
            (BYTE_RATE and 0xff).toByte()
        wavHeader[29] = (BYTE_RATE shr 8 and 0xff).toByte()
        wavHeader[30] = (BYTE_RATE shr 16 and 0xff).toByte()
        wavHeader[31] = (BYTE_RATE shr 24 and 0xff).toByte()

        wavHeader[32] = (NUMBER_CHANNELS * BITS_PER_SAMPLE / 8).toByte()
        wavHeader[33] = 0

        wavHeader[34] = BITS_PER_SAMPLE.toByte()
        wavHeader[35] = 0

        wavHeader[36] = 'd'.code.toByte()
        wavHeader[37] = 'a'.code.toByte()
        wavHeader[38] = 't'.code.toByte()
        wavHeader[39] = 'a'.code.toByte()

        wavHeader[40] = (0 and 0xff).toByte()
        wavHeader[41] = (0 shr 8 and 0xff).toByte()
        wavHeader[42] = (0 shr 16 and 0xff).toByte()
        wavHeader[43] = (0 shr 24 and 0xff).toByte()

        return wavHeader
    }

    companion object {
        const val WAV_HEADER_SIZE = 44
        const val SAMPLE_RATE = 16000
        const val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_MONO
        const val RECORDER_AUDIO_ENCODING: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
        const val BITS_PER_SAMPLE: Short = 16
        const val NUMBER_CHANNELS: Short = 1
        const val BYTE_RATE = SAMPLE_RATE * NUMBER_CHANNELS * 16 / 8

        var bufElem2Rec = 1024
    }
}
