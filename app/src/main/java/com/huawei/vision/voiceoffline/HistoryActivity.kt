package com.huawei.vision.voiceoffline

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HistoryActivity"
    }

    private lateinit var historyManager: CommandHistoryManager
    private lateinit var tvHistoryCountBadge: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var btnBackFromHistory: Button
    private lateinit var tvEmptyHistory: TextView
    private lateinit var rvHistoryList: RecyclerView
    private lateinit var adapter: HistoryAdapter

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyManager = CommandHistoryManager(this)

        tvHistoryCountBadge = findViewById(R.id.tvHistoryCountBadge)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        btnBackFromHistory = findViewById(R.id.btnBackFromHistory)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)
        rvHistoryList = findViewById(R.id.rvHistoryList)

        rvHistoryList.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter()
        rvHistoryList.adapter = adapter

        btnClearHistory.setOnClickListener {
            showClearConfirmDialog()
        }

        btnBackFromHistory.setOnClickListener {
            finish()
        }

        loadHistory()
    }

    private fun loadHistory() {
        val list = historyManager.getHistory()
        tvHistoryCountBadge.text = "${list.size} записей"

        if (list.isEmpty()) {
            tvEmptyHistory.visibility = View.VISIBLE
            rvHistoryList.visibility = View.GONE
            btnClearHistory.visibility = View.GONE
        } else {
            tvEmptyHistory.visibility = View.GONE
            rvHistoryList.visibility = View.VISIBLE
            btnClearHistory.visibility = View.VISIBLE
            adapter.submitList(list)
            rvHistoryList.post {
                rvHistoryList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this, R.style.Theme_VisionVoice_Dialog)
            .setTitle("Очистка истории")
            .setMessage("Вы действительно хотите удалить все записи журнала команд?")
            .setPositiveButton("Удалить") { _, _ ->
                historyManager.clearHistory()
                loadHistory()
                Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        private val items = mutableListOf<HistoryEntry>()

        fun submitList(newItems: List<HistoryEntry>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_entry, parent, false)
            return HistoryViewHolder(view)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvHistoryIntentBadge: TextView = itemView.findViewById(R.id.tvHistoryIntentBadge)
            private val tvHistorySuccessBadge: TextView = itemView.findViewById(R.id.tvHistorySuccessBadge)
            private val tvHistoryTime: TextView = itemView.findViewById(R.id.tvHistoryTime)
            private val tvHistoryDuration: TextView = itemView.findViewById(R.id.tvHistoryDuration)
            private val tvHistoryRawText: TextView = itemView.findViewById(R.id.tvHistoryRawText)
            private val tvHistoryStatus: TextView = itemView.findViewById(R.id.tvHistoryStatus)

            fun bind(entry: HistoryEntry) {
                tvHistoryIntentBadge.text = entry.intent
                tvHistorySuccessBadge.text = if (entry.success) "ВЫПОЛНЕНО" else "ОТКЛОНЕНО"
                tvHistorySuccessBadge.setTextColor(
                    ContextCompat.getColor(
                        this@HistoryActivity,
                        if (entry.success) R.color.accent_green else R.color.accent_red
                    )
                )

                val dateStr = try {
                    timeFormat.format(Date(entry.timestampMs))
                } catch (_: Exception) {
                    "—"
                }
                tvHistoryTime.text = dateStr
                tvHistoryDuration.text = "${entry.executionTimeMs} ms"

                tvHistoryRawText.text = if (entry.rawText.isNotEmpty()) "«${entry.rawText}»" else "(Пустая транскрипция)"
                tvHistoryStatus.text = entry.statusMessage
            }
        }
    }
}
