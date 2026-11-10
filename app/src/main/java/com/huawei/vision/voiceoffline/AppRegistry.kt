package com.huawei.vision.voiceoffline

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object AppRegistry {

    val apps = listOf(
        AppAlias(
            key = "settings",
            displayName = "Настройки",
            aliases = listOf(
                "настройки", "настройка", "настройку", "настрой",
                "параметры", "опции", "системные настройки"
            ),
            candidatePackages = listOf(
                "com.android.tv.settings",
                "com.android.settings"
            ),
            fallbackAction = Settings.ACTION_SETTINGS
        ),
        AppAlias(
            key = "youtube",
            displayName = "YouTube",
            aliases = listOf(
                "ютуб", "youtube", "ютуп", "ютюб", "ютубе",
                "смарттьюб", "смарт туб", "смарттуб", "newpipe"
            ),
            candidatePackages = listOf(
                "com.google.android.youtube.tv",
                "com.liskovsoft.smarttubetv.beta",
                "com.armanich.youtube",
                "org.schabi.newpipe"
            )
        ),
        AppAlias(
            key = "kinopoisk",
            displayName = "Кинопоиск",
            aliases = listOf(
                "кинопоиск", "кино поиск", "яндекс кино",
                "кинопои", "кинопоис", "кино"
            ),
            candidatePackages = listOf(
                "ru.kinopoisk.tv",
                "com.yandex.tv.kinopoisk",
                "com.kinopoisk"
            )
        ),
        AppAlias(
            key = "lampa",
            displayName = "Lampa",
            aliases = listOf(
                "лампа", "lampa", "лампу", "lmd"
            ),
            candidatePackages = listOf(
                "com.lazycatsoftware.lmd",
                "lampa.app",
                "top.mms.lampa"
            )
        ),
        AppAlias(
            key = "hdrezka",
            displayName = "HDRezka",
            aliases = listOf(
                "резка", "хдрезка", "hdrezka", "резку", "хд резка"
            ),
            candidatePackages = listOf(
                "to.pda.falcofemoralis.hdrezkaapp",
                "com.falcofemoralis.hdrezkaapp"
            )
        ),
        AppAlias(
            key = "browser",
            displayName = "Браузер",
            aliases = listOf(
                "браузер", "интернет", "веб", "browser"
            ),
            candidatePackages = listOf(
                "com.mi.globalbrowser",
                "org.mozilla.tv.firefox",
                "com.android.browser"
            )
        ),
        AppAlias(
            key = "files",
            displayName = "Проводник (X-plore)",
            aliases = listOf(
                "файлы", "проводник", "эксплорер", "xplore", "x-plore"
            ),
            candidatePackages = listOf(
                "com.lonelycatgames.Xplore",
                "com.android.documentsui"
            )
        ),
        AppAlias(
            key = "retroarch",
            displayName = "RetroArch",
            aliases = listOf(
                "ретроарч", "retroarch", "игры"
            ),
            candidatePackages = listOf(
                "com.retroarch"
            )
        ),
        AppAlias(
            key = "tvplayer",
            displayName = "Видеоплеер",
            aliases = listOf(
                "плеер", "видеоплеер", "видео", "плеер тв"
            ),
            candidatePackages = listOf(
                "com.huawei.tvplayer",
                "com.teamsmart.videomanager.tv"
            )
        )
    )

    fun findAppByAlias(query: String): AppAlias? {
        val q = query.trim().lowercase()
        return apps.firstOrNull { app ->
            app.aliases.any { alias ->
                q == alias || q == "приложение $alias" || q.endsWith(" $alias")
            }
        }
    }

    fun resolveInstalledPackage(context: Context, appAlias: AppAlias): String? {
        val pm = context.packageManager
        for (pkg in appAlias.candidatePackages) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    return pkg
                }
                // Try checking packageInfo
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Not installed, continue checking next candidate
            }
        }
        return null
    }
}
