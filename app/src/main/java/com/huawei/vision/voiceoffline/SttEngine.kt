package com.huawei.vision.voiceoffline

import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.util.concurrent.Executors

class SttEngine(private val context: Context) {

    companion object {
        private const val TAG = "SttEngine"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    private var isStreaming = false
    private var utteranceStartTimeMs = 0L
    private var decodeTimeAccumulatorMs = 0L
    private var audioSamplesCount = 0L

    var isReady = false
        private set

    fun init(onComplete: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                Log.i(TAG, "Initializing Sherpa-ONNX streaming Zipformer RU model...")
                val t0 = SystemClock.elapsedRealtime()

                val config = OnlineRecognizerConfig().apply {
                    featConfig.sampleRate = 16000
                    featConfig.featureDim = 80
                    modelConfig = OnlineModelConfig().apply {
                        transducer = OnlineTransducerModelConfig().apply {
                            encoder = "model/encoder.int8.onnx"
                            decoder = "model/decoder.onnx"
                            joiner = "model/joiner.int8.onnx"
                        }
                        tokens = "model/tokens.txt"
                        bpeVocab = "model/bpe.model"
                        modelType = "zipformer2"
                        numThreads = 2
                        provider = "cpu"
                        debug = false
                    }
                    enableEndpoint = false
                    decodingMethod = "greedy_search"
                    maxActivePaths = 4
                }

                val rec = OnlineRecognizer(context.assets, config)
                recognizer = rec
                isReady = true

                val initTime = SystemClock.elapsedRealtime() - t0
                val msg = "Sherpa-ONNX Zipformer RU (INT8) инициализирован за ${initTime} ms"
                Log.i(TAG, msg)
                onComplete(true, msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Sherpa-ONNX", e)
                isReady = false
                onComplete(false, "Ошибка инициализации STT: ${e.message}")
            }
        }
    }

    fun startUtterance() {
        executor.execute {
            val rec = recognizer ?: return@execute
            if (!isReady) return@execute

            stream?.release()
            stream = rec.createStream()

            isStreaming = true
            utteranceStartTimeMs = SystemClock.elapsedRealtime()
            decodeTimeAccumulatorMs = 0L
            audioSamplesCount = 0L
            Log.d(TAG, "Utterance stream created")
        }
    }

    fun feedSamples(samples: FloatArray, onPartialText: ((String) -> Unit)? = null) {
        executor.execute {
            val rec = recognizer ?: return@execute
            val stm = stream ?: return@execute
            if (!isStreaming) return@execute

            audioSamplesCount += samples.size
            stm.acceptWaveform(samples, 16000)

            while (rec.isReady(stm)) {
                val d0 = SystemClock.elapsedRealtime()
                rec.decode(stm)
                decodeTimeAccumulatorMs += (SystemClock.elapsedRealtime() - d0)
            }

            val partialText = rec.getResult(stm).text.trim()
            if (partialText.isNotEmpty()) {
                onPartialText?.invoke(partialText)
            }
        }
    }

    fun finishUtterance(keyUpTimeMs: Long, onResult: (SttMetrics) -> Unit) {
        executor.execute {
            val rec = recognizer
            val stm = stream
            if (!isStreaming || rec == null || stm == null) {
                onResult(SttMetrics())
                return@execute
            }

            isStreaming = false
            stm.inputFinished()

            while (rec.isReady(stm)) {
                val d0 = SystemClock.elapsedRealtime()
                rec.decode(stm)
                decodeTimeAccumulatorMs += (SystemClock.elapsedRealtime() - d0)
            }

            val finalText = rec.getResult(stm).text.trim()
            val finishTimeMs = SystemClock.elapsedRealtime()

            val audioDurationMs = if (audioSamplesCount > 0) (audioSamplesCount * 1000L) / 16000L else 0L
            val postReleaseLatencyMs = (finishTimeMs - keyUpTimeMs).coerceAtLeast(0)
            val rtf = if (audioDurationMs > 0) decodeTimeAccumulatorMs.toFloat() / audioDurationMs else 0.0f

            val memInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memInfo)
            val pssMb = memInfo.totalPss / 1024.0
            val cpuTimeMs = Process.getElapsedCpuTime()

            val metrics = SttMetrics(
                audioDurationMs = audioDurationMs,
                postReleaseLatencyMs = postReleaseLatencyMs,
                totalDecodeDurationMs = decodeTimeAccumulatorMs,
                rtf = rtf,
                peakRamMb = pssMb,
                cpuTimeMs = cpuTimeMs,
                finalTranscription = finalText
            )

            Log.i(TAG, "Utterance done: '$finalText' | latency=${postReleaseLatencyMs}ms | RTF=$rtf | RAM=${"%.1f".format(pssMb)}MB")
            onResult(metrics)
        }
    }

    fun decodeWav(assetPath: String, onResult: (SttMetrics) -> Unit) {
        executor.execute {
            val rec = recognizer
            if (!isReady || rec == null) {
                onResult(SttMetrics(finalTranscription = "Ошибка: модель не инициализирована"))
                return@execute
            }

            try {
                val wavData = WavReader.readWavFromAsset(context.assets, assetPath)
                val testStream = rec.createStream()
                val startTime = SystemClock.elapsedRealtime()

                var decodeAccumMs = 0L
                testStream.acceptWaveform(wavData.samples, wavData.sampleRate)
                testStream.inputFinished()

                while (rec.isReady(testStream)) {
                    val d0 = SystemClock.elapsedRealtime()
                    rec.decode(testStream)
                    decodeAccumMs += (SystemClock.elapsedRealtime() - d0)
                }

                val resultText = rec.getResult(testStream).text.trim()
                val finishTime = SystemClock.elapsedRealtime()
                testStream.release()

                val audioDurationMs = (wavData.samples.size * 1000L) / wavData.sampleRate
                val totalTimeMs = finishTime - startTime
                val rtf = if (audioDurationMs > 0) decodeAccumMs.toFloat() / audioDurationMs else 0.0f

                val memInfo = Debug.MemoryInfo()
                Debug.getMemoryInfo(memInfo)
                val pssMb = memInfo.totalPss / 1024.0
                val cpuTimeMs = Process.getElapsedCpuTime()

                val metrics = SttMetrics(
                    audioDurationMs = audioDurationMs,
                    postReleaseLatencyMs = totalTimeMs,
                    totalDecodeDurationMs = decodeAccumMs,
                    rtf = rtf,
                    peakRamMb = pssMb,
                    cpuTimeMs = cpuTimeMs,
                    finalTranscription = resultText
                )

                onResult(metrics)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode WAV: $assetPath", e)
                onResult(SttMetrics(finalTranscription = "Ошибка WAV: ${e.message}"))
            }
        }
    }

    fun release() {
        executor.execute {
            stream?.release()
            stream = null
            recognizer?.release()
            recognizer = null
            isReady = false
        }
        executor.shutdown()
    }
}
