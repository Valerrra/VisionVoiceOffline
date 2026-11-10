package com.huawei.vision.voiceoffline

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class RemoteAudioCapture(private val context: Context) {

    companion object {
        private const val TAG = "RemoteAudioCapture"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SIZE = 512 // 32 ms chunks for streaming STT
        const val DEFAULT_TAIL_DRAIN_MS = 150L // Allow Bluetooth BLE/SBC in-flight audio to arrive
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var isDrainRequested = false

    @Volatile
    private var drainDeadlineMs = 0L

    @Volatile
    private var drainCallback: ((totalSamplesRead: Long, timeFirstChunkMs: Long, timeLastChunkMs: Long, tailChunksCount: Int) -> Unit)? = null

    @Volatile
    private var timeFirstChunkMs = 0L

    @Volatile
    private var timeLastChunkMs = 0L

    @Volatile
    private var totalSamplesRead = 0L

    data class DeviceInfoSummary(
        val id: Int,
        val productName: String,
        val address: String,
        val type: Int
    )

    fun findBackMicDevice(): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        // 1. Prioritize device with address "back" (HDRC-BV1 BT remote HAL stream)
        var target = devices.firstOrNull { it.address.equals("back", ignoreCase = true) }
        if (target != null) return target

        // 2. Secondary check: ID 13 (as proven on KANT-359)
        target = devices.firstOrNull { it.id == 13 }
        if (target != null) return target

        // 3. Fallback: BUILTIN_MIC
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }

    fun getAvailableInputDevices(): List<DeviceInfoSummary> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map {
            DeviceInfoSummary(it.id, it.productName.toString(), it.address, it.type)
        }
    }

    @SuppressLint("MissingPermission")
    fun startCapture(
        onAudioChunk: (FloatArray, Double, Int) -> Unit,
        onRouteConfirmed: (DeviceInfoSummary?, DeviceInfoSummary?) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (isRecording) {
            Log.w(TAG, "Capture already active")
            return false
        }

        try {
            timeFirstChunkMs = 0L
            timeLastChunkMs = 0L
            totalSamplesRead = 0L
            isDrainRequested = false
            drainCallback = null

            val targetDevice = findBackMicDevice()
            val minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBufSize * 2, 4096)

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                onError("AudioRecord initialization failed (state != STATE_INITIALIZED)")
                return false
            }

            var prefSummary: DeviceInfoSummary? = null
            if (targetDevice != null) {
                val prefSuccess = record.setPreferredDevice(targetDevice)
                prefSummary = DeviceInfoSummary(
                    targetDevice.id,
                    targetDevice.productName.toString(),
                    targetDevice.address,
                    targetDevice.type
                )
                Log.i(TAG, "setPreferredDevice back mic result: $prefSuccess (address=${targetDevice.address}, id=${targetDevice.id})")
            } else {
                Log.w(TAG, "Built-In Back Mic not found, using system default route")
            }

            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                onError("AudioRecord startRecording failed (recordingState != RECORDING)")
                return false
            }

            val routed = record.routedDevice
            val routedSummary = routed?.let {
                DeviceInfoSummary(it.id, it.productName.toString(), it.address, it.type)
            }
            Log.i(TAG, "AudioRecord started. Routed: address=${routedSummary?.address}, id=${routedSummary?.id}, name=${routedSummary?.productName}")

            audioRecord = record
            isRecording = true

            onRouteConfirmed(prefSummary, routedSummary)

            recordingThread = Thread({
                val shortBuffer = ShortArray(CHUNK_SIZE)
                var tailChunksCount = 0

                try {
                    while (isRecording) {
                        if (isDrainRequested && SystemClock.elapsedRealtime() >= drainDeadlineMs) {
                            Log.d(TAG, "Tail drain window elapsed ($DEFAULT_TAIL_DRAIN_MS ms). Stopping record loop.")
                            break
                        }

                        val readCount = record.read(shortBuffer, 0, CHUNK_SIZE)
                        if (readCount > 0) {
                            val now = SystemClock.elapsedRealtime()
                            if (timeFirstChunkMs == 0L) {
                                timeFirstChunkMs = now
                            }
                            timeLastChunkMs = now
                            totalSamplesRead += readCount

                            if (isDrainRequested) {
                                tailChunksCount++
                            }

                            val floatBuffer = FloatArray(readCount)
                            var sumSq = 0.0
                            var peak = 0

                            for (i in 0 until readCount) {
                                val sample = shortBuffer[i].toInt()
                                val absVal = abs(sample)
                                if (absVal > peak) peak = absVal
                                sumSq += absVal * absVal
                                floatBuffer[i] = sample / 32768.0f
                            }

                            val rms = sqrt(sumSq / readCount)
                            val rmsDb = if (rms > 0) 20 * log10(rms / 32768.0) else -96.0

                            onAudioChunk(floatBuffer, rmsDb, peak)
                        } else if (readCount < 0) {
                            Log.e(TAG, "AudioRecord read error code: $readCount")
                            break
                        }
                    }

                    // Stop hardware recording without destroying the AudioRecord buffer yet
                    try {
                        if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            record.stop()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping AudioRecord", e)
                    }

                    // Drain any remaining non-blocking samples already queued in AudioRecord
                    var consecutiveZeroes = 0
                    while (consecutiveZeroes < 3) {
                        val nbRead = record.read(shortBuffer, 0, CHUNK_SIZE, AudioRecord.READ_NON_BLOCKING)
                        if (nbRead > 0) {
                            tailChunksCount++
                            totalSamplesRead += nbRead
                            timeLastChunkMs = SystemClock.elapsedRealtime()

                            val floatBuffer = FloatArray(nbRead)
                            var sumSq = 0.0
                            var peak = 0

                            for (i in 0 until nbRead) {
                                val sample = shortBuffer[i].toInt()
                                val absVal = abs(sample)
                                if (absVal > peak) peak = absVal
                                sumSq += absVal * absVal
                                floatBuffer[i] = sample / 32768.0f
                            }

                            val rms = sqrt(sumSq / nbRead)
                            val rmsDb = if (rms > 0) 20 * log10(rms / 32768.0) else -96.0

                            onAudioChunk(floatBuffer, rmsDb, peak)
                            consecutiveZeroes = 0
                        } else {
                            consecutiveZeroes++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception in recordingThread", e)
                } finally {
                    try {
                        record.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing AudioRecord", e)
                    }

                    audioRecord = null
                    isRecording = false
                    isDrainRequested = false
                    recordingThread = null

                    Log.i(TAG, "AudioRecord gracefully stopped. Samples read: $totalSamplesRead, tail chunks: $tailChunksCount")
                    val cb = drainCallback
                    drainCallback = null
                    cb?.invoke(totalSamplesRead, timeFirstChunkMs, timeLastChunkMs, tailChunksCount)
                }
            }, "RemoteAudioCaptureThread").apply { start() }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during startCapture", e)
            onError("Capture error: ${e.message}")
            stopCapture()
            return false
        }
    }

    /**
     * Gracefully stops capture by allowing in-flight Bluetooth BLE/SBC audio frames
     * to arrive for [tailDrainMs] milliseconds, drains the hardware AudioRecord buffer,
     * releases resources, and invokes [onComplete].
     */
    fun stopCaptureGracefully(
        tailDrainMs: Long = DEFAULT_TAIL_DRAIN_MS,
        onComplete: (totalSamplesRead: Long, timeFirstChunkMs: Long, timeLastChunkMs: Long, tailChunksCount: Int) -> Unit
    ) {
        if (!isRecording) {
            onComplete(totalSamplesRead, timeFirstChunkMs, timeLastChunkMs, 0)
            return
        }

        drainCallback = onComplete
        drainDeadlineMs = SystemClock.elapsedRealtime() + tailDrainMs
        isDrainRequested = true
        Log.i(TAG, "Graceful stop requested (tail drain window: ${tailDrainMs}ms)...")
    }

    /**
     * Forceful immediate teardown (used on destroy or error).
     */
    fun stopCapture() {
        isRecording = false
        isDrainRequested = false
        drainCallback = null
        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
        }
        audioRecord = null
        Log.i(TAG, "AudioRecord forcefully stopped and released")
    }

    fun isCapturing(): Boolean = isRecording
}
