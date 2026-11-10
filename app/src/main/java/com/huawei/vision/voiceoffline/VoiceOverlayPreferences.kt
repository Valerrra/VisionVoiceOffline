package com.huawei.vision.voiceoffline

import android.content.Context
import android.content.SharedPreferences

class VoiceOverlayPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "vision_voice_overlay_prefs"
        private const val KEY_SERVICE_ENABLED = "overlay_service_enabled"
        private const val KEY_BOOT_AUTOSTART = "boot_autostart_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isBootAutostartEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_AUTOSTART, false)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_AUTOSTART, value).apply()
}
