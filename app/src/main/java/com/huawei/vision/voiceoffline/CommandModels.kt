package com.huawei.vision.voiceoffline

enum class OperatingMode {
    COMMAND,
    DICTATION
}

enum class CommandType {
    OPEN_APP,
    VOLUME_CONTROL,
    MEDIA_CONTROL,
    SEARCH,
    SYSTEM_NAV,
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

data class AppAlias(
    val key: String,
    val displayName: String,
    val aliases: List<String>,
    val candidatePackages: List<String>,
    val fallbackAction: String? = null
)
