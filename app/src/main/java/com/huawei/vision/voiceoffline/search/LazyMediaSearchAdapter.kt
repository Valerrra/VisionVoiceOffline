package com.huawei.vision.voiceoffline.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.huawei.vision.voiceoffline.AppRegistry

class LazyMediaSearchAdapter : SearchAdapter {

    companion object {
        private const val TAG = "LazyMediaSearchAdapter"
        private const val SEARCH_ACTIVITY = "com.lazycatsoftware.lazymediadeluxe.ui.tv.activities.ActivityTvSearch"
    }

    override val targetAppKey: String = "lazymedia"
    override val displayName: String = "LazyMedia Deluxe"
    override val candidatePackages: List<String> = listOf(
        "com.lazycatsoftware.lmd",
        "com.lazycatsoftware.lazymedia"
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
                message = "Приложение LazyMedia Deluxe не установлено"
            )

        return try {
            val intent = Intent().apply {
                component = ComponentName(app.packageName, SEARCH_ACTIVITY)
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Log.i(TAG, "Launched native LazyMedia search: '$query' [pkg=${app.packageName}]")
            SearchExecutionResult.success(
                targetApp = targetAppKey,
                query = query,
                message = "Поиск в LazyMedia: «$query»"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch LazyMedia search", e)
            SearchExecutionResult.error(
                targetApp = targetAppKey,
                query = query,
                error = SearchError.EXECUTION_FAILED,
                message = "Ошибка запуска LazyMedia: ${e.message}"
            )
        }
    }
}
