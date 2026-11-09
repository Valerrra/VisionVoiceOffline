package com.huawei.vision.voiceoffline

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VisionVoiceOffline"
        private const val PERMISSION_REQ_RECORD_AUDIO = 1001

        private const val ACTION_VOICE_ASSIST_KEY_DOWN = "com.huawei.key.action.VOICE_ASSIST_KEY_DOWN"
        private const val ACTION_VOICE_ASSIST_KEY_UP = "com.huawei.key.action.VOICE_ASSIST_KEY_UP"
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var tvRecognizedText: TextView
    private lateinit var tvAudioMeter: TextView
    private lateinit var tvKeyIndicator: TextView

    private lateinit var tvMetricLatency: TextView
    private lateinit var tvMetricRtf: TextView
    private lateinit var tvMetricDuration: TextView
    private lateinit var tvMetricTotalTime: TextView
    private lateinit var tvMetricRam: TextView
    private lateinit var tvMetricCpu: TextView

    private lateinit var btnHoldRecord: Button
    private lateinit var btnClear: Button
    private lateinit var btnTestWav0: Button
    private lateinit var btnTestWav1: Button
    private lateinit var btnNextPhrase: Button

    private lateinit var tvPhraseCategory: TextView
    private lateinit var tvPhrasePrompt: TextView
    private lateinit var tvPhraseHelp: TextView

    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView

    private lateinit var sttEngine: SttEngine
    private lateinit var audioCapture: RemoteAudioCapture

    @Volatile
    private var isVoiceActive = false
    private var keyPressTimeMs = 0L
    private var currentPhraseIndex = 0

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val voiceKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_VOICE_ASSIST_KEY_DOWN -> {
                    log("Broadcast received: VOICE_ASSIST_KEY_DOWN")
                    handleVoiceStart("Huawei Broadcast KEY_DOWN")
                }
                ACTION_VOICE_ASSIST_KEY_UP -> {
                    log("Broadcast received: VOICE_ASSIST_KEY_UP")
                    handleVoiceStop("Huawei Broadcast KEY_UP")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initServices()
        registerVoiceKeyReceivers()
        updatePhraseCard()
        checkAudioPermission()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvModelInfo = findViewById(R.id.tvModelInfo)
        tvStatusBadge = findViewById(R.id.tvStatusBadge)
        tvRecognizedText = findViewById(R.id.tvRecognizedText)
        tvAudioMeter = findViewById(R.id.tvAudioMeter)
        tvKeyIndicator = findViewById(R.id.tvKeyIndicator)

        tvMetricLatency = findViewById(R.id.tvMetricLatency)
        tvMetricRtf = findViewById(R.id.tvMetricRtf)
        tvMetricDuration = findViewById(R.id.tvMetricDuration)
        tvMetricTotalTime = findViewById(R.id.tvMetricTotalTime)
        tvMetricRam = findViewById(R.id.tvMetricRam)
        tvMetricCpu = findViewById(R.id.tvMetricCpu)

        btnHoldRecord = findViewById(R.id.btnHoldRecord)
        btnClear = findViewById(R.id.btnClear)
        btnTestWav0 = findViewById(R.id.btnTestWav0)
        btnTestWav1 = findViewById(R.id.btnTestWav1)
        btnNextPhrase = findViewById(R.id.btnNextPhrase)

        tvPhraseCategory = findViewById(R.id.tvPhraseCategory)
        tvPhrasePrompt = findViewById(R.id.tvPhrasePrompt)
        tvPhraseHelp = findViewById(R.id.tvPhraseHelp)

        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)

        setupButtons()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        // UI Touch / Click hold support for testing
        btnHoldRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleVoiceStart("UI Button Hold")
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleVoiceStop("UI Button Release")
                    true
                }
                else -> false
            }
        }

        btnClear.setOnClickListener {
            tvRecognizedText.text = "Готов к распознаванию..."
            tvMetricLatency.text = "— ms"
            tvMetricRtf.text = "—"
            tvMetricDuration.text = "— ms"
            tvMetricTotalTime.text = "— ms"
            tvMetricRam.text = "— MB"
            tvMetricCpu.text = "— ms"
            tvLog.text = ""
            log("Состояние очищено")
        }

        btnTestWav0.setOnClickListener {
            runWavBenchmark("test_wavs/0.wav", "0.wav")
        }

        btnTestWav1.setOnClickListener {
            runWavBenchmark("test_wavs/1.wav", "1.wav")
        }

        btnNextPhrase.setOnClickListener {
            currentPhraseIndex = (currentPhraseIndex + 1) % TestPhrases.items.size
            updatePhraseCard()
            log("Выбрана фраза ${currentPhraseIndex + 1}/10: ${TestPhrases.items[currentPhraseIndex].phrase}")
        }
    }

    private fun updatePhraseCard() {
        val p = TestPhrases.items[currentPhraseIndex]
        tvPhraseCategory.text = "Тест ${p.index}/${p.total}: ${p.category}"
        tvPhrasePrompt.text = "«${p.phrase}»"
        tvPhraseHelp.text = "${p.hint} (Зажмите микрофон на пульте)"
        btnNextPhrase.text = "След. фраза (${p.index}/${p.total})"
    }

    private fun initServices() {
        audioCapture = RemoteAudioCapture(this)
        sttEngine = SttEngine(this)

        log("Инициализация Vosk Zipformer Small RU (INT8)...")
        tvModelInfo.text = "Модель: Загрузка INT8..."

        sttEngine.init { success, msg ->
            runOnUiThread {
                if (success) {
                    tvModelInfo.text = "Модель: Vosk Zipformer RU (Готова)"
                    tvModelInfo.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    log(msg)
                } else {
                    tvModelInfo.text = "Модель: Ошибка"
                    tvModelInfo.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                    log("ОШИБКА: $msg")
                }
            }
        }
    }

    private fun registerVoiceKeyReceivers() {
        val filter = IntentFilter().apply {
            addAction(ACTION_VOICE_ASSIST_KEY_DOWN)
            addAction(ACTION_VOICE_ASSIST_KEY_UP)
        }
        registerReceiver(voiceKeyReceiver, filter)
        log("Зарегистрирован BroadcastReceiver для Huawei Voice Assist")
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log("Запрос разрешения RECORD_AUDIO...")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQ_RECORD_AUDIO)
        } else {
            log("Разрешение RECORD_AUDIO предоставлено")
            logInputDevices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                log("Разрешение RECORD_AUDIO успешно получено")
                logInputDevices()
            } else {
                log("ОШИБКА: Разрешение RECORD_AUDIO отклонено!")
            }
        }
    }

    private fun logInputDevices() {
        val devices = audioCapture.getAvailableInputDevices()
        log("Найдено аудиоустройств ввода: ${devices.size}")
        for (d in devices) {
            log("  Device ID: ${d.id}, Name: '${d.productName}', Address: '${d.address}', Type: ${d.type}")
        }
        val target = audioCapture.findBackMicDevice()
        log("Целевой пульт (back mic): ID=${target?.id}, address='${target?.address}'")
    }

    @Synchronized
    private fun handleVoiceStart(source: String) {
        if (isVoiceActive) {
            // Already active; ignore duplicate/repeat key-down events
            return
        }

        if (!sttEngine.isReady) {
            log("Внимание: модель STT еще загружается, ожидайте...")
            return
        }

        isVoiceActive = true
        keyPressTimeMs = SystemClock.elapsedRealtime()

        runOnUiThread {
            tvStatusBadge.text = "ЗАПИСЬ И РЕЧЬ"
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            tvKeyIndicator.text = "Кнопка: НАЖАТА ($source)"
            tvKeyIndicator.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvRecognizedText.text = "Слушаю..."
            tvRecognizedText.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        }

        log("Начало сессии распознавания ($source)")
        sttEngine.startUtterance()

        val started = audioCapture.startCapture(
            onAudioChunk = { samples, rmsDb, peak ->
                // Feed to streaming STT engine
                sttEngine.feedSamples(samples) { partialText ->
                    runOnUiThread {
                        if (isVoiceActive) {
                            tvRecognizedText.text = partialText
                        }
                    }
                }

                // Update audio meter occasionally
                runOnUiThread {
                    tvAudioMeter.text = "RMS: ${"%.1f".format(rmsDb)} dB | Peak: $peak | Маршрут: back"
                }
            },
            onRouteConfirmed = { preferred, routed ->
                runOnUiThread {
                    log("Маршрутизация: Pref=${preferred?.address} (id ${preferred?.id}) -> Routed=${routed?.address} (id ${routed?.id})")
                }
            },
            onError = { errMsg ->
                runOnUiThread {
                    log("ОШИБКА ЗАХВАТА: $errMsg")
                    tvStatusBadge.text = "ОШИБКА"
                    tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
                }
            }
        )

        if (!started) {
            isVoiceActive = false
            log("Не удалось запустить захват звука!")
        }
    }

    @Synchronized
    private fun handleVoiceStop(source: String) {
        if (!isVoiceActive) return

        val keyUpTimeMs = SystemClock.elapsedRealtime()
        isVoiceActive = false

        runOnUiThread {
            tvKeyIndicator.text = "Кнопка: ОТПУЩЕНА"
            tvKeyIndicator.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tvStatusBadge.text = "ДЕКОДИРОВАНИЕ..."
            tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))
        }

        log("Окончание удержания кнопки ($source)")
        audioCapture.stopCapture()

        sttEngine.finishUtterance(keyUpTimeMs) { metrics ->
            runOnUiThread {
                tvStatusBadge.text = "ЗАВЕРШЕНО"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))

                val recognized = if (metrics.finalTranscription.isNotEmpty()) {
                    metrics.finalTranscription
                } else {
                    "(Речь не распознана или тишина)"
                }

                tvRecognizedText.text = recognized
                tvRecognizedText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

                tvMetricLatency.text = "${metrics.postReleaseLatencyMs} ms"
                tvMetricRtf.text = "%.3f".format(metrics.rtf)
                tvMetricDuration.text = "${metrics.audioDurationMs} ms"
                tvMetricTotalTime.text = "${metrics.totalDecodeDurationMs} ms"
                tvMetricRam.text = "${"%.1f".format(metrics.peakRamMb)} MB"
                tvMetricCpu.text = "${metrics.cpuTimeMs} ms"

                val curPhrase = TestPhrases.items[currentPhraseIndex]
                log("════════════════════════════════════════")
                log("РЕЗУЛЬТАТ РАСПОЗНАВАНИЯ:")
                log("  Ожидалось:   '${curPhrase.phrase}'")
                log("  Распознано:  '$recognized'")
                log("  Post-Release Latency: ${metrics.postReleaseLatencyMs} ms")
                log("  Audio Duration:       ${metrics.audioDurationMs} ms")
                log("  Decode Duration:      ${metrics.totalDecodeDurationMs} ms")
                log("  Real-Time Factor:     ${"%.3f".format(metrics.rtf)}")
                log("  Peak RAM (PSS):       ${"%.1f".format(metrics.peakRamMb)} MB")
                log("  Process CPU Time:     ${metrics.cpuTimeMs} ms")
                log("════════════════════════════════════════")
            }
        }
    }

    private fun runWavBenchmark(assetPath: String, label: String) {
        log("Запуск теста WAV: $label ($assetPath)...")
        tvStatusBadge.text = "ДЕКОДИРОВАНИЕ WAV"
        tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_yellow))

        sttEngine.decodeWav(assetPath) { metrics ->
            runOnUiThread {
                tvStatusBadge.text = "WAV ТЕСТ ГОТОВ"
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_green))

                tvRecognizedText.text = metrics.finalTranscription
                tvRecognizedText.setTextColor(ContextCompat.getColor(this, R.color.accent_blue))

                tvMetricLatency.text = "${metrics.postReleaseLatencyMs} ms"
                tvMetricRtf.text = "%.3f".format(metrics.rtf)
                tvMetricDuration.text = "${metrics.audioDurationMs} ms"
                tvMetricTotalTime.text = "${metrics.totalDecodeDurationMs} ms"
                tvMetricRam.text = "${"%.1f".format(metrics.peakRamMb)} MB"
                tvMetricCpu.text = "${metrics.cpuTimeMs} ms"

                log("WAV $label: '${metrics.finalTranscription}' | RTF=${"%.3f".format(metrics.rtf)} | Decode=${metrics.totalDecodeDurationMs}ms | Audio=${metrics.audioDurationMs}ms | RAM=${"%.1f".format(metrics.peakRamMb)}MB")
            }
        }
    }

    // Direct hardware key fallback (if delivered directly to window)
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 231 || keyCode == KeyEvent.KEYCODE_SEARCH) {
            handleVoiceStart("Direct KeyEvent KEY_DOWN ($keyCode)")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val focused = currentFocus
            if (focused == btnHoldRecord) {
                handleVoiceStart("DPAD_CENTER Hold")
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == 231 || keyCode == KeyEvent.KEYCODE_SEARCH) {
            handleVoiceStop("Direct KeyEvent KEY_UP ($keyCode)")
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (isVoiceActive) {
                handleVoiceStop("DPAD_CENTER Release")
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun log(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        Log.i(TAG, message)
        runOnUiThread {
            tvLog.append("$line\n")
            scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(voiceKeyReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        audioCapture.stopCapture()
        sttEngine.release()
    }
}
