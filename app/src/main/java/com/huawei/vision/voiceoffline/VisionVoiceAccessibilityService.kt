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
        var currentActivePackage: String? = null
            internal set

        @Volatile
        private var testActionHandler: ((Int) -> Boolean)? = null

        val isRunning: Boolean
            get() = instance != null || testActionHandler != null

        fun getActivePackage(context: Context): String? {
            // 1. Direct cached from recent AccessibilityEvents
            val cached = currentActivePackage
            if (!cached.isNullOrEmpty() && cached != context.packageName) {
                return cached
            }

            // 2. Query active interactive windows from AccessibilityService
            val service = instance
            if (service != null) {
                try {
                    val winList = service.windows
                    for (win in winList) {
                        if (win.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION &&
                            (win.isFocused || win.isActive)
                        ) {
                            val title = win.title?.toString()
                            if (!title.isNullOrEmpty() && title != context.packageName) {
                                // Match title to registered search adapters (e.g. "YouTube", "LazyMedia Deluxe")
                                val match = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.getAllAdapters().firstOrNull {
                                    title.contains(it.displayName, ignoreCase = true) ||
                                            it.displayName.contains(title, ignoreCase = true) ||
                                            it.candidatePackages.any { pkg -> title.contains(pkg, ignoreCase = true) }
                                }
                                if (match != null) {
                                    val pkg = match.candidatePackages.firstOrNull() ?: match.targetAppKey
                                    currentActivePackage = pkg
                                    try {
                                        VoiceOverlayPreferences(context).lastActivePackage = pkg
                                    } catch (_: Exception) {}
                                    Log.i(TAG, "Resolved active package '$pkg' from window title '$title'")
                                    return pkg
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error inspecting windows in getActivePackage", e)
                }
            }

            // 3. Fallback to ActivityManager getRunningAppProcesses
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val procs = am?.runningAppProcesses
                if (procs != null) {
                    for (proc in procs) {
                        if (proc.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                            proc.processName != context.packageName
                        ) {
                            val pkg = proc.pkgList?.firstOrNull() ?: proc.processName
                            if (pkg != context.packageName && !pkg.contains("launcher") && !pkg.contains("systemui")) {
                                if (com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.getAdapterForPackage(pkg) != null) {
                                    currentActivePackage = pkg
                                    try {
                                        VoiceOverlayPreferences(context).lastActivePackage = pkg
                                    } catch (_: Exception) {}
                                    Log.i(TAG, "Resolved active package '$pkg' from runningAppProcesses")
                                    return pkg
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            return VoiceOverlayPreferences(context).lastActivePackage
        }

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
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
            Log.i(TAG, "VisionVoiceAccessibilityService onServiceConnected: dynamic eventTypes and window flags applied")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying dynamic serviceInfo in onServiceConnected", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        Log.i(TAG, "VisionVoiceAccessibilityService onDestroy")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Safe foreground package tracking for contextual search (no screen/window content inspection)
        if (event == null) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != packageName && !pkg.contains("launcher") && !pkg.contains("systemui")) {
                currentActivePackage = pkg
                try {
                    VoiceOverlayPreferences(applicationContext).lastActivePackage = pkg
                } catch (_: Exception) {
                }
                Log.d(TAG, "AccessibilityEvent captured active package: $pkg")
            }
        }
    }

    override fun onInterrupt() {
        // NO-OP
    }
}
