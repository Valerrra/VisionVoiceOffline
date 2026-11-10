package com.huawei.vision.voiceoffline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), ServiceTelemetryListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQ_RECORD_AUDIO = 1001
    }

    private lateinit var prefs: VoiceOverlayPreferences
    private lateinit var historyManager: CommandHistoryManager

    // Header views
    private lateinit var ivHeaderLogo: ImageView
    private lateinit var tvHeaderServiceBadge: TextView
    private lateinit var tvHeaderOverlayBadge: TextView
    private lateinit var tvHeaderModeBadge: TextView

    // Hero Card views
    private lateinit var heroCard: LinearLayout
    private lateinit var tvHeroStatePill: TextView
    private lateinit var tvHeroTime: TextView
    private lateinit var tvHeroCommand: TextView
    private lateinit var layoutHeroResult: LinearLayout
    private lateinit var tvHeroIntentBadge: TextView
    private lateinit var tvHeroStatus: TextView
    private lateinit var tvHeroExecTime: TextView

    // Navigation cards
    private lateinit var cardVoiceInput: LinearLayout
    private lateinit var tvVoiceInputHint: TextView
    private lateinit var cardMyApps: LinearLayout
    private lateinit var tvMyAppsCount: TextView
    private lateinit var cardHistory: LinearLayout
    private lateinit var tvHistoryCount: TextView
    private lateinit var cardSettings: LinearLayout
    private lateinit var btnDiagnostic: Button

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = VoiceOverlayPreferences(this)
        historyManager = CommandHistoryManager(this)
        AppRegistry.init(this)

        initViews()
        checkAudioPermission()

        // Ensure background service is running for global PTT handling
        if (!VoiceOverlayService.isRunning) {
            VoiceOverlayService.start(this)
            prefs.isServiceEnabled = true
        }
    }

    private fun initViews() {
        ivHeaderLogo = findViewById(R.id.ivHeaderLogo)
        tvHeaderServiceBadge = findViewById(R.id.tvHeaderServiceBadge)
        tvHeaderOverlayBadge = findViewById(R.id.tvHeaderOverlayBadge)
        tvHeaderModeBadge = findViewById(R.id.tvHeaderModeBadge)

        heroCard = findViewById(R.id.heroCard)
        tvHeroStatePill = findViewById(R.id.tvHeroStatePill)
        tvHeroTime = findViewById(R.id.tvHeroTime)
        tvHeroCommand = findViewById(R.id.tvHeroCommand)
        layoutHeroResult = findViewById(R.id.layoutHeroResult)
        tvHeroIntentBadge = findViewById(R.id.tvHeroIntentBadge)
        tvHeroStatus = findViewById(R.id.tvHeroStatus)
        tvHeroExecTime = findViewById(R.id.tvHeroExecTime)

        cardVoiceInput = findViewById(R.id.cardVoiceInput)
        tvVoiceInputHint = findViewById(R.id.tvVoiceInputHint)
        cardMyApps = findViewById(R.id.cardMyApps)
        tvMyAppsCount = findViewById(R.id.tvMyAppsCount)
        cardHistory = findViewById(R.id.cardHistory)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        cardSettings = findViewById(R.id.cardSettings)
        btnDiagnostic = findViewById(R.id.btnDiagnostic)

        // Setup Card Listeners
        cardVoiceInput.setOnClickListener {
            handleVoiceCardClick()
        }

        cardMyApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        cardHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnDiagnostic.setOnClickListener {
            startActivity(Intent(this, DiagnosticActivity::class.java))
        }

        // Initial focus on first card for 10-foot TV D-pad experience
        cardVoiceInput.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateHeaderBadges()
        updateHeroCardFromHistory()
        updateSummaryCounts()

        // Attach telemetry listener to receive live updates from service
        VoiceOverlayService.telemetryListener = this
    }

    override fun onPause() {
        super.onPause()
        if (VoiceOverlayService.telemetryListener === this) {
            VoiceOverlayService.telemetryListener = null
        }
    }

    private fun updateHeaderBadges() {
        val serviceRunning = VoiceOverlayService.isRunning
        tvHeaderServiceBadge.text = if (serviceRunning) "СЕРВИС: РАБОТАЕТ" else "СЕРВИС: ОСТАНОВЛЕН"
        tvHeaderServiceBadge.setTextColor(
            ContextCompat.getColor(this, if (serviceRunning) R.color.accent_green else R.color.text_secondary)
        )

        val overlayPerm = VoiceOverlayWindowManager(this).canDrawOverlays()
        tvHeaderOverlayBadge.text = if (overlayPerm) "ОВЕРЛЕЙ: ДОСТУПЕН" else "ОВЕРЛЕЙ: НЕТ ПРАВ"
        tvHeaderOverlayBadge.setTextColor(
            ContextCompat.getColor(this, if (overlayPerm) R.color.brand_cyan else R.color.accent_yellow)
        )

        tvHeaderModeBadge.text = if (prefs.operatingMode == OperatingMode.COMMAND) {
            "РЕЖИМ: КОМАНДЫ"
        } else {
            "РЕЖИМ: ДИКТОВКА"
        }
    }

    private fun updateSummaryCounts() {
        val apps = AppRegistry.getAllApps()
        tvMyAppsCount.text = "${apps.size} установл. приложений"

        val history = historyManager.getHistory()
        tvHistoryCount.text = "${history.size} записей в журнале"
    }

    private fun updateHeroCardFromHistory() {
        val latest = historyManager.getHistory().firstOrNull()
        if (latest != null) {
            displayHeroEntry(latest)
        } else {
            tvHeroStatePill.text = "ГОТОВ К ПРИЕМУ"
            tvHeroStatePill.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvHeroTime.text = "—"
            tvHeroCommand.text = "Удерживайте кнопку микрофона на пульте и произнесите команду"
            tvHeroIntentBadge.text = "IDLE"
            tvHeroStatus.text = "Ожидание нажатия кнопки микрофона на пульте HDRC-BV1..."
            tvHeroExecTime.text = "0 ms"
        }
    }

    private fun displayHeroEntry(entry: HistoryEntry) {
        tvHeroStatePill.text = if (entry.success) "ВЫПОЛНЕНО" else "ОТКЛОНЕНО"
        tvHeroStatePill.setTextColor(
            ContextCompat.getColor(this, if (entry.success) R.color.accent_green else R.color.accent_red)
        )

        val dateStr = try {
            timeFormat.format(Date(entry.timestampMs))
        } catch (_: Exception) {
            "—"
        }
        tvHeroTime.text = dateStr

        tvHeroCommand.text = if (entry.rawText.isNotEmpty()) "«${entry.rawText}»" else "(Речь не распознана)"
        tvHeroIntentBadge.text = entry.intent
        tvHeroStatus.text = entry.statusMessage
        tvHeroExecTime.text = "${entry.executionTimeMs} ms"
    }

    private fun handleVoiceCardClick() {
        if (!VoiceOverlayService.isRunning) {
            Toast.makeText(this, "Запуск фонового сервиса...", Toast.LENGTH_SHORT).show()
            VoiceOverlayService.start(this)
            prefs.isServiceEnabled = true
            updateHeaderBadges()
        }
        Toast.makeText(
            this,
            "Для голосового ввода удерживайте кнопку с микрофоном на пульте HDRC-BV1",
            Toast.LENGTH_LONG
        ).show()
    }

    // --- ServiceTelemetryListener Implementation ---
    override fun onSessionStarted(source: String) {
        runOnUiThread {
            tvHeroStatePill.text = "СЛУШАЮ..."
            tvHeroStatePill.setTextColor(ContextCompat.getColor(this, R.color.brand_cyan))
            tvHeroCommand.text = "Говорите в пульт..."
            tvHeroStatus.text = "Идет запись звука с микрофона ($source)..."
        }
    }

    override fun onSessionFinished(metrics: SttMetrics, result: CommandResult?) {
        runOnUiThread {
            updateSummaryCounts()
            if (result != null) {
                val isSuccess = result.success
                tvHeroStatePill.text = if (isSuccess) "ВЫПОЛНЕНО" else "ОТКЛОНЕНО"
                tvHeroStatePill.setTextColor(
                    ContextCompat.getColor(this, if (isSuccess) R.color.accent_green else R.color.accent_red)
                )
                tvHeroCommand.text = if (metrics.finalTranscription.isNotEmpty()) "«${metrics.finalTranscription}»" else "(Речь не распознана)"
                tvHeroIntentBadge.text = result.command.type.name
                tvHeroStatus.text = result.statusMessage
                tvHeroExecTime.text = "${metrics.postReleaseLatencyMs} ms"
                tvHeroTime.text = timeFormat.format(Date())
            }
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            tvHeroStatePill.text = "ОШИБКА"
            tvHeroStatePill.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            tvHeroStatus.text = error
        }
    }

    override fun onModelReady(ready: Boolean) {
        runOnUiThread {
            updateHeaderBadges()
        }
    }

    // --- Key Events ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVoiceKeyCode(keyCode)) {
            Log.d(TAG, "onKeyDown voice key $keyCode")
            if (!VoiceOverlayService.isRunning) {
                VoiceOverlayService.start(this)
                prefs.isServiceEnabled = true
                updateHeaderBadges()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVoiceKeyCode(keyCode)) {
            Log.d(TAG, "onKeyUp voice key $keyCode")
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVoiceKeyCode(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == 231 ||
                keyCode == 232 ||
                keyCode == 130
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQ_RECORD_AUDIO)
        }
    }
}
