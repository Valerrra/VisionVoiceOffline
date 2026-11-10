package com.huawei.vision.voiceoffline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = VoiceOverlayPreferences(context)
            Log.i(
                TAG,
                "BOOT_COMPLETED received. Autostart enabled=${prefs.isBootAutostartEnabled}, Service enabled=${prefs.isServiceEnabled}"
            )

            if (prefs.isBootAutostartEnabled && prefs.isServiceEnabled) {
                Log.i(TAG, "Starting VoiceOverlayService on boot...")
                VoiceOverlayService.start(context)
            } else {
                Log.i(TAG, "Boot autostart is disabled by user settings; doing nothing.")
            }
        }
    }
}
