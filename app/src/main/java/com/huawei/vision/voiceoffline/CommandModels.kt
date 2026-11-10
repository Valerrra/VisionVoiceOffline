package com.huawei.vision.voiceoffline

enum class OperatingMode {
    COMMAND,
    DICTATION
}

enum class CommandType {
    OPEN_APP,
    CLOSE_APP,
    MINIMIZE_APP,
    SYSTEM_BACK,
    SYSTEM_NAV,
    VOLUME_CONTROL,
    MEDIA_CONTROL,
    SEARCH,
    DICTATION_TEXT,
    UNKNOWN
}

data class ParsedCommand(
    val type: CommandType,
    val rawText: String,
    val normalizedText: String,
    val action: String,
    val target: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    val confidence: Float = 1.0f,
    val matchedRule: String = "default"
)

data class CommandResult(
    val command: ParsedCommand,
    val success: Boolean,
    val statusMessage: String,
    val executedAction: String? = null,
    val targetPackage: String? = null,
    val error: String? = null,
    val executionTimeMs: Long = 0
)

data class InstalledApp(
    val key: String,
    val packageName: String,
    val activityName: String?,
    val appLabel: String,
    val aliases: Set<String>,
    val isSystemApp: Boolean = false,
    val isUserCustom: Boolean = false,
    val fallbackAction: String? = null
) {
    val displayName: String get() = appLabel
}

data class AppMatch(
    val app: InstalledApp,
    val matchedAlias: String,
    val score: Float,
    val isAmbiguous: Boolean = false
)
