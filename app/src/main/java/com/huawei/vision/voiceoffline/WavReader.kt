package com.huawei.vision.voiceoffline

import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavAudioData(
    val sampleRate: Int,
    val channels: Int,
    val samples: FloatArray
)

object WavReader {
    fun readWavFromAsset(assetManager: AssetManager, assetPath: String): WavAudioData {
        val inputStream = BufferedInputStream(assetManager.open(assetPath))
        val bytes = inputStream.use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read RIFF header
        val riff = String(bytes, 0, 4)
        if (riff != "RIFF") throw IllegalArgumentException("Not a valid RIFF file: $riff")
        val wave = String(bytes, 8, 4)
        if (wave != "WAVE") throw IllegalArgumentException("Not a valid WAVE file: $wave")

        var offset = 12
        var channels = 1
        var sampleRate = 16000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = buffer.getInt(offset + 4)
            offset += 8

            if (chunkId == "fmt ") {
                val format = buffer.getShort(offset).toInt()
                channels = buffer.getShort(offset + 2).toInt()
                sampleRate = buffer.getInt(offset + 4)
                bitsPerSample = buffer.getShort(offset + 14).toInt()
                offset += chunkSize
            } else if (chunkId == "data") {
                dataOffset = offset
                dataSize = chunkSize
                break
            } else {
                offset += chunkSize
            }
        }

        if (dataOffset == -1) {
            throw IllegalArgumentException("Data chunk not found in $assetPath")
        }

        val totalSamples = dataSize / (channels * (bitsPerSample / 8))
        val samples = FloatArray(totalSamples)

        buffer.position(dataOffset)
        if (bitsPerSample == 16) {
            for (i in 0 until totalSamples) {
                val sampleShort = buffer.short
                samples[i] = sampleShort / 32768.0f
                if (channels > 1) {
                    for (c in 1 until channels) buffer.short // Skip extra channels
                }
            }
        } else {
            throw UnsupportedOperationException("Unsupported bitsPerSample: $bitsPerSample")
        }

        return WavAudioData(sampleRate, 1, samples)
    }
}
