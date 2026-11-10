package com.huawei.vision.voiceoffline.search

import android.content.Context
import android.util.Log
import com.huawei.vision.voiceoffline.CommandResult
import com.huawei.vision.voiceoffline.ParsedCommand

object SearchCoordinator {

    private const val TAG = "SearchCoordinator"

    fun processSearchCommand(cmd: ParsedCommand, context: Context): CommandResult {
        val rawQuery = (cmd.params["query"] as? String) ?: cmd.rawText
        val resolved = SearchTargetResolver.resolve(cmd.rawText)

        val targetAppKey = resolved.targetAppKey ?: (cmd.params["targetAppKey"] as? String)
        val cleanQuery = if (resolved.cleanQuery.isNotEmpty()) resolved.cleanQuery else rawQuery

        Log.i(TAG, "Processing search: target=$targetAppKey, cleanQuery='$cleanQuery', raw='${cmd.rawText}'")

        // 1. Explicit target specified
        if (!targetAppKey.isNullOrEmpty()) {
            val adapter = SearchAdapterRegistry.getAdapter(targetAppKey)
            if (adapter != null) {
                if (adapter.isAvailable(context)) {
                    Log.i(TAG, "Direct search execution in ${adapter.displayName} for '$cleanQuery'")
                    val execRes = adapter.executeSearch(context, cleanQuery)
                    return CommandResult(
                        command = cmd,
                        success = execRes.success,
                        statusMessage = execRes.statusMessage,
                        executedAction = "DIRECT_SEARCH_${targetAppKey.uppercase()}",
                        targetPackage = adapter.candidatePackages.firstOrNull(),
                        error = execRes.error?.name
                    )
                } else {
                    Log.w(TAG, "Explicit search target '$targetAppKey' is not installed")
                    // Start dialogue suggesting installed alternatives, retaining the user query
                    val dialogueRes = DialogueManager.startSearchDialogue(cleanQuery, cmd.rawText, context)
                    val disp = resolved.targetDisplayName ?: targetAppKey
                    val statusMsg = "$disp не установлен. ${dialogueRes.statusMessage}"
                    return CommandResult(
                        command = cmd,
                        success = false,
                        statusMessage = statusMsg,
                        executedAction = "SEARCH_TARGET_NOT_INSTALLED",
                        error = "APP_NOT_INSTALLED"
                    )
                }
            } else {
                Log.w(TAG, "No search adapter for target '$targetAppKey'")
                val dialogueRes = DialogueManager.startSearchDialogue(cleanQuery, cmd.rawText, context)
                return CommandResult(
                    command = cmd,
                    success = false,
                    statusMessage = dialogueRes.statusMessage,
                    executedAction = "SEARCH_ADAPTER_UNSUPPORTED",
                    error = "ADAPTER_NOT_FOUND"
                )
            }
        }

        // 2. Ambiguous target -> Contextual Dialogue
        Log.i(TAG, "Ambiguous search target for '$cleanQuery'; initiating dialogue")
        val dialogueRes = DialogueManager.startSearchDialogue(cleanQuery, cmd.rawText, context)

        if (dialogueRes.isActionComplete && dialogueRes.executionResult != null) {
            val exec = dialogueRes.executionResult
            return CommandResult(
                command = cmd,
                success = exec.success,
                statusMessage = exec.statusMessage,
                executedAction = "POLICY_AUTO_SEARCH_${exec.targetApp.uppercase()}"
            )
        }

        return CommandResult(
            command = cmd,
            success = true,
            statusMessage = dialogueRes.statusMessage,
            executedAction = "DIALOGUE_AWAITING_TARGET"
        )
    }

    fun handleDialogueInput(rawText: String, context: Context): CommandResult? {
        val dialogueRes = DialogueManager.handleInput(rawText, context)
        if (!dialogueRes.handled) {
            return null
        }

        return if (dialogueRes.isActionComplete) {
            val success = dialogueRes.state == DialogueState.SEARCH_COMPLETED || dialogueRes.state == DialogueState.CANCELLED
            CommandResult(
                command = ParsedCommand(
                    type = com.huawei.vision.voiceoffline.CommandType.SEARCH,
                    rawText = rawText,
                    normalizedText = SearchTargetResolver.normalize(rawText),
                    action = dialogueRes.state.name,
                    confidence = 1.0f,
                    matchedRule = "dialogue_completed"
                ),
                success = success,
                statusMessage = dialogueRes.statusMessage,
                executedAction = "DIALOGUE_${dialogueRes.state.name}"
            )
        } else {
            CommandResult(
                command = ParsedCommand(
                    type = com.huawei.vision.voiceoffline.CommandType.SEARCH,
                    rawText = rawText,
                    normalizedText = SearchTargetResolver.normalize(rawText),
                    action = "AWAITING_SEARCH_TARGET",
                    confidence = 1.0f,
                    matchedRule = "dialogue_clarification"
                ),
                success = true,
                statusMessage = dialogueRes.statusMessage,
                executedAction = "DIALOGUE_PROMPT"
            )
        }
    }
}
