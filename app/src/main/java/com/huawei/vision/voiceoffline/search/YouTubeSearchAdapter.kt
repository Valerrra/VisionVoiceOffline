package com.huawei.vision.voiceoffline.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.huawei.vision.voiceoffline.AppRegistry
import java.net.URLEncoder

class YouTubeSearchAdapter : SearchAdapter {

    companion object {
        private const val TAG = "YouTubeSearchAdapter"
    }

    override val targetAppKey: String = "youtube"
    override val displayName: String = "YouTube"
    override val candidatePackages: List<String> = listOf(
        "com.google.android.youtube.tv",
        "com.liskovsoft.smarttubetv.beta",
        "com.armanich.youtube"
    )

    override fun isAvailable(context: Context): Boolean {
        return candidatePackages.any { pkg -> AppRegistry.getApp(pkg) != null }
    }

    override fun executeSearch(context: Context, query: String): SearchExecutionResult {
        val app = candidatePackages.firstNotNullOfOrNull { AppRegistry.getApp(it) }
            ?: return SearchExecutionResult.error(
                targetApp = targetAppKey,
                query = query,
                error = SearchError.APP_NOT_INSTALLED,
                message = "Приложение YouTube не установлено"
            )

        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
            val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                `package` = app.packageName
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched native YouTube TV search: '$query' [pkg=${app.packageName}]")
            SearchExecutionResult.success(
                targetApp = targetAppKey,
                query = query,
                message = "Поиск в YouTube: «$query»"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube search", e)
            SearchExecutionResult.error(
                targetApp = targetAppKey,
                query = query,
                error = SearchError.EXECUTION_FAILED,
                message = "Ошибка запуска YouTube: ${e.message}"
            )
        }
    }
}
