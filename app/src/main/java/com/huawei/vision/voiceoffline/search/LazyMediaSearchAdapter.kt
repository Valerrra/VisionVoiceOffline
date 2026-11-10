package com.huawei.vision.voiceoffline.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

class LazyMediaSearchAdapter : SearchAdapter {

    companion object {
        private const val TAG = "LazyMediaSearchAdapter"
        const val APP_KEY = "lazymedia"
        val CANDIDATE_PACKAGES = listOf(
            "com.lazycatsoftware.lmd",
            "com.lazycatsoftware.lazymediadeluxe"
        )
        const val SEARCH_ACTIVITY_NAME = "com.lazycatsoftware.lazymediadeluxe.ui.tv.activities.ActivityTvSearch"
    }

    override val targetAppKey: String = APP_KEY
    override val displayName: String = "LazyMedia Deluxe"
    override val candidatePackages: List<String> = CANDIDATE_PACKAGES
    override val integrationMethod: IntegrationMethod = IntegrationMethod.EXPORTED_INTENT
    override val lastVerificationStatus: String = "Проверено на Huawei Vision S (ActivityTvSearch extra query)"

    override fun isAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return CANDIDATE_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun executeSearch(context: Context, query: String): SearchExecutionResult {
        Log.i(TAG, "Executing LazyMedia Deluxe search for: '$query'")
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
            val installedPkg = CANDIDATE_PACKAGES.firstOrNull { pkg ->
                try {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            } ?: CANDIDATE_PACKAGES.first()

            val intent = Intent().apply {
                component = ComponentName(installedPkg, SEARCH_ACTIVITY_NAME)
                putExtra("query", clean)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(intent)
            Log.i(TAG, "LazyMedia search activity launched for '$clean'")
            SearchExecutionResult.success(
                targetApp = displayName,
                query = clean,
                message = "Поиск в LazyMedia Deluxe: «$clean»",
                method = integrationMethod
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute LazyMedia search", e)
            SearchExecutionResult.error(
                targetApp = displayName,
                query = clean,
                error = SearchError.EXECUTION_FAILED,
                message = "Ошибка при запуске поиска в LazyMedia Deluxe: ${e.message}"
            )
        }
    }
}
