package com.huawei.vision.voiceoffline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private lateinit var prefs: VoiceOverlayPreferences
    private lateinit var overlayWindowManager: VoiceOverlayWindowManager

    private lateinit var tvModeTitle: TextView
    private lateinit var tvModeDesc: TextView
    private lateinit var btnToggleMode: Button

    private lateinit var tvServiceTitle: TextView
    private lateinit var btnToggleService: Button

    private lateinit var tvOverlayPermDesc: TextView
    private lateinit var btnOverlayPerm: Button

    private lateinit var btnDurationCycle: Button
    private lateinit var btnToggleAutostart: Button

    private lateinit var btnNavMyApps: Button
    private lateinit var btnNavHistory: Button
    private lateinit var btnNavDiagnostic: Button
    private lateinit var btnBackFromSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = VoiceOverlayPreferences(this)
        overlayWindowManager = VoiceOverlayWindowManager(this)

        initViews()
        updateAllViews()
    }

    override fun onResume() {
        super.onResume()
        updateAllViews()
    }

    private fun initViews() {
        tvModeTitle = findViewById(R.id.tvModeTitle)
        tvModeDesc = findViewById(R.id.tvModeDesc)
        btnToggleMode = findViewById(R.id.btnToggleMode)

        tvServiceTitle = findViewById(R.id.tvServiceTitle)
        btnToggleService = findViewById(R.id.btnToggleService)

        tvOverlayPermDesc = findViewById(R.id.tvOverlayPermDesc)
        btnOverlayPerm = findViewById(R.id.btnOverlayPerm)

        btnDurationCycle = findViewById(R.id.btnDurationCycle)
        btnToggleAutostart = findViewById(R.id.btnToggleAutostart)

        btnNavMyApps = findViewById(R.id.btnNavMyApps)
        btnNavHistory = findViewById(R.id.btnNavHistory)
        btnNavDiagnostic = findViewById(R.id.btnNavDiagnostic)
        btnBackFromSettings = findViewById(R.id.btnBackFromSettings)

        btnToggleMode.setOnClickListener {
            val newMode = if (prefs.operatingMode == OperatingMode.COMMAND) {
                OperatingMode.DICTATION
            } else {
                OperatingMode.COMMAND
            }
            prefs.operatingMode = newMode
            updateModeViews()
            Toast.makeText(this, "Режим изменен: ${newMode.name}", Toast.LENGTH_SHORT).show()
        }

        btnToggleService.setOnClickListener {
            if (VoiceOverlayService.isRunning) {
                VoiceOverlayService.stop(this)
                prefs.isServiceEnabled = false
                Toast.makeText(this, "Фоновый сервис остановлен", Toast.LENGTH_SHORT).show()
            } else {
                VoiceOverlayService.start(this)
                prefs.isServiceEnabled = true
                Toast.makeText(this, "Фоновый сервис запущен", Toast.LENGTH_SHORT).show()
            }
            btnToggleService.postDelayed({ updateServiceViews() }, 400)
        }

        btnOverlayPerm.setOnClickListener {
            if (!overlayWindowManager.canDrawOverlays()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Разрешение уже предоставлено", Toast.LENGTH_SHORT).show()
            }
        }

        btnDurationCycle.setOnClickListener {
            val nextDuration = when (prefs.overlayDurationSec) {
                2 -> 3
                3 -> 5
                else -> 2
            }
            prefs.overlayDurationSec = nextDuration
            updateDurationView()
        }

        btnToggleAutostart.setOnClickListener {
            prefs.isBootAutostartEnabled = !prefs.isBootAutostartEnabled
            updateAutostartView()
        }

        btnNavMyApps.setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        btnNavHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        btnNavDiagnostic.setOnClickListener {
            startActivity(Intent(this, DiagnosticActivity::class.java))
        }

        btnBackFromSettings.setOnClickListener {
            finish()
        }

        btnToggleMode.requestFocus()
    }

    private fun updateAllViews() {
        updateModeViews()
        updateServiceViews()
        updateOverlayPermViews()
        updateDurationView()
        updateAutostartView()
    }

    private fun updateModeViews() {
        val isCommand = prefs.operatingMode == OperatingMode.COMMAND
        tvModeTitle.text = if (isCommand) {
            "Режим: Управление командами (COMMAND)"
        } else {
            "Режим: Диктовка текста (DICTATION)"
        }
        tvModeDesc.text = if (isCommand) {
            "Запуск приложений, регулировка громкости, навигация назад и поиск"
        } else {
            "Транскрибирование речи в чистый текст без исполнения команд"
        }
        btnToggleMode.text = if (isCommand) "Переключить на ДИКТОВКУ" else "Переключить на КОМАНДЫ"
    }

    private fun updateServiceViews() {
        val isRunning = VoiceOverlayService.isRunning
        btnToggleService.text = if (isRunning) "РАБОТАЕТ (ВКЛ)" else "ОСТАНОВЛЕН (ВЫКЛ)"
        btnToggleService.setTextColor(
            ContextCompat.getColor(this, if (isRunning) R.color.accent_green else R.color.text_secondary)
        )
    }

    private fun updateOverlayPermViews() {
        val hasPerm = overlayWindowManager.canDrawOverlays()
        if (hasPerm) {
            btnOverlayPerm.text = "✓ ПРЕДОСТАВЛЕНО"
            btnOverlayPerm.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            tvOverlayPermDesc.text = "Разрешение активно. Нижняя голосовая панель доступна поверх всех приложений."
        } else {
            btnOverlayPerm.text = "ПРЕДОСТАВИТЬ"
            btnOverlayPerm.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            tvOverlayPermDesc.text = "Разрешение отсутствует! Нажмите кнопку для открытия системных настроек."
        }
    }

    private fun updateDurationView() {
        btnDurationCycle.text = "${prefs.overlayDurationSec} сек."
    }

    private fun updateAutostartView() {
        val isAutostart = prefs.isBootAutostartEnabled
        btnToggleAutostart.text = if (isAutostart) "ВКЛЮЧЕН" else "ВЫКЛЮЧЕН"
        btnToggleAutostart.setTextColor(
            ContextCompat.getColor(this, if (isAutostart) R.color.brand_cyan else R.color.text_secondary)
        )
    }
}
