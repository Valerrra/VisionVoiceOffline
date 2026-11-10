package com.huawei.vision.voiceoffline

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppsActivity"
    }

    private lateinit var tvAppsCountBadge: TextView
    private lateinit var btnRefreshApps: Button
    private lateinit var btnBackFromApps: Button
    private lateinit var rvAppsList: RecyclerView
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        AppRegistry.init(this)

        tvAppsCountBadge = findViewById(R.id.tvAppsCountBadge)
        btnRefreshApps = findViewById(R.id.btnRefreshApps)
        btnBackFromApps = findViewById(R.id.btnBackFromApps)
        rvAppsList = findViewById(R.id.rvAppsList)

        rvAppsList.layoutManager = LinearLayoutManager(this)
        adapter = AppsAdapter { app -> showAppActionsDialog(app) }
        rvAppsList.adapter = adapter

        btnRefreshApps.setOnClickListener {
            refreshAppsList()
            Toast.makeText(this, "Список приложений обновлен", Toast.LENGTH_SHORT).show()
        }

        btnBackFromApps.setOnClickListener {
            finish()
        }

        refreshAppsList()
    }

    private fun refreshAppsList() {
        AppRegistry.init(this)
        val apps = AppRegistry.getAllApps().sortedWith(
            compareByDescending<InstalledApp> { it.isUserCustom }
                .thenBy { it.displayName.lowercase() }
        )

        tvAppsCountBadge.text = "${apps.size} приложений"
        adapter.submitList(apps)
        if (apps.isNotEmpty()) {
            rvAppsList.post {
                rvAppsList.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun showAppActionsDialog(app: InstalledApp) {
        val userAliases = AppRegistry.getUserAliases(this, app.key)
        val searchAdapter = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.getAdapterForPackage(app.packageName)
        val options = mutableListOf(
            "🚀 Запустить приложение",
            "➕ Добавить голосовой алиас"
        )
        if (userAliases.isNotEmpty()) {
            options.add("🗑 Сбросить пользовательские алиасы (${userAliases.size})")
        }

        if (searchAdapter != null) {
            val isExcluded = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.isAppExcludedFromSearch(this, searchAdapter.targetAppKey)
            val toggleLabel = if (isExcluded) "🔍 Включить в голосовой поиск" else "🚫 Исключить из голосового поиска"
            options.add(toggleLabel)
            options.add("ℹ️ Метод поиска: ${searchAdapter.integrationMethod.name}")
        } else {
            options.add("🔍 Поиск пока не поддерживается")
        }

        AlertDialog.Builder(this, R.style.Theme_VisionVoice_Dialog)
            .setTitle(app.displayName)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> {
                        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            Toast.makeText(this, "Запуск «${app.displayName}»", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Не удалось открыть ${app.packageName}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> showAddAliasDialog(app)
                    2 -> {
                        if (userAliases.isNotEmpty()) {
                            AppRegistry.resetAppAliases(this, app.key)
                            refreshAppsList()
                            Toast.makeText(this, "Алиасы сброшены", Toast.LENGTH_SHORT).show()
                        } else if (searchAdapter != null) {
                            val isExcluded = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.isAppExcludedFromSearch(this, searchAdapter.targetAppKey)
                            com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.setAppExcludedFromSearch(this, searchAdapter.targetAppKey, !isExcluded)
                            refreshAppsList()
                            val msg = if (!isExcluded) "Приложение исключено из поиска" else "Приложение включено в поиск"
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> {
                        if (userAliases.isNotEmpty() && searchAdapter != null) {
                            val isExcluded = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.isAppExcludedFromSearch(this, searchAdapter.targetAppKey)
                            com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.setAppExcludedFromSearch(this, searchAdapter.targetAppKey, !isExcluded)
                            refreshAppsList()
                            val msg = if (!isExcluded) "Приложение исключено из поиска" else "Приложение включено в поиск"
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        } else if (searchAdapter != null) {
                            Toast.makeText(this, "${searchAdapter.displayName}: ${searchAdapter.lastVerificationStatus}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAddAliasDialog(app: InstalledApp) {
        val input = EditText(this).apply {
            hint = "Например: 'мой плеер', 'кино'"
            setTextColor(ContextCompat.getColor(this@AppsActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@AppsActivity, R.color.text_secondary))
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this, R.style.Theme_VisionVoice_Dialog)
            .setTitle("Добавить алиас для «${app.displayName}»")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val newAlias = input.text.toString().trim()
                if (newAlias.isNotEmpty()) {
                    AppRegistry.addUserAlias(this, app.key, newAlias)
                    refreshAppsList()
                    Toast.makeText(this, "Алиас '$newAlias' добавлен!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    inner class AppsAdapter(
        private val onAppClicked: (InstalledApp) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.AppViewHolder>() {

        private val items = mutableListOf<InstalledApp>()
        private val iconCache = mutableMapOf<String, Drawable?>()

        fun submitList(newItems: List<InstalledApp>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_entry, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivAppIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
            private val tvAppTitle: TextView = itemView.findViewById(R.id.tvAppTitle)
            private val tvAppCustomBadge: TextView = itemView.findViewById(R.id.tvAppCustomBadge)
            private val tvAppPackage: TextView = itemView.findViewById(R.id.tvAppPackage)
            private val tvAppAliases: TextView = itemView.findViewById(R.id.tvAppAliases)
            private val tvAppSearchSupport: TextView = itemView.findViewById(R.id.tvAppSearchSupport)

            fun bind(app: InstalledApp) {
                tvAppTitle.text = app.displayName
                tvAppPackage.text = app.packageName
                tvAppCustomBadge.visibility = if (app.isUserCustom) View.VISIBLE else View.GONE

                val aliasesStr = if (app.aliases.isNotEmpty()) {
                    "Алиасы: " + app.aliases.take(6).joinToString(", ") + (if (app.aliases.size > 6) "..." else "")
                } else {
                    "Алиасы: (по названию приложения)"
                }
                tvAppAliases.text = aliasesStr

                val searchAdapter = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.getAdapterForPackage(app.packageName)
                if (searchAdapter != null) {
                    val isExcluded = com.huawei.vision.voiceoffline.search.SearchAdapterRegistry.isAppExcludedFromSearch(itemView.context, searchAdapter.targetAppKey)
                    if (isExcluded) {
                        tvAppSearchSupport.text = "🔍 Поиск: Отключен в настройках (${searchAdapter.integrationMethod.name})"
                        tvAppSearchSupport.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_muted))
                    } else {
                        tvAppSearchSupport.text = "🔍 Поиск: Поддерживается • ${searchAdapter.integrationMethod.name}"
                        tvAppSearchSupport.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_cyan))
                    }
                } else {
                    tvAppSearchSupport.text = "🔍 Поиск пока не поддерживается"
                    tvAppSearchSupport.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_muted))
                }

                val cached = iconCache[app.packageName]
                if (cached != null) {
                    ivAppIcon.setImageDrawable(cached)
                } else {
                    try {
                        val icon = packageManager.getApplicationIcon(app.packageName)
                        iconCache[app.packageName] = icon
                        ivAppIcon.setImageDrawable(icon)
                    } catch (_: Exception) {
                        ivAppIcon.setImageResource(R.drawable.ic_overlay_symbol)
                    }
                }

                itemView.setOnClickListener {
                    onAppClicked(app)
                }
            }
        }
    }
}
