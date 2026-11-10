package com.huawei.vision.voiceoffline

import android.content.Context
import android.content.SharedPreferences

class VoiceOverlayPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "vision_voice_overlay_prefs"
        private const val KEY_SERVICE_ENABLED = "overlay_service_enabled"
        private const val KEY_BOOT_AUTOSTART = "boot_autostart_enabled"
        private const val KEY_HISTORY_ENABLED = "history_logging_enabled"
        private const val KEY_OVERLAY_DURATION_SEC = "overlay_display_duration_sec"
        private const val KEY_OPERATING_MODE = "operating_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isBootAutostartEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_AUTOSTART, value).apply()

    var isHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_HISTORY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HISTORY_ENABLED, value).apply()

    var overlayDurationSec: Int
        get() = prefs.getInt(KEY_OVERLAY_DURATION_SEC, 3)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_DURATION_SEC, value.coerceIn(1, 10)).apply()

    var operatingMode: OperatingMode
        get() {
            val name = prefs.getString(KEY_OPERATING_MODE, OperatingMode.COMMAND.name)
            return try {
                OperatingMode.valueOf(name ?: OperatingMode.COMMAND.name)
            } catch (_: Exception) {
                OperatingMode.COMMAND
            }
        }
        set(value) = prefs.edit().putString(KEY_OPERATING_MODE, value.name).apply()
}
