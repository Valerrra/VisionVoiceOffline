package com.huawei.vision.voiceoffline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.util.Locale

object AppRegistry {

    private const val TAG = "AppRegistry"
    private const val PREFS_USER_ALIASES = "vision_voice_user_aliases"

    data class StaticAppRule(
        val key: String,
        val defaultDisplayName: String,
        val candidatePackages: List<String>,
        val russianAliases: List<String>,
        val fallbackAction: String? = null
    )

    private val STATIC_RULES = listOf(
        StaticAppRule(
            key = "settings",
            defaultDisplayName = "Настройки",
            candidatePackages = listOf(
                "com.android.tv.settings",
                "com.android.settings",
                "com.huawei.homevision.settings"
            ),
            russianAliases = listOf(
                "настройки", "настройка", "настройку", "настрой",
                "параметры", "опции", "системные настройки"
            ),
            fallbackAction = Settings.ACTION_SETTINGS
        ),
        StaticAppRule(
            key = "lazymedia",
            defaultDisplayName = "LazyMedia Deluxe",
            candidatePackages = listOf(
                "com.lazycatsoftware.lmd",
                "com.lazycatsoftware.lazymedia"
            ),
            russianAliases = listOf(
                "лэйзи медиа", "лейзи медиа", "лэйзимедиа", "лейзимедиа",
                "лэйзе медиа", "лейзе медиа", "лэйземедиа", "лейземедиа",
                "лэйзи", "лейзи", "лэйзе", "лейзе", "lazy media", "lazymedia", "лэйзик",
                "лэйзи медиа делюкс", "лейзи медиа делюкс", "lazymedia deluxe"
            )
        ),
        StaticAppRule(
            key = "byedpi",
            defaultDisplayName = "ByeByeDPI",
            candidatePackages = listOf(
                "io.github.dovecoteescapee.byedpi",
                "io.github.romanvht.byedpi",
                "com.github.byebyedpi"
            ),
            russianAliases = listOf(
                "байбай дипиай", "байбайдипиай", "бай бай ди пи ай",
                "бай дипиай", "байдипиай", "байбай", "бай бай",
                "bye bye dpi", "byebyedpi", "byedpi", "дипиай", "дпи"
            )
        ),
        StaticAppRule(
            key = "youtube",
            defaultDisplayName = "YouTube",
            candidatePackages = listOf(
                "com.google.android.youtube.tv",
                "com.liskovsoft.smarttubetv.beta",
                "com.armanich.youtube",
                "org.schabi.newpipe"
            ),
            russianAliases = listOf(
                "ютуб", "youtube", "ютуп", "ютьюб", "ютубе",
                "смарттьюб", "смарт туб", "смарттуб", "smarttube", "newpipe"
            )
        ),
        StaticAppRule(
            key = "kinopoisk",
            defaultDisplayName = "Кинопоиск",
            candidatePackages = listOf(
                "ru.kinopoisk.tv",
                "com.yandex.tv.kinopoisk",
                "com.kinopoisk"
            ),
            russianAliases = listOf(
                "кинопоиск", "кино поиск", "яндекс кино",
                "кинопои", "кинопоис", "кино"
            )
        ),
        StaticAppRule(
            key = "lampa",
            defaultDisplayName = "Lampa",
            candidatePackages = listOf(
                "ru.lampa",
                "com.lampa",
                "ru.lampa.tv",
                "com.lampa.app"
            ),
            russianAliases = listOf(
                "лампа", "лампу", "лампе", "lampa"
            )
        ),
        StaticAppRule(
            key = "hdrezka",
            defaultDisplayName = "HDRezka",
            candidatePackages = listOf(
                "to.pda.falcofemoralis.hdrezkaapp",
                "com.falcofemoralis.hdrezkaapp",
                "com.hdrezka.app"
            ),
            russianAliases = listOf(
                "хдрезка", "хд резка", "резка", "хдрезку", "хд резку", "резку",
                "hdrezka", "hd rezka", "эйчди резка"
            )
        ),
        StaticAppRule(
            key = "browser",
            defaultDisplayName = "Браузер",
            candidatePackages = listOf(
                "com.mi.globalbrowser",
                "com.android.browser",
                "org.mozilla.tv.firefox"
            ),
            russianAliases = listOf(
                "браузер", "интернет", "browser", "веб"
            )
        ),
        StaticAppRule(
            key = "files",
            defaultDisplayName = "Файловый менеджер",
            candidatePackages = listOf(
                "com.lonelycatgames.Xplore",
                "com.android.documentsui"
            ),
            russianAliases = listOf(
                "файлы", "проводник", "эксплорер", "иксплор", "xplore", "файловый менеджер"
            )
        ),
        StaticAppRule(
            key = "retroarch",
            defaultDisplayName = "RetroArch",
            candidatePackages = listOf(
                "com.retroarch"
            ),
            russianAliases = listOf(
                "ретроарч", "ретро арч", "retroarch", "игры"
            )
        ),
        StaticAppRule(
            key = "4pda",
            defaultDisplayName = "4PDA",
            candidatePackages = listOf(
                "ru.freeman42.app4pda",
                "ru.fourpda.client"
            ),
            russianAliases = listOf(
                "фопда", "4пда", "четыре пда", "4pda"
            )
        ),
        StaticAppRule(
            key = "openvpn",
            defaultDisplayName = "OpenVPN",
            candidatePackages = listOf(
                "net.openvpn.openvpn"
            ),
            russianAliases = listOf(
                "опенвпн", "опен впн", "openvpn", "впн"
            )
        ),
        StaticAppRule(
            key = "launchermanager",
            defaultDisplayName = "Launcher Manager",
            candidatePackages = listOf(
                "com.wolf.google.lm"
            ),
            russianAliases = listOf(
                "лаунчер менеджер", "лаунчер манагер", "лаунчер", "launcher manager"
            )
        )
    )

    private val dynamicApps = mutableListOf<InstalledApp>()
    private var isInitialized = false

    init {
        loadStaticRulesBaseline()
    }

    private fun loadStaticRulesBaseline() {
        dynamicApps.clear()
        for (rule in STATIC_RULES) {
            val aliases = mutableSetOf<String>()
            aliases.addAll(rule.russianAliases.map { normalize(it) })
            val normLabel = normalize(rule.defaultDisplayName)
            if (normLabel.isNotEmpty()) aliases.add(normLabel)
            dynamicApps.add(
                InstalledApp(
                    key = rule.key,
                    packageName = rule.candidatePackages.first(),
                    activityName = null,
                    appLabel = rule.defaultDisplayName,
                    aliases = aliases,
                    isSystemApp = rule.key == "settings",
                    fallbackAction = rule.fallbackAction
                )
            )
        }
    }

    @Synchronized
    fun init(context: Context) {
        val pm = context.packageManager
        val discovered = mutableMapOf<String, InstalledApp>()

        // 1. Scan Leanback (Android TV) launcher activities
        val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val leanbackList = pm.queryIntentActivities(leanbackIntent, 0)
        for (info in leanbackList) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue // Skip ourselves
            val label = info.loadLabel(pm).toString()
            val activity = info.activityInfo.name
            val appKey = pkg.substringAfterLast('.').lowercase()

            val entry = createInstalledAppEntry(pkg, activity, label, appKey, pm)
            discovered[pkg] = entry
        }

        // 2. Scan standard launcher activities (sideloaded phone/tablet apps)
        val standardIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val standardList = pm.queryIntentActivities(standardIntent, 0)
        for (info in standardList) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName) continue
            if (!discovered.containsKey(pkg)) {
                val label = info.loadLabel(pm).toString()
                val activity = info.activityInfo.name
                val appKey = pkg.substringAfterLast('.').lowercase()

                val entry = createInstalledAppEntry(pkg, activity, label, appKey, pm)
                discovered[pkg] = entry
            }
        }

        // 3. Ensure static system rules like Settings are always present
        for (rule in STATIC_RULES) {
            val installedPkg = rule.candidatePackages.firstOrNull { isPackageInstalled(pm, it) }
            val key = rule.key
            if (installedPkg != null) {
                val current = discovered[installedPkg]
                val aliases = mutableSetOf<String>()
                aliases.addAll(rule.russianAliases.map { normalize(it) })
                if (current != null) {
                    aliases.addAll(current.aliases)
                }

                val finalApp = InstalledApp(
                    key = key,
                    packageName = installedPkg,
                    activityName = current?.activityName,
                    appLabel = current?.appLabel ?: rule.defaultDisplayName,
                    aliases = aliases,
                    isSystemApp = isSystemPackage(installedPkg),
                    fallbackAction = rule.fallbackAction
                )
                discovered[installedPkg] = finalApp
            } else if (rule.fallbackAction != null) {
                // If setting or fallback action exists, register as available via fallback
                val finalApp = InstalledApp(
                    key = key,
                    packageName = rule.candidatePackages.first(),
                    activityName = null,
                    appLabel = rule.defaultDisplayName,
                    aliases = rule.russianAliases.map { normalize(it) }.toSet(),
                    isSystemApp = true,
                    fallbackAction = rule.fallbackAction
                )
                discovered[finalApp.packageName] = finalApp
            }
        }

        // 4. Load custom user aliases from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_USER_ALIASES, Context.MODE_PRIVATE)
        val allUserPrefs = prefs.all
        for ((prefKey, aliasValue) in allUserPrefs) {
            val alias = normalize(aliasValue.toString())
            if (alias.isNotEmpty()) {
                val match = discovered.values.firstOrNull { it.key == prefKey || it.packageName == prefKey }
                if (match != null) {
                    val updatedAliases = match.aliases.toMutableSet().apply { add(alias) }
                    discovered[match.packageName] = match.copy(aliases = updatedAliases, isUserCustom = true)
                }
            }
        }

        dynamicApps.clear()
        dynamicApps.addAll(discovered.values)
        isInitialized = true

        Log.i(TAG, "DynamicAppRegistry initialized: ${dynamicApps.size} launchable apps registered")
        for (a in dynamicApps) {
            Log.d(TAG, "  App: '${a.appLabel}' [${a.packageName}] -> Aliases: ${a.aliases}")
        }
    }

    private fun createInstalledAppEntry(
        pkg: String,
        activity: String,
        label: String,
        appKey: String,
        pm: PackageManager
    ): InstalledApp {
        // Check if there is a static rule for this package
        val matchedRule = STATIC_RULES.firstOrNull { rule -> rule.candidatePackages.contains(pkg) }

        val aliases = mutableSetOf<String>()

        // 1. Add normalized label
        val normLabel = normalize(label)
        if (normLabel.isNotEmpty()) {
            aliases.add(normLabel)
            // If label has multiple words, add first word or acronym
            val words = normLabel.split(' ').filter { it.length > 2 }
            if (words.size > 1) {
                aliases.add(words.first())
            }
        }

        // 2. Add rule-based phonetic Russian aliases if matched
        if (matchedRule != null) {
            aliases.addAll(matchedRule.russianAliases.map { normalize(it) })
        }

        // 3. Add transliterated Latin-to-Cyrillic variant if label is English
        val translit = transliterateLatinToCyrillic(normLabel)
        if (translit.isNotEmpty() && translit != normLabel) {
            aliases.add(translit)
        }

        val key = matchedRule?.key ?: appKey

        return InstalledApp(
            key = key,
            packageName = pkg,
            activityName = activity,
            appLabel = label,
            aliases = aliases,
            isSystemApp = isSystemPackage(pkg),
            fallbackAction = matchedRule?.fallbackAction
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("com.huawei.") ||
                packageName.startsWith("android")
    }

    fun normalize(text: String): String {
        var s = text.lowercase(Locale.ROOT)
        s = s.replace('ё', 'е')
        s = s.replace(Regex("[^a-zа-я0-9\\s-]"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    fun findApp(query: String): AppMatch? {
        val q = normalize(query)
        if (q.isEmpty()) return null

        val exactMatches = mutableListOf<AppMatch>()
        val partialMatches = mutableListOf<AppMatch>()

        // Pass 1: Check for exact alias matches (score 1.0) and "приложение <alias>" (score 0.98)
        for (app in dynamicApps) {
            for (alias in app.aliases) {
                if (q == alias) {
                    exactMatches.add(AppMatch(app, alias, score = 1.0f))
                    break
                }
                if (q == "приложение $alias" || q == "$alias приложение") {
                    exactMatches.add(AppMatch(app, alias, score = 0.98f))
                    break
                }
            }
        }

        if (exactMatches.isNotEmpty()) {
            if (exactMatches.size > 1) {
                // Ambiguity check: if multiple distinct packages matched with top score
                val first = exactMatches.first()
                val distinctPackages = exactMatches.map { it.app.packageName }.distinct()
                return if (distinctPackages.size > 1) {
                    first.copy(isAmbiguous = true)
                } else {
                    first
                }
            }
            return exactMatches.first()
        }

        // Pass 2: Prefix or partial match only when no exact match exists
        for (app in dynamicApps) {
            for (alias in app.aliases) {
                if (q.startsWith("$alias ") || alias.startsWith("$q ")) {
                    partialMatches.add(AppMatch(app, alias, score = 0.85f))
                    break
                }
            }
        }

        if (partialMatches.isNotEmpty()) {
            val distinctPackages = partialMatches.map { it.app.packageName }.distinct()
            val first = partialMatches.first()
            return if (distinctPackages.size > 1) {
                first.copy(isAmbiguous = true)
            } else {
                first
            }
        }

        // Fallback check: check static rules even if dynamic scan was not run yet
        for (rule in STATIC_RULES) {
            for (alias in rule.russianAliases) {
                val normAlias = normalize(alias)
                if (q == normAlias) {
                    val placeholder = InstalledApp(
                        key = rule.key,
                        packageName = rule.candidatePackages.first(),
                        activityName = null,
                        appLabel = rule.defaultDisplayName,
                        aliases = setOf(normAlias),
                        fallbackAction = rule.fallbackAction
                    )
                    return AppMatch(placeholder, normAlias, score = 1.0f)
                }
                if (q == "приложение $normAlias" || q == "$normAlias приложение") {
                    val placeholder = InstalledApp(
                        key = rule.key,
                        packageName = rule.candidatePackages.first(),
                        activityName = null,
                        appLabel = rule.defaultDisplayName,
                        aliases = setOf(normAlias),
                        fallbackAction = rule.fallbackAction
                    )
                    return AppMatch(placeholder, normAlias, score = 0.98f)
                }
            }
        }

        return null
    }

    fun getAllApps(): List<InstalledApp> = dynamicApps.toList()

    fun addUserAlias(context: Context, appKeyOrPackage: String, alias: String) {
        val norm = normalize(alias)
        if (norm.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_USER_ALIASES, Context.MODE_PRIVATE)
        prefs.edit().putString(appKeyOrPackage, norm).apply()

        val app = dynamicApps.firstOrNull { it.key == appKeyOrPackage || it.packageName == appKeyOrPackage }
        if (app != null) {
            val updated = app.copy(aliases = app.aliases + norm, isUserCustom = true)
            val index = dynamicApps.indexOf(app)
            dynamicApps[index] = updated
        }
    }

    fun removeUserAlias(context: Context, appKeyOrPackage: String) {
        val prefs = context.getSharedPreferences(PREFS_USER_ALIASES, Context.MODE_PRIVATE)
        prefs.edit().remove(appKeyOrPackage).apply()
        init(context)
    }

    fun registerAppForTesting(app: InstalledApp) {
        dynamicApps.removeAll { it.packageName == app.packageName || it.key == app.key }
        dynamicApps.add(app)
        isInitialized = true
    }

    fun resetForTesting() {
        loadStaticRulesBaseline()
        isInitialized = false
    }

    fun registerPackageReceiver(context: Context): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                Log.i(TAG, "Package event: $action, refreshing AppRegistry")
                if (ctx != null) {
                    init(ctx.applicationContext)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(receiver, filter)
        return receiver
    }

    fun transliterateLatinToCyrillic(latin: String): String {
        val map = mapOf(
            "shch" to "щ", "sh" to "ш", "ch" to "ч", "zh" to "ж",
            "yu" to "ю", "ya" to "я", "yo" to "е", "ye" to "е",
            "a" to "а", "b" to "б", "v" to "в", "g" to "г", "d" to "д",
            "e" to "е", "z" to "з", "i" to "и", "j" to "й", "k" to "к",
            "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п",
            "r" to "р", "s" to "с", "t" to "т", "u" to "у", "f" to "ф",
            "h" to "х", "c" to "к", "w" to "в", "x" to "кс", "y" to "и"
        )
        var result = latin
        for ((k, v) in map) {
            result = result.replace(k, v)
        }
        return result
    }
}
