package com.huawei.vision.voiceoffline

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class HistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = "",
    val rawText: String,
    val intent: String,
    val target: String? = null,
    val statusMessage: String,
    val success: Boolean,
    val error: String? = null,
    val executionTimeMs: Long = 0L
) {
    val timestampMs: Long get() = timestamp
}

class CommandHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "CommandHistoryManager"
        private const val FILE_NAME = "command_history.json"
        private const val MAX_HISTORY_ENTRIES = 100

        @Volatile
        private var instance: CommandHistoryManager? = null

        fun getInstance(context: Context): CommandHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: CommandHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = VoiceOverlayPreferences(context)
    private val timeFormat = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
    private val historyFile: File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun addEntry(
        rawText: String,
        intent: String,
        target: String?,
        statusMessage: String,
        success: Boolean,
        error: String?,
        executionTimeMs: Long = 0L
    ) {
        if (!prefs.isHistoryEnabled) {
            return
        }

        try {
            val now = System.currentTimeMillis()
            val entry = HistoryEntry(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                formattedTime = timeFormat.format(Date(now)),
                rawText = rawText,
                intent = intent,
                target = target,
                statusMessage = statusMessage,
                success = success,
                error = error,
                executionTimeMs = executionTimeMs
            )

            val current = getHistory().toMutableList()
            current.add(0, entry) // Newest first

            if (current.size > MAX_HISTORY_ENTRIES) {
                current.subList(MAX_HISTORY_ENTRIES, current.size).clear()
            }

            saveHistory(current)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding history entry", e)
        }
    }

    @Synchronized
    fun getHistory(): List<HistoryEntry> {
        if (!historyFile.exists()) {
            return emptyList()
        }

        val result = mutableListOf<HistoryEntry>()
        try {
            val jsonStr = historyFile.readText(Charsets.UTF_8)
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                result.add(
                    HistoryEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", 0L),
                        formattedTime = obj.optString("formattedTime", ""),
                        rawText = obj.optString("rawText", ""),
                        intent = obj.optString("intent", ""),
                        target = if (obj.has("target") && !obj.isNull("target")) obj.getString("target") else null,
                        statusMessage = obj.optString("statusMessage", ""),
                        success = obj.optBoolean("success", true),
                        error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null,
                        executionTimeMs = obj.optLong("executionTimeMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading history file", e)
        }
        return result
    }

    @Synchronized
    fun clearHistory() {
        try {
            if (historyFile.exists()) {
                historyFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing history", e)
        }
    }

    private fun saveHistory(entries: List<HistoryEntry>) {
        try {
            val jsonArr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("formattedTime", entry.formattedTime)
                    put("rawText", entry.rawText)
                    put("intent", entry.intent)
                    put("target", entry.target ?: JSONObject.NULL)
                    put("statusMessage", entry.statusMessage)
                    put("success", entry.success)
                    put("error", entry.error ?: JSONObject.NULL)
                    put("executionTimeMs", entry.executionTimeMs)
                }
                jsonArr.put(obj)
            }
            historyFile.writeText(jsonArr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history file", e)
        }
    }
}
