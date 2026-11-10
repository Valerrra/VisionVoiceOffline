package com.huawei.vision.voiceoffline.search

enum class SearchPolicy {
    ASK_ALWAYS,
    PREFER_ACTIVE,
    DEFAULT_APP
}

enum class DialogueState {
    IDLE,
    AWAITING_SEARCH_CONFIRMATION, // Candidate app proposed (e.g. active app): "Искать «Доктор Кто» в YouTube?"
    AWAITING_SEARCH_TARGET,       // Target unknown: "Где будем искать?"
    EXECUTING_SEARCH,
    SEARCH_COMPLETED,             // Search finished; memory retained for follow-up turns
    CANCELLED,
    ERROR
}

enum class SearchError {
    APP_NOT_INSTALLED,
    ADAPTER_NOT_FOUND,
    TIMEOUT,
    CANCELLED,
    EXECUTION_FAILED,
    SECURITY_VIOLATION,
    NO_PREVIOUS_CONTEXT
}

enum class IntegrationMethod {
    DEEP_LINK,
    EXPORTED_INTENT,
    ACCESSIBILITY_AUTOMATION,
    IME_INPUT,
    UNSUPPORTED
}

enum class RefinementType {
    SEASON,
    YEAR,
    LANGUAGE_AUDIO,
    SUBTITLES,
    EXTRA_KEYWORDS,
    UNKNOWN
}

data class ResolvedSearchTarget(
    val targetAppKey: String?,
    val targetDisplayName: String?,
    val cleanQuery: String,
    val contentType: String? = null,
    val matchedAlias: String? = null,
    val isExplicitTarget: Boolean = false
)

data class SearchSession(
    val query: String,
    val rawText: String,
    val targetAppKey: String? = null,
    val contentType: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val prompt: String = "",
    val availableOptions: List<String> = emptyList(),
    val candidateAppKey: String? = null,
    val candidateDisplayName: String? = null
) {
    fun isExpired(timeoutSeconds: Int): Boolean {
        val elapsedMs = System.currentTimeMillis() - createdAt
        return elapsedMs > (timeoutSeconds * 1000L)
    }
}

data class DialogueContext(
    var originalQuery: String = "",
    var currentQuery: String = "",
    var contentType: String? = null,
    var lastSearchTarget: String? = null,
    var lastSearchTargetDisplayName: String? = null,
    var lastSearchResult: SearchExecutionResult? = null,
    var lastSearchTimestamp: Long = 0L,
    var dialogueState: DialogueState = DialogueState.IDLE,
    val rejectedTargets: MutableList<String> = mutableListOf(),
    var lastExplicitUserIntent: String? = null,
    var pendingCandidateTarget: String? = null,
    var pendingCandidateDisplayName: String? = null
) {
    fun isMemoryExpired(memoryTimeoutSeconds: Int): Boolean {
        if (lastSearchTimestamp == 0L) return true
        val elapsedMs = System.currentTimeMillis() - lastSearchTimestamp
        return elapsedMs > (memoryTimeoutSeconds * 1000L)
    }

    fun clear() {
        originalQuery = ""
        currentQuery = ""
        contentType = null
        lastSearchTarget = null
        lastSearchTargetDisplayName = null
        lastSearchResult = null
        lastSearchTimestamp = 0L
        dialogueState = DialogueState.IDLE
        rejectedTargets.clear()
        lastExplicitUserIntent = null
        pendingCandidateTarget = null
        pendingCandidateDisplayName = null
    }
}

data class SearchExecutionResult(
    val success: Boolean,
    val targetApp: String,
    val query: String,
    val statusMessage: String,
    val error: SearchError? = null,
    val executionMethod: IntegrationMethod = IntegrationMethod.DEEP_LINK
) {
    companion object {
        fun success(
            targetApp: String,
            query: String,
            message: String,
            method: IntegrationMethod = IntegrationMethod.DEEP_LINK
        ): SearchExecutionResult {
            return SearchExecutionResult(
                success = true,
                targetApp = targetApp,
                query = query,
                statusMessage = message,
                executionMethod = method
            )
        }

        fun error(
            targetApp: String,
            query: String,
            error: SearchError,
            message: String
        ): SearchExecutionResult {
            return SearchExecutionResult(
                success = false,
                targetApp = targetApp,
                query = query,
                statusMessage = message,
                error = error,
                executionMethod = IntegrationMethod.UNSUPPORTED
            )
        }
    }
}

data class DialogueResult(
    val handled: Boolean,
    val state: DialogueState,
    val statusMessage: String,
    val promptToUser: String? = null,
    val availableOptions: List<String> = emptyList(),
    val isActionComplete: Boolean = false,
    val executionResult: SearchExecutionResult? = null
)
