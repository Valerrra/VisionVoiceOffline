package com.huawei.vision.voiceoffline

import android.content.Context
import android.util.Log
import com.huawei.vision.voiceoffline.search.DialogueManager
import com.huawei.vision.voiceoffline.search.SearchCoordinator

class CommandRouter(private val context: Context) {

    companion object {
        private const val TAG = "CommandRouter"
    }

    private val parser = CommandParser
    private val executor = CommandExecutor(context)
    private val prefs = VoiceOverlayPreferences(context)

    var currentMode: OperatingMode
        get() = prefs.operatingMode
        set(value) {
            prefs.operatingMode = value
        }

    fun route(rawText: String): CommandResult {
        Log.i(TAG, "Routing text: '$rawText' (Mode: $currentMode)")

        if (currentMode == OperatingMode.DICTATION) {
            val dictCmd = ParsedCommand(
                type = CommandType.DICTATION_TEXT,
                rawText = rawText,
                normalizedText = parser.normalize(rawText),
                action = "NONE",
                confidence = 1.0f,
                matchedRule = "dictation_mode_active"
            )
            return CommandResult(
                command = dictCmd,
                success = true,
                statusMessage = "Режим диктовки: выполнение команд отключено",
                executedAction = "NONE"
            )
        }

        // 1. Check if DialogueManager has an active dialogue session awaiting target
        if (DialogueManager.hasActiveSession(prefs.dialogueTimeoutSeconds)) {
            val dialogueRes = SearchCoordinator.handleDialogueInput(rawText, context)
            if (dialogueRes != null) {
                Log.i(TAG, "DialogueManager resolved input: '${dialogueRes.statusMessage}'")
                return dialogueRes
            }
        }

        // 2. Parse and execute standard command
        val parsed = parser.parse(rawText)
        Log.i(TAG, "Parsed: type=${parsed.type}, action=${parsed.action}, target=${parsed.target}, rule=${parsed.matchedRule}")
        return executor.execute(parsed)
    }

    fun parseOnly(rawText: String): ParsedCommand {
        return parser.parse(rawText)
    }
}
