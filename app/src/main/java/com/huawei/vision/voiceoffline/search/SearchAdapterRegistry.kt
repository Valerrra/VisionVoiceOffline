package com.huawei.vision.voiceoffline.search

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

object SearchAdapterRegistry {

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

    fun getAvailableAdapters(context: Context): List<SearchAdapter> {
        return adapters.filter { it.isAvailable(context) }
    }

    fun getAllAdapters(): List<SearchAdapter> {
        return adapters.toList()
    }

    fun resetForTesting() {
        registerDefaultAdapters()
    }
}
