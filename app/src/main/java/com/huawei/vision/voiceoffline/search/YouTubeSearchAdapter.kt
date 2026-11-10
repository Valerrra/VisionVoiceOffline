package com.huawei.vision.voiceoffline.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class YouTubeSearchAdapter : SearchAdapter {

    companion object {
        private const val TAG = "YouTubeSearchAdapter"
        const val APP_KEY = "youtube"
        val CANDIDATE_PACKAGES = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.liskovsoft.videomanager",
            "com.liskovsoft.smarttubetv.beta",
            "org.schabi.newpipe"
        )
    }

    override val targetAppKey: String = APP_KEY
    override val displayName: String = "YouTube"
    override val candidatePackages: List<String> = CANDIDATE_PACKAGES
    override val integrationMethod: IntegrationMethod = IntegrationMethod.DEEP_LINK
    override val lastVerificationStatus: String = "Проверено на Huawei Vision S (ACTION_VIEW Leanback deep link)"

    override fun isAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return candidatePackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun executeSearch(context: Context, query: String): SearchExecutionResult {
        Log.i(TAG, "Executing YouTube search for: '$query'")
        val clean = query.trim()
        if (clean.isEmpty()) {
            return SearchExecutionResult.error(
                targetApp = displayName,
                query = query,
                error = SearchError.EXECUTION_FAILED,
                message = "Пустой поисковый запрос"
            )
        }

        return try {
            val encodedQuery = Uri.encode(clean)
            val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")

            val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // If YouTube TV is present, target it specifically
                setPackage("com.google.android.youtube.tv")
            }

            // Fallback package resolution if YouTube TV package isn't found
            val pm = context.packageManager
            if (intent.resolveActivity(pm) == null) {
                intent.setPackage(null)
            }

            context.startActivity(intent)
            Log.i(TAG, "YouTube search intent launched for '$clean'")
            SearchExecutionResult.success(
                targetApp = displayName,
                query = clean,
                message = "Поиск в YouTube: «$clean»",
                method = integrationMethod
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute YouTube search", e)
            SearchExecutionResult.error(
                targetApp = displayName,
                query = clean,
                error = SearchError.EXECUTION_FAILED,
                message = "Ошибка при запуске поиска в YouTube: ${e.message}"
            )
        }
    }
}
