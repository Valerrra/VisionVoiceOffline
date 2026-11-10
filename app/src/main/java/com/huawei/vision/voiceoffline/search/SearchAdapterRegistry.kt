package com.huawei.vision.voiceoffline.search

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

object SearchAdapterRegistry {

    private const val PREFS_NAME = "search_registry_prefs"
    private const val KEY_EXCLUDED_APPS = "excluded_search_keys"

    private val adapters = CopyOnWriteArrayList<SearchAdapter>()

    init {
        registerDefaultAdapters()
    }

    private fun registerDefaultAdapters() {
        adapters.clear()
        adapters.add(YouTubeSearchAdapter())
        adapters.add(LazyMediaSearchAdapter())
    }

    fun registerAdapter(adapter: SearchAdapter) {
        adapters.removeAll { it.targetAppKey == adapter.targetAppKey }
        adapters.add(adapter)
    }

    fun getAdapter(appKey: String): SearchAdapter? {
        return adapters.firstOrNull { it.targetAppKey == appKey }
    }

    fun getAdapterForPackage(packageName: String): SearchAdapter? {
        return adapters.firstOrNull { it.candidatePackages.contains(packageName) }
    }

    fun isSearchSupported(appKey: String): Boolean {
        return adapters.any { it.targetAppKey == appKey }
    }

    fun isAppExcludedFromSearch(context: Context, appKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()
        return set.contains(appKey)
    }

    fun setAppExcludedFromSearch(context: Context, appKey: String, excluded: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_EXCLUDED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (excluded) {
            set.add(appKey)
        } else {
            set.remove(appKey)
        }
        prefs.edit().putStringSet(KEY_EXCLUDED_APPS, set).apply()
    }

    fun getAvailableAdapters(context: Context): List<SearchAdapter> {
        return adapters.filter { adapter ->
            adapter.isAvailable(context) && !isAppExcludedFromSearch(context, adapter.targetAppKey)
        }
    }

    fun getAllAdapters(): List<SearchAdapter> {
        return adapters.toList()
    }

    fun resetForTesting() {
        registerDefaultAdapters()
    }
}
