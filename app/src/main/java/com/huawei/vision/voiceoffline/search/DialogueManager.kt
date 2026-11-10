package com.huawei.vision.voiceoffline.search

import android.content.Context
import android.util.Log
import com.huawei.vision.voiceoffline.VoiceOverlayPreferences

object DialogueManager {

    private const val TAG = "DialogueManager"

    var state: DialogueState = DialogueState.IDLE
        private set

    var activeSession: SearchSession? = null
        private set

    fun hasActiveSession(timeoutSeconds: Int): Boolean {
        val session = activeSession ?: return false
        if (session.isExpired(timeoutSeconds)) {
            Log.d(TAG, "Search dialogue session expired (timeout: ${timeoutSeconds}s)")
            resetSession()
            return false
        }
        return state == DialogueState.AWAITING_SEARCH_TARGET
    }

    fun startSearchDialogue(query: String, rawText: String, context: Context): DialogueResult {
        val prefs = VoiceOverlayPreferences(context)
        val availableAdapters = SearchAdapterRegistry.getAvailableAdapters(context)

        if (availableAdapters.isEmpty()) {
            state = DialogueState.ERROR
            return DialogueResult(
                handled = true,
                state = DialogueState.ERROR,
                statusMessage = "Нет установленных приложений с поддержкой поиска",
                isActionComplete = false
            )
        }

        // Check configured search policy
        when (prefs.searchPolicy) {
            SearchPolicy.DEFAULT_APP -> {
                val defaultKey = prefs.defaultSearchApp
                val defaultAdapter = availableAdapters.firstOrNull { it.targetAppKey == defaultKey }
                    ?: availableAdapters.first()
                Log.i(TAG, "Search policy DEFAULT_APP: using ${defaultAdapter.displayName}")
                val execRes = defaultAdapter.executeSearch(context, query)
                state = DialogueState.SEARCH_COMPLETED
                return DialogueResult(
                    handled = true,
                    state = DialogueState.SEARCH_COMPLETED,
                    statusMessage = execRes.statusMessage,
                    isActionComplete = true,
                    executionResult = execRes
                )
            }
            SearchPolicy.PREFER_ACTIVE -> {
                val activePkg = prefs.lastActivePackage
                if (!activePkg.isNullOrEmpty()) {
                    val activeAdapter = SearchAdapterRegistry.getAdapterForPackage(activePkg)
                    if (activeAdapter != null && activeAdapter.isAvailable(context)) {
                        Log.i(TAG, "Search policy PREFER_ACTIVE: using active app ${activeAdapter.displayName}")
                        val execRes = activeAdapter.executeSearch(context, query)
                        state = DialogueState.SEARCH_COMPLETED
                        return DialogueResult(
                            handled = true,
                            state = DialogueState.SEARCH_COMPLETED,
                            statusMessage = execRes.statusMessage,
                            isActionComplete = true,
                            executionResult = execRes
                        )
                    }
                }
                // Fallback to asking user
            }
            SearchPolicy.ASK_ALWAYS -> {
                // Proceed to dialogue prompt
            }
        }

        // Dialogue prompt
        val options = availableAdapters.map { it.displayName }
        val prompt = "Где искать «$query»?"
        val session = SearchSession(
            query = query,
            rawText = rawText,
            prompt = prompt,
            availableOptions = options,
            createdAt = System.currentTimeMillis()
        )
        activeSession = session
        state = DialogueState.AWAITING_SEARCH_TARGET

        Log.i(TAG, "Started search dialogue: '$prompt' Options: $options")
        return DialogueResult(
            handled = true,
            state = DialogueState.AWAITING_SEARCH_TARGET,
            statusMessage = "$prompt (Варианты: ${options.joinToString(", ")})",
            promptToUser = prompt,
            availableOptions = options,
            isActionComplete = false
        )
    }

    fun handleInput(text: String, context: Context): DialogueResult {
        val prefs = VoiceOverlayPreferences(context)
        val timeoutSec = prefs.dialogueTimeoutSeconds

        val session = activeSession
        if (session == null || session.isExpired(timeoutSec)) {
            resetSession()
            return DialogueResult(
                handled = false,
                state = DialogueState.IDLE,
                statusMessage = "Сессия диалога не активна или истекла"
            )
        }

        val norm = SearchTargetResolver.normalize(text)

        // 1. Cancellation check
        val cancelTriggers = listOf("отмена", "отменить", "отбой", "забудь", "хватит", "стоп", "выйти")
        if (cancelTriggers.contains(norm)) {
            Log.i(TAG, "Search dialogue cancelled by user: '$text'")
            resetSession()
            return DialogueResult(
                handled = true,
                state = DialogueState.CANCELLED,
                statusMessage = "Поиск отменён",
                isActionComplete = true
            )
        }

        // 2. Repeat prompt check
        val repeatTriggers = listOf("повтори", "повторите", "что", "еще раз", "ещё раз", "не понял")
        if (repeatTriggers.contains(norm)) {
            Log.i(TAG, "Search dialogue repeat prompt requested")
            return DialogueResult(
                handled = true,
                state = DialogueState.AWAITING_SEARCH_TARGET,
                statusMessage = "${session.prompt} (Варианты: ${session.availableOptions.joinToString(", ")})",
                promptToUser = session.prompt,
                availableOptions = session.availableOptions,
                isActionComplete = false
            )
        }

        // 3. Resolve target from reply
        val target = SearchTargetResolver.resolveTargetFromReply(text)
        if (target != null && target.targetAppKey != null) {
            val adapter = SearchAdapterRegistry.getAdapter(target.targetAppKey)
            if (adapter != null) {
                if (adapter.isAvailable(context)) {
                    Log.i(TAG, "Dialogue target confirmed: ${adapter.displayName}, executing search for '${session.query}'")
                    state = DialogueState.EXECUTING_SEARCH
                    val execRes = adapter.executeSearch(context, session.query)
                    resetSession()
                    state = DialogueState.SEARCH_COMPLETED
                    return DialogueResult(
                        handled = true,
                        state = DialogueState.SEARCH_COMPLETED,
                        statusMessage = execRes.statusMessage,
                        isActionComplete = true,
                        executionResult = execRes
                    )
                } else {
                    Log.w(TAG, "Target app '${adapter.displayName}' recognized but not available on device")
                    val msg = "${adapter.displayName} не установлено. Искать в ${session.availableOptions.joinToString(" или ")}?"
                    return DialogueResult(
                        handled = true,
                        state = DialogueState.AWAITING_SEARCH_TARGET,
                        statusMessage = msg,
                        promptToUser = msg,
                        availableOptions = session.availableOptions,
                        isActionComplete = false
                    )
                }
            } else {
                // Recognized app, but no search adapter (e.g. ByeByeDPI or Settings)
                Log.w(TAG, "Target app '${target.targetDisplayName}' does not support search")
                val msg = "В ${target.targetDisplayName ?: "этом приложении"} поиск не поддерживается. Выберите: ${session.availableOptions.joinToString(" или ")}"
                return DialogueResult(
                    handled = true,
                    state = DialogueState.AWAITING_SEARCH_TARGET,
                    statusMessage = msg,
                    promptToUser = msg,
                    availableOptions = session.availableOptions,
                    isActionComplete = false
                )
            }
        }

        // 4. Check if the user initiated an entirely new explicit command
        val newCommandTriggers = listOf(
            "найди", "поищи", "открой", "закрой", "домой", "назад",
            "громче", "тише", "звук", "без звука", "выключи"
        )
        if (newCommandTriggers.any { norm.startsWith("$it ") || norm == it }) {
            Log.i(TAG, "New command interrupted dialogue; resetting session for text: '$text'")
            resetSession()
            return DialogueResult(
                handled = false,
                state = DialogueState.IDLE,
                statusMessage = "Новая команда прервала диалог"
            )
        }

        // 5. Unrecognized input in dialogue
        Log.w(TAG, "Unrecognized dialogue input: '$text'")
        val errorPrompt = "Не удалось определить источник. Выберите: ${session.availableOptions.joinToString(" или ")}"
        return DialogueResult(
            handled = true,
            state = DialogueState.AWAITING_SEARCH_TARGET,
            statusMessage = errorPrompt,
            promptToUser = errorPrompt,
            availableOptions = session.availableOptions,
            isActionComplete = false
        )
    }

    fun selectTargetOption(targetAppKey: String, context: Context): DialogueResult {
        val session = activeSession ?: return DialogueResult(
            handled = false,
            state = DialogueState.IDLE,
            statusMessage = "Сессия диалога не активна"
        )

        val adapter = SearchAdapterRegistry.getAdapter(targetAppKey)
            ?: return DialogueResult(
                handled = true,
                state = DialogueState.ERROR,
                statusMessage = "Адаптер для $targetAppKey не найден"
            )

        val execRes = adapter.executeSearch(context, session.query)
        resetSession()
        return DialogueResult(
            handled = true,
            state = DialogueState.SEARCH_COMPLETED,
            statusMessage = execRes.statusMessage,
            isActionComplete = true,
            executionResult = execRes
        )
    }

    fun resetSession() {
        activeSession = null
        state = DialogueState.IDLE
        Log.d(TAG, "Dialogue session reset to IDLE")
    }
}
