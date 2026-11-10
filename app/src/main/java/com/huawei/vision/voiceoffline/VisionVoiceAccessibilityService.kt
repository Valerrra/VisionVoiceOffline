package com.huawei.vision.voiceoffline

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class VisionVoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VisionVoiceA11y"

        @Volatile
        private var instance: VisionVoiceAccessibilityService? = null

        @Volatile
        private var testActionHandler: ((Int) -> Boolean)? = null

        val isRunning: Boolean
            get() = instance != null || testActionHandler != null

        fun isServiceEnabled(context: Context): Boolean {
            if (isRunning) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedPkg = context.packageName
            val expectedClass = VisionVoiceAccessibilityService::class.java.name
            return enabledServices.any {
                val serviceInfo = it.resolveInfo?.serviceInfo
                serviceInfo != null && serviceInfo.packageName == expectedPkg && serviceInfo.name == expectedClass
            }
        }

        fun performHome(): Boolean {
            testActionHandler?.let { return it(GLOBAL_ACTION_HOME) }
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot perform GLOBAL_ACTION_HOME: AccessibilityService is not running")
                return false
            }
            val success = service.performGlobalAction(GLOBAL_ACTION_HOME)
            Log.i(TAG, "GLOBAL_ACTION_HOME result: $success")
            return success
        }

        fun performBack(): Boolean {
            testActionHandler?.let { return it(GLOBAL_ACTION_BACK) }
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot perform GLOBAL_ACTION_BACK: AccessibilityService is not running")
                return false
            }
            val success = service.performGlobalAction(GLOBAL_ACTION_BACK)
            Log.i(TAG, "GLOBAL_ACTION_BACK result: $success")
            return success
        }

        fun performRecents(): Boolean {
            testActionHandler?.let { return it(GLOBAL_ACTION_RECENTS) }
            val service = instance
            if (service == null) {
                Log.w(TAG, "Cannot perform GLOBAL_ACTION_RECENTS: AccessibilityService is not running")
                return false
            }
            val success = service.performGlobalAction(GLOBAL_ACTION_RECENTS)
            Log.i(TAG, "GLOBAL_ACTION_RECENTS result: $success")
            return success
        }

        // Test hooks for Unit Testing
        internal fun setTestActionHandler(handler: ((Int) -> Boolean)?) {
            testActionHandler = handler
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "VisionVoiceAccessibilityService onServiceConnected: navigation controls ready")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "VisionVoiceAccessibilityService onDestroy")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Strictly empty / NO-OP: do NOT inspect or process screen content, text, or windows
    }

    override fun onInterrupt() {
        // NO-OP
    }
}
