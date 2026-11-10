package com.huawei.vision.voiceoffline.search

import android.content.Context

interface SearchAdapter {
    val targetAppKey: String
    val displayName: String
    val candidatePackages: List<String>
    val integrationMethod: IntegrationMethod
    val lastVerificationStatus: String

    fun isAvailable(context: Context): Boolean
    fun executeSearch(context: Context, query: String): SearchExecutionResult
}
