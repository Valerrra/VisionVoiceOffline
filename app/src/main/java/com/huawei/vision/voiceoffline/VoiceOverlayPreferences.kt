package com.huawei.vision.voiceoffline

import android.content.Context
import android.content.SharedPreferences
import com.huawei.vision.voiceoffline.search.SearchPolicy

class VoiceOverlayPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "vision_voice_overlay_prefs"
        private const val KEY_SERVICE_ENABLED = "overlay_service_enabled"
        private const val KEY_BOOT_AUTOSTART = "boot_autostart_enabled"
        private const val KEY_HISTORY_ENABLED = "history_logging_enabled"
        private const val KEY_OVERLAY_DURATION_SEC = "overlay_display_duration_sec"
        private const val KEY_OPERATING_MODE = "operating_mode"
        private const val KEY_SEARCH_POLICY = "search_source_policy"
        private const val KEY_DIALOGUE_TIMEOUT_SEC = "dialogue_timeout_sec"
        private const val KEY_LAST_SEARCH_MEMORY_TIMEOUT_SEC = "last_search_memory_timeout_sec"
        private const val KEY_DEFAULT_SEARCH_APP = "default_search_app"
        private const val KEY_LAST_ACTIVE_PKG = "last_active_package"
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

    var searchPolicy: SearchPolicy
        get() {
            val name = prefs.getString(KEY_SEARCH_POLICY, SearchPolicy.PREFER_ACTIVE.name)
            return try {
                SearchPolicy.valueOf(name ?: SearchPolicy.PREFER_ACTIVE.name)
            } catch (_: Exception) {
                SearchPolicy.PREFER_ACTIVE
            }
        }
        set(value) = prefs.edit().putString(KEY_SEARCH_POLICY, value.name).apply()

    var dialogueTimeoutSeconds: Int
        get() = prefs.getInt(KEY_DIALOGUE_TIMEOUT_SEC, 30)
        set(value) = prefs.edit().putInt(KEY_DIALOGUE_TIMEOUT_SEC, value.coerceIn(10, 120)).apply()

    var lastSearchMemoryTimeoutSeconds: Int
        get() = prefs.getInt(KEY_LAST_SEARCH_MEMORY_TIMEOUT_SEC, 120)
        set(value) = prefs.edit().putInt(KEY_LAST_SEARCH_MEMORY_TIMEOUT_SEC, value.coerceIn(30, 600)).apply()

    var defaultSearchApp: String
        get() = prefs.getString(KEY_DEFAULT_SEARCH_APP, "lazymedia") ?: "lazymedia"
        set(value) = prefs.edit().putString(KEY_DEFAULT_SEARCH_APP, value).apply()

    var lastActivePackage: String?
        get() = prefs.getString(KEY_LAST_ACTIVE_PKG, null)
        set(value) = prefs.edit().putString(KEY_LAST_ACTIVE_PKG, value).apply()
}
