package com.huawei.vision.voiceoffline

import android.content.Context
import android.util.Log
import com.huawei.vision.voiceoffline.search.DialogueManager
import com.huawei.vision.voiceoffline.search.SearchCoordinator
import com.huawei.vision.voiceoffline.search.SearchQueryParser

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

        // Fast parse to check for independent volume / navigation / media commands
        val fastParsed = parser.parse(rawText)
        val isIndependentControl = fastParsed.type == CommandType.VOLUME_CONTROL ||
                fastParsed.type == CommandType.SYSTEM_NAV ||
                fastParsed.type == CommandType.MEDIA_CONTROL

        // 1. If user gave independent control command during active dialogue, cancel waiting prompt and execute control
        if (isIndependentControl) {
            if (DialogueManager.hasActiveSession(prefs.dialogueTimeoutSeconds)) {
                Log.i(TAG, "Independent control command '${fastParsed.type}' interrupts waiting dialogue")
                DialogueManager.resetSession()
            }
            return executor.execute(fastParsed)
        }

        // 2. Active dialogue turn (Awaiting confirmation or Awaiting target)
        if (DialogueManager.hasActiveSession(prefs.dialogueTimeoutSeconds)) {
            val dialogueRes = SearchCoordinator.handleDialogueInput(rawText, context)
            if (dialogueRes != null) {
                Log.i(TAG, "DialogueManager resolved active turn: '${dialogueRes.statusMessage}'")
                return dialogueRes
            }
        }

        // 3. Contextual Follow-Up (Repeat in another app or Query Refinement within memory timeout)
        val prevQuery = DialogueManager.dialogueContext.currentQuery.ifEmpty { DialogueManager.dialogueContext.originalQuery }
        val repeatIntent = SearchQueryParser.detectRepeatIntent(rawText)
        val refinementIntent = SearchQueryParser.detectRefinement(rawText, prevQuery)

        if (repeatIntent.isRepeat || refinementIntent.isRefinement) {
            val dialogueRes = SearchCoordinator.handleDialogueInput(rawText, context)
            if (dialogueRes != null) {
                Log.i(TAG, "Contextual search follow-up handled: '${dialogueRes.statusMessage}'")
                return dialogueRes
            }
        }

        // 4. Standard command execution
        Log.i(TAG, "Standard routing for: type=${fastParsed.type}, action=${fastParsed.action}, target=${fastParsed.target}")
        return executor.execute(fastParsed)
    }

    fun parseOnly(rawText: String): ParsedCommand {
        return parser.parse(rawText)
    }
}
