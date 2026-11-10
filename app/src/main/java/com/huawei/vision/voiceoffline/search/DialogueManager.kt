package com.huawei.vision.voiceoffline.search

import android.content.Context
import android.util.Log
import com.huawei.vision.voiceoffline.VisionVoiceAccessibilityService
import com.huawei.vision.voiceoffline.VoiceOverlayPreferences

object DialogueManager {

    private const val TAG = "DialogueManager"

    var state: DialogueState = DialogueState.IDLE
        private set

    var activeSession: SearchSession? = null
        private set

    val dialogueContext = DialogueContext()

    private val POSITIVE_CONFIRMATIONS = listOf(
        "да", "ага", "конечно", "здесь", "в этом приложении", "давай",
        "ищи здесь", "ищи тут", "тут", "да ищи", "да ищи здесь", "точно",
        "в текущем", "в текущем приложении", "открыть в текущем",
        "открыть в текущем приложении", "искать в текущем", "в этом", "открой здесь",
        "открой в ютубе", "открой в youtube", "в ютубе", "на ютубе"
    )

    private val NEGATIVE_CONFIRMATIONS = listOf(
        "нет", "не здесь", "не тут", "другое", "в другом приложении",
        "другое приложение", "не надо здесь", "поменяй", "выбрать другое",
        "в другом", "искать в другом", "ищи в другом", "искать в другом приложении"
    )

    private val CANCEL_TRIGGERS = listOf(
        "отмена", "отменить", "отбой", "забудь", "хватит", "стоп",
        "выйти", "не надо", "прекрати", "отвяжись"
    )

    private val REPEAT_PROMPT_TRIGGERS = listOf(
        "повтори", "повторите", "что", "еще раз", "ещё раз", "не понял", "повтори вопрос"
    )

    fun hasActiveSession(timeoutSeconds: Int): Boolean {
        val session = activeSession ?: return false
        if (session.isExpired(timeoutSeconds)) {
            Log.d(TAG, "Search dialogue session expired (timeout: ${timeoutSeconds}s)")
            resetSession()
            return false
        }
        return state == DialogueState.AWAITING_SEARCH_CONFIRMATION ||
                state == DialogueState.AWAITING_SEARCH_TARGET
    }

    fun hasRecentSearchMemory(memoryTimeoutSeconds: Int): Boolean {
        return !dialogueContext.isMemoryExpired(memoryTimeoutSeconds)
    }

    fun startSearchDialogue(
        query: String,
        rawText: String,
        context: Context,
        forcedPolicy: SearchPolicy? = null
    ): DialogueResult {
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

        val policy = forcedPolicy ?: prefs.searchPolicy
        Log.i(TAG, "Starting search dialogue for '$query' with policy: $policy")

        // Initialize dialogue context with query
        dialogueContext.originalQuery = query
        dialogueContext.currentQuery = query
        dialogueContext.lastExplicitUserIntent = rawText
        dialogueContext.rejectedTargets.clear()

        when (policy) {
            SearchPolicy.DEFAULT_APP -> {
                val defaultKey = prefs.defaultSearchApp
                val defaultAdapter = availableAdapters.firstOrNull { it.targetAppKey == defaultKey }
                    ?: availableAdapters.first()
                Log.i(TAG, "Search policy DEFAULT_APP: using ${defaultAdapter.displayName}")
                val execRes = defaultAdapter.executeSearch(context, query)
                recordSearchExecution(defaultAdapter.targetAppKey, defaultAdapter.displayName, query, execRes)
                return DialogueResult(
                    handled = true,
                    state = DialogueState.SEARCH_COMPLETED,
                    statusMessage = execRes.statusMessage,
                    isActionComplete = true,
                    executionResult = execRes
                )
            }

            SearchPolicy.PREFER_ACTIVE -> {
                val activePkg = VisionVoiceAccessibilityService.getActivePackage(context)
                if (!activePkg.isNullOrEmpty()) {
                    val activeAdapter = SearchAdapterRegistry.getAdapterForPackage(activePkg)
                    if (activeAdapter != null && activeAdapter.isAvailable(context) &&
                        !SearchAdapterRegistry.isAppExcludedFromSearch(context, activeAdapter.targetAppKey)
                    ) {
                        Log.i(TAG, "Search policy PREFER_ACTIVE: proposing active app ${activeAdapter.displayName}")
                        state = DialogueState.AWAITING_SEARCH_CONFIRMATION
                        dialogueContext.dialogueState = state
                        dialogueContext.pendingCandidateTarget = activeAdapter.targetAppKey
                        dialogueContext.pendingCandidateDisplayName = activeAdapter.displayName

                        val prompt = "Искать «$query» в ${activeAdapter.displayName}?"
                        val options = listOf("Да (в текущем)", "В другом приложении", "Отмена")

                        val session = SearchSession(
                            query = query,
                            rawText = rawText,
                            prompt = prompt,
                            availableOptions = options,
                            candidateAppKey = activeAdapter.targetAppKey,
                            candidateDisplayName = activeAdapter.displayName,
                            createdAt = System.currentTimeMillis()
                        )
                        activeSession = session

                        return DialogueResult(
                            handled = true,
                            state = DialogueState.AWAITING_SEARCH_CONFIRMATION,
                            statusMessage = "$prompt (Скажите: «Да» / «В текущем» или «В другом приложении»)",
                            promptToUser = prompt,
                            availableOptions = options,
                            isActionComplete = false
                        )
                    }
                }
                // Fallback to ASK_ALWAYS if active app does not support search
            }

            SearchPolicy.ASK_ALWAYS -> {
                // Fall through to target prompt below
            }
        }

        // Prompt user for search target
        return promptForTarget(query, rawText, availableAdapters)
    }

    private fun promptForTarget(
        query: String,
        rawText: String,
        availableAdapters: List<SearchAdapter>
    ): DialogueResult {
        val nonRejectedAdapters = availableAdapters.filter {
            !dialogueContext.rejectedTargets.contains(it.targetAppKey)
        }.ifEmpty { availableAdapters }

        val options = nonRejectedAdapters.map { it.displayName }
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
        dialogueContext.dialogueState = state

        Log.i(TAG, "Prompting search target: '$prompt' Options: $options")
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
        val memTimeoutSec = prefs.lastSearchMemoryTimeoutSeconds
        val norm = SearchTargetResolver.normalize(text)

        // 1. Check Cancellation across all active dialogue states
        if (CANCEL_TRIGGERS.contains(norm)) {
            Log.i(TAG, "Search dialogue cancelled by user: '$text'")
            resetSession()
            dialogueContext.dialogueState = DialogueState.CANCELLED
            return DialogueResult(
                handled = true,
                state = DialogueState.CANCELLED,
                statusMessage = "Поиск отменён",
                isActionComplete = true
            )
        }

        // 2. Check Repeat Question Prompt ("повтори")
        if (REPEAT_PROMPT_TRIGGERS.contains(norm)) {
            val session = activeSession
            if (session != null) {
                Log.i(TAG, "Search dialogue repeat prompt requested")
                return DialogueResult(
                    handled = true,
                    state = state,
                    statusMessage = "${session.prompt} (Варианты: ${session.availableOptions.joinToString(", ")})",
                    promptToUser = session.prompt,
                    availableOptions = session.availableOptions,
                    isActionComplete = false
                )
            }
        }

        // 3. Handle state AWAITING_SEARCH_CONFIRMATION
        if (state == DialogueState.AWAITING_SEARCH_CONFIRMATION) {
            val session = activeSession
            if (session == null || session.isExpired(timeoutSec)) {
                resetSession()
                return DialogueResult(
                    handled = false,
                    state = DialogueState.IDLE,
                    statusMessage = "Время ожидания подтверждения истекло"
                )
            }

            val candidateKey = session.candidateAppKey ?: dialogueContext.pendingCandidateTarget
            val candidateName = session.candidateDisplayName ?: dialogueContext.pendingCandidateDisplayName ?: "приложение"

            // A) Positive response: execute in proposed app
            if (POSITIVE_CONFIRMATIONS.contains(norm)) {
                Log.i(TAG, "Search confirmation accepted for $candidateName ('$norm')")
                val adapter = candidateKey?.let { SearchAdapterRegistry.getAdapter(it) }
                if (adapter != null && adapter.isAvailable(context)) {
                    val execRes = adapter.executeSearch(context, session.query)
                    recordSearchExecution(adapter.targetAppKey, adapter.displayName, session.query, execRes)
                    resetSession()
                    return DialogueResult(
                        handled = true,
                        state = DialogueState.SEARCH_COMPLETED,
                        statusMessage = execRes.statusMessage,
                        isActionComplete = true,
                        executionResult = execRes
                    )
                }
            }

            // B) Negative response: switch to other available apps
            if (NEGATIVE_CONFIRMATIONS.contains(norm)) {
                Log.i(TAG, "Search confirmation rejected for $candidateName ('$norm')")
                if (candidateKey != null) {
                    dialogueContext.rejectedTargets.add(candidateKey)
                }
                val available = SearchAdapterRegistry.getAvailableAdapters(context)
                return promptForTarget(session.query, session.rawText, available)
            }

            // C) User directly named an alternative target in response (e.g. "Лэйзи Медиа")
            val directTarget = SearchTargetResolver.resolveTargetFromReply(text)
            if (directTarget?.targetAppKey != null) {
                return executeTargetSearch(directTarget.targetAppKey, session.query, context)
            }
        }

        // 4. Handle state AWAITING_SEARCH_TARGET
        if (state == DialogueState.AWAITING_SEARCH_TARGET) {
            val session = activeSession
            if (session == null || session.isExpired(timeoutSec)) {
                resetSession()
                return DialogueResult(
                    handled = false,
                    state = DialogueState.IDLE,
                    statusMessage = "Время ожидания выбора источника истекло"
                )
            }

            val target = SearchTargetResolver.resolveTargetFromReply(text)
            if (target?.targetAppKey != null) {
                return executeTargetSearch(target.targetAppKey, session.query, context)
            }

            // Unrecognized target input
            Log.w(TAG, "Unrecognized dialogue target input: '$text'")
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

        // 5. Contextual Follow-Up turns (Repeat in another app or Query Refinement) within 120s
        if (hasRecentSearchMemory(memTimeoutSec)) {
            val prevQuery = dialogueContext.currentQuery.ifEmpty { dialogueContext.originalQuery }

            // 5A. Repeat in another app / "там же" / "в другом приложении"
            val repeatIntent = SearchQueryParser.detectRepeatIntent(text)
            if (repeatIntent.isRepeat) {
                if (repeatIntent.useSameTarget) {
                    val lastTarget = dialogueContext.lastSearchTarget
                    if (!lastTarget.isNullOrEmpty()) {
                        Log.i(TAG, "Repeating search for '$prevQuery' in same target: $lastTarget")
                        return executeTargetSearch(lastTarget, prevQuery, context)
                    }
                } else if (repeatIntent.requestAlternative) {
                    val available = SearchAdapterRegistry.getAvailableAdapters(context)
                    dialogueContext.lastSearchTarget?.let { dialogueContext.rejectedTargets.add(it) }
                    return promptForTarget(prevQuery, text, available)
                } else if (!repeatIntent.targetAppKey.isNullOrEmpty()) {
                    Log.i(TAG, "Repeating search for '$prevQuery' in new target: ${repeatIntent.targetAppKey}")
                    return executeTargetSearch(repeatIntent.targetAppKey, prevQuery, context)
                }
            }

            // 5B. Query Refinement (seasons, years, audio, subtitles, extra terms)
            val refinement = SearchQueryParser.detectRefinement(text, prevQuery)
            if (refinement.isRefinement) {
                val updatedQuery = refinement.updatedQuery
                Log.i(TAG, "Refining query from '$prevQuery' to '$updatedQuery' (${refinement.refinementType})")
                dialogueContext.currentQuery = updatedQuery

                val target = dialogueContext.lastSearchTarget
                    ?: SearchAdapterRegistry.getAvailableAdapters(context).firstOrNull()?.targetAppKey

                if (target != null) {
                    return executeTargetSearch(target, updatedQuery, context)
                } else {
                    val available = SearchAdapterRegistry.getAvailableAdapters(context)
                    return promptForTarget(updatedQuery, text, available)
                }
            }
        } else {
            // Check if user attempted a follow-up ("а теперь...", "поищи там же") but context is expired/missing
            val repeatIntent = SearchQueryParser.detectRepeatIntent(text)
            val refinement = SearchQueryParser.detectRefinement(text, "stub")
            if (repeatIntent.isRepeat || (norm.startsWith("а теперь ") && refinement.isRefinement)) {
                Log.w(TAG, "Attempted contextual search follow-up without active context: '$text'")
                return DialogueResult(
                    handled = true,
                    state = DialogueState.ERROR,
                    statusMessage = "Предыдущий поиск не найден или устарел. Что найти?",
                    promptToUser = "Что найти?",
                    isActionComplete = false
                )
            }
        }

        return DialogueResult(
            handled = false,
            state = DialogueState.IDLE,
            statusMessage = "Ввод не относится к диалогу поиска"
        )
    }

    private fun executeTargetSearch(targetAppKey: String, query: String, context: Context): DialogueResult {
        val adapter = SearchAdapterRegistry.getAdapter(targetAppKey)
        if (adapter != null) {
            if (adapter.isAvailable(context) && !SearchAdapterRegistry.isAppExcludedFromSearch(context, adapter.targetAppKey)) {
                Log.i(TAG, "Executing target search in ${adapter.displayName} for '$query'")
                state = DialogueState.EXECUTING_SEARCH
                val execRes = adapter.executeSearch(context, query)
                recordSearchExecution(adapter.targetAppKey, adapter.displayName, query, execRes)
                resetSession()
                return DialogueResult(
                    handled = true,
                    state = DialogueState.SEARCH_COMPLETED,
                    statusMessage = execRes.statusMessage,
                    isActionComplete = true,
                    executionResult = execRes
                )
            } else {
                Log.w(TAG, "Target app '${adapter.displayName}' recognized but unavailable/excluded")
                val available = SearchAdapterRegistry.getAvailableAdapters(context).map { it.displayName }
                val msg = "${adapter.displayName} не доступно для поиска. Искать в ${available.joinToString(" или ")}?"
                return DialogueResult(
                    handled = true,
                    state = DialogueState.AWAITING_SEARCH_TARGET,
                    statusMessage = msg,
                    promptToUser = msg,
                    availableOptions = available,
                    isActionComplete = false
                )
            }
        } else {
            val disp = SearchTargetResolver.getDisplayName(targetAppKey) ?: targetAppKey
            Log.w(TAG, "Target app '$disp' does not support search")
            val available = SearchAdapterRegistry.getAvailableAdapters(context).map { it.displayName }
            val msg = "В $disp поиск не поддерживается. Выберите: ${available.joinToString(" или ")}"
            return DialogueResult(
                handled = true,
                state = DialogueState.AWAITING_SEARCH_TARGET,
                statusMessage = msg,
                promptToUser = msg,
                availableOptions = available,
                isActionComplete = false
            )
        }
    }

    fun recordSearchExecution(
        targetAppKey: String,
        targetDisplayName: String,
        query: String,
        execResult: SearchExecutionResult
    ) {
        dialogueContext.lastSearchTarget = targetAppKey
        dialogueContext.lastSearchTargetDisplayName = targetDisplayName
        if (dialogueContext.originalQuery.isEmpty()) {
            dialogueContext.originalQuery = query
        }
        dialogueContext.currentQuery = query
        dialogueContext.lastSearchResult = execResult
        dialogueContext.lastSearchTimestamp = System.currentTimeMillis()
        dialogueContext.dialogueState = DialogueState.SEARCH_COMPLETED
        state = DialogueState.SEARCH_COMPLETED
        Log.d(TAG, "Recorded search execution: target=$targetAppKey, query='$query', timestamp=${dialogueContext.lastSearchTimestamp}")
    }

    fun resetSession() {
        activeSession = null
        state = DialogueState.IDLE
        Log.d(TAG, "Dialogue session reset to IDLE")
    }

    fun clearMemory() {
        resetSession()
        dialogueContext.clear()
        Log.d(TAG, "Dialogue context memory cleared")
    }
}
