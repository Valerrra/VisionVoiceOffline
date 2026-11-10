package com.huawei.vision.voiceoffline.search

enum class SearchPolicy {
    ASK_ALWAYS,
    PREFER_ACTIVE,
    DEFAULT_APP
}

enum class DialogueState {
    IDLE,
    SEARCH_REQUEST,
    AWAITING_SEARCH_TARGET,
    EXECUTING_SEARCH,
    SEARCH_COMPLETED,
    CANCELLED,
    ERROR
}

enum class SearchError {
    APP_NOT_INSTALLED,
    ADAPTER_NOT_FOUND,
    TIMEOUT,
    CANCELLED,
    EXECUTION_FAILED,
    SECURITY_VIOLATION
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
    val availableOptions: List<String> = emptyList()
) {
    fun isExpired(timeoutSeconds: Int): Boolean {
        val elapsedMs = System.currentTimeMillis() - createdAt
        return elapsedMs > (timeoutSeconds * 1000L)
    }
}

data class SearchExecutionResult(
    val success: Boolean,
    val targetApp: String,
    val query: String,
    val statusMessage: String,
    val error: SearchError? = null
) {
    companion object {
        fun success(targetApp: String, query: String, message: String): SearchExecutionResult {
            return SearchExecutionResult(
                success = true,
                targetApp = targetApp,
                query = query,
                statusMessage = message
            )
        }

        fun error(targetApp: String, query: String, error: SearchError, message: String): SearchExecutionResult {
            return SearchExecutionResult(
                success = false,
                targetApp = targetApp,
                query = query,
                statusMessage = message,
                error = error
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
