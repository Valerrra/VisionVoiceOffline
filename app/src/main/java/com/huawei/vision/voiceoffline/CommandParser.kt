package com.huawei.vision.voiceoffline

import java.util.Locale

object CommandParser {

    private val OPEN_PREFIXES = listOf(
        "открой приложение", "откройте приложение",
        "запусти приложение", "запустите приложение",
        "включи приложение", "включите приложение",
        "перейди в приложение", "перейти в приложение",
        "открой", "откройте",
        "запусти", "запустите",
        "включи", "включите",
        "перейди в", "перейти в",
        "старт", "пуск"
    )

    private val CLOSE_PREFIXES = listOf(
        "закрой приложение", "закройте приложение",
        "выход из приложения", "выйди из приложения",
        "останови приложение", "остановите приложение",
        "заверши приложение", "завершите приложение",
        "закрой", "закройте",
        "выход из", "выйди из",
        "останови", "остановите",
        "заверши", "завершите",
        "погаси", "погасить"
    )

    private val MINIMIZE_TRIGGERS = listOf(
        "сверни приложение", "свернуть приложение",
        "сверни в фон", "свернуть в фон",
        "в фон", "в фоновый режим",
        "сверни", "свернуть"
    )

    private val SEARCH_PREFIXES = listOf(
        "найди в интернете", "найди мне", "найди", "найдите",
        "поиск в интернете", "поиск",
        "ищи", "ищите",
        "покажи мне", "покажи", "покажите",
        "гугл", "яндекс"
    )

    fun normalize(text: String): String {
        var s = text.lowercase(Locale.ROOT)
        // Replace Russian ё with е
        s = s.replace('ё', 'е')
        // Remove common punctuation except hyphens
        s = s.replace(Regex("[^a-zа-я0-9\\s-]"), " ")
        // Collapse multiple whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    fun parse(rawText: String): ParsedCommand {
        val normalized = normalize(rawText)

        if (normalized.isEmpty()) {
            return ParsedCommand(
                type = CommandType.UNKNOWN,
                rawText = rawText,
                normalizedText = normalized,
                action = "EMPTY",
                confidence = 0.0f,
                matchedRule = "empty_input"
            )
        }

        // 1. Explicit Close Command ("закрой ...", "выход из ...")
        parseCloseCommand(rawText, normalized)?.let { return it }

        // 2. Explicit Minimize Command ("сверни ...", "в фон")
        parseMinimizeCommand(rawText, normalized)?.let { return it }

        // 3. Volume commands
        parseVolumeCommand(rawText, normalized)?.let { return it }

        // 4. Media control commands
        parseMediaCommand(rawText, normalized)?.let { return it }

        // 5. System navigation commands (HOME, BACK)
        parseNavigationCommand(rawText, normalized)?.let { return it }

        // 6. Explicit App Launch commands ("открой ...", "запусти ...") or direct standalone app name
        parseAppLaunchCommand(rawText, normalized)?.let { return it }

        // 7. Search & Media query commands ("найди ...", "фильмы с ...")
        parseSearchCommand(rawText, normalized)?.let { return it }

        // 8. Rejection / Unambiguous unknown
        return ParsedCommand(
            type = CommandType.UNKNOWN,
            rawText = rawText,
            normalizedText = normalized,
            action = "UNKNOWN",
            confidence = 0.0f,
            matchedRule = "no_match_found"
        )
    }

    private fun parseCloseCommand(rawText: String, norm: String): ParsedCommand? {
        for (prefix in CLOSE_PREFIXES) {
            if (norm == prefix) {
                return ParsedCommand(
                    type = CommandType.CLOSE_APP,
                    rawText = rawText,
                    normalizedText = norm,
                    action = "CLOSE_CURRENT",
                    confidence = 1.0f,
                    matchedRule = "close_current_app"
                )
            }
            if (norm.startsWith("$prefix ")) {
                val remainder = norm.substring(prefix.length).trim()
                if (remainder.isEmpty() || remainder == "приложение") {
                    return ParsedCommand(
                        type = CommandType.CLOSE_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "CLOSE_CURRENT",
                        confidence = 1.0f,
                        matchedRule = "close_current_app"
                    )
                }

                // Match specific app to close
                val match = AppRegistry.findApp(remainder)
                if (match != null) {
                    return ParsedCommand(
                        type = CommandType.CLOSE_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "CLOSE",
                        target = match.app.key,
                        params = mapOf(
                            "displayName" to match.app.appLabel,
                            "packageName" to match.app.packageName,
                            "matchedAlias" to match.matchedAlias,
                            "matchScore" to match.score,
                            "isAmbiguous" to match.isAmbiguous
                        ),
                        confidence = match.score,
                        matchedRule = "close_specific_app"
                    )
                } else {
                    return ParsedCommand(
                        type = CommandType.CLOSE_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "NOT_FOUND",
                        target = remainder,
                        params = mapOf("query" to remainder),
                        confidence = 0.8f,
                        matchedRule = "close_app_not_found"
                    )
                }
            }
        }
        return null
    }

    private fun parseMinimizeCommand(rawText: String, norm: String): ParsedCommand? {
        if (MINIMIZE_TRIGGERS.any { norm == it || norm.startsWith("$it ") }) {
            return ParsedCommand(
                type = CommandType.MINIMIZE_APP,
                rawText = rawText,
                normalizedText = norm,
                action = "MINIMIZE",
                confidence = 1.0f,
                matchedRule = "minimize_app"
            )
        }
        return null
    }

    private fun parseVolumeCommand(rawText: String, norm: String): ParsedCommand? {
        // MUTE
        val muteTriggers = listOf(
            "выключи звук", "выключить звук", "отключи звук", "отключить звук",
            "без звука", "заглуши", "заглушить", "мьют", "mute", "тишина", "убери звук"
        )
        if (muteTriggers.any { norm == it || norm.startsWith("$it ") }) {
            return ParsedCommand(
                type = CommandType.VOLUME_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "MUTE",
                confidence = 1.0f,
                matchedRule = "volume_mute"
            )
        }

        // UNMUTE
        val unmuteTriggers = listOf(
            "включи звук", "включить звук", "верни звук", "вернуть звук",
            "размьют", "unmute"
        )
        if (unmuteTriggers.any { norm == it || norm.startsWith("$it ") }) {
            return ParsedCommand(
                type = CommandType.VOLUME_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "UNMUTE",
                confidence = 1.0f,
                matchedRule = "volume_unmute"
            )
        }

        // VOLUME UP
        val volUpTriggers = listOf(
            "сделай громче", "сделать громче", "громче", "погромче",
            "сделай громч", "громч", "погромч",
            "прибавь звук", "прибавить звук", "прибавь громкость", "прибавить громкость",
            "увеличь громкость", "увеличить громкость", "добавь звук", "добавь громкость",
            "плюс громкость", "сделай погромче"
        )
        if (volUpTriggers.any { norm == it || norm.startsWith("$it ") }) {
            return ParsedCommand(
                type = CommandType.VOLUME_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "UP",
                params = mapOf("delta" to 1),
                confidence = 1.0f,
                matchedRule = "volume_up"
            )
        }

        // VOLUME DOWN
        val volDownTriggers = listOf(
            "сделай тише", "сделать тише", "тише", "потише",
            "сделай тиш", "тиш", "потиш",
            "убавь звук", "убавить звук", "убавь громкость", "убавить громкость",
            "уменьши громкость", "уменьшить громкость", "сбавь звук", "сбавить звук",
            "минус громкость", "сделай потише"
        )
        if (volDownTriggers.any { norm == it || norm.startsWith("$it ") }) {
            return ParsedCommand(
                type = CommandType.VOLUME_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "DOWN",
                params = mapOf("delta" to 1),
                confidence = 1.0f,
                matchedRule = "volume_down"
            )
        }

        return null
    }

    private fun parseMediaCommand(rawText: String, norm: String): ParsedCommand? {
        // PAUSE
        val pauseTriggers = listOf(
            "пауза", "поставь на паузу", "на паузу", "стоп видео", "приостанови", "останови видео", "стоп"
        )
        if (pauseTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.MEDIA_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "PAUSE",
                confidence = 1.0f,
                matchedRule = "media_pause"
            )
        }

        // PLAY / RESUME
        val playTriggers = listOf(
            "продолжи", "продолжить", "играй", "плей", "play", "воспроизведение", "запусти видео"
        )
        if (playTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.MEDIA_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "PLAY",
                confidence = 1.0f,
                matchedRule = "media_play"
            )
        }

        // NEXT
        val nextTriggers = listOf(
            "следующий", "следующая", "следующее", "вперед", "следующий трек",
            "следующая серия", "следующее видео", "переключи вперед"
        )
        if (nextTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.MEDIA_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "NEXT",
                confidence = 1.0f,
                matchedRule = "media_next"
            )
        }

        // PREVIOUS
        val prevTriggers = listOf(
            "предыдущий", "предыдущая", "предыдущее", "назад трек",
            "предыдущий трек", "предыдущая песня", "переключи назад"
        )
        if (prevTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.MEDIA_CONTROL,
                rawText = rawText,
                normalizedText = norm,
                action = "PREVIOUS",
                confidence = 1.0f,
                matchedRule = "media_prev"
            )
        }

        return null
    }

    private fun parseNavigationCommand(rawText: String, norm: String): ParsedCommand? {
        val homeTriggers = listOf(
            "домой", "на главный экран", "главный экран", "на главную",
            "рабочий стол", "выйти в меню", "главное меню"
        )
        if (homeTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.SYSTEM_NAV,
                rawText = rawText,
                normalizedText = norm,
                action = "HOME",
                confidence = 1.0f,
                matchedRule = "nav_home"
            )
        }

        val backTriggers = listOf(
            "назад", "вернись назад", "вернись", "назад в меню", "шаг назад", "отмена", "отмени"
        )
        if (backTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.SYSTEM_BACK,
                rawText = rawText,
                normalizedText = norm,
                action = "BACK",
                confidence = 1.0f,
                matchedRule = "nav_back"
            )
        }

        val recentsTriggers = listOf(
            "недавние", "недавние приложения", "последние приложения",
            "диспетчер задач", "список задач", "запущенные приложения",
            "открытые приложения", "переключатель приложений", "recents"
        )
        if (recentsTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.SYSTEM_NAV,
                rawText = rawText,
                normalizedText = norm,
                action = "RECENTS",
                confidence = 1.0f,
                matchedRule = "nav_recents"
            )
        }

        return null
    }

    private fun parseAppLaunchCommand(rawText: String, norm: String): ParsedCommand? {
        // 1. Prefix-based launch: "открой ...", "запусти ...", "включи ..."
        for (prefix in OPEN_PREFIXES) {
            if (norm.startsWith("$prefix ")) {
                val remainder = norm.substring(prefix.length).trim()
                if (remainder.isEmpty() || remainder == "приложение") {
                    return ParsedCommand(
                        type = CommandType.UNKNOWN,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "INCOMPLETE_OPEN",
                        confidence = 0.0f,
                        matchedRule = "incomplete_open"
                    )
                }

                val match = AppRegistry.findApp(remainder)
                if (match != null) {
                    return ParsedCommand(
                        type = CommandType.OPEN_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "OPEN",
                        target = match.app.key,
                        params = mapOf(
                            "displayName" to match.app.appLabel,
                            "packageName" to match.app.packageName,
                            "matchedAlias" to match.matchedAlias,
                            "matchScore" to match.score,
                            "isAmbiguous" to match.isAmbiguous
                        ),
                        confidence = match.score,
                        matchedRule = "prefixed_app_alias"
                    )
                }

                // If not matched to an installed app, check if it's a search/media query (e.g. "включи классическую музыку")
                val searchCandidate = parseSearchCommand(rawText, norm)
                if (searchCandidate != null) {
                    return searchCandidate
                }

                // If explicitly requested as application ("открой/запусти приложение ...") or specific app launch verbs
                if (prefix.contains("приложение") || prefix.startsWith("открой") || prefix.startsWith("запусти")) {
                    return ParsedCommand(
                        type = CommandType.OPEN_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "NOT_FOUND",
                        target = remainder,
                        params = mapOf("query" to remainder),
                        confidence = 0.7f,
                        matchedRule = "open_app_not_found"
                    )
                }
            }
        }

        // 2. Direct app name match without any prefix (ONLY for standalone high-confidence app names)
        val directMatch = AppRegistry.findApp(norm)
        if (directMatch != null && directMatch.score >= 0.95f) {
            return ParsedCommand(
                type = CommandType.OPEN_APP,
                rawText = rawText,
                normalizedText = norm,
                action = "OPEN",
                target = directMatch.app.key,
                params = mapOf(
                    "displayName" to directMatch.app.appLabel,
                    "packageName" to directMatch.app.packageName,
                    "matchedAlias" to directMatch.matchedAlias,
                    "matchScore" to directMatch.score,
                    "isAmbiguous" to directMatch.isAmbiguous
                ),
                confidence = directMatch.score,
                matchedRule = "direct_app_alias"
            )
        }

        return null
    }

    private fun parseSearchCommand(rawText: String, norm: String): ParsedCommand? {
        // Explicit search prefix: "найди ...", "поиск ...", etc.
        for (prefix in SEARCH_PREFIXES) {
            if (norm.startsWith("$prefix ")) {
                val query = norm.substring(prefix.length).trim()
                if (query.isNotEmpty()) {
                    return ParsedCommand(
                        type = CommandType.SEARCH,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "SEARCH",
                        params = mapOf("query" to query, "searchType" to "explicit_search"),
                        confidence = 1.0f,
                        matchedRule = "prefixed_search"
                    )
                }
            }
        }

        // Topical queries: "фильмы с ...", "сериал ...", "прогноз погоды ...", "включи музыку ..."
        val topicalPrefixes = listOf(
            "фильмы с", "фильмы про", "фильмы", "фильм", "сериал", "кино",
            "прогноз погоды", "погода", "что такое", "кто такой", "музыка",
            "включи классическую музыку", "включи музыку", "включи песню", "включи трек"
        )
        for (tp in topicalPrefixes) {
            if (norm == tp || norm.startsWith("$tp ")) {
                return ParsedCommand(
                    type = CommandType.SEARCH,
                    rawText = rawText,
                    normalizedText = norm,
                    action = "SEARCH",
                    params = mapOf("query" to norm, "searchType" to "topical_query"),
                    confidence = 0.9f,
                    matchedRule = "topical_search"
                )
            }
        }

        return null
    }
}
