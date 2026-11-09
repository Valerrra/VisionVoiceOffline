package com.huawei.vision.voiceoffline

import java.util.Locale

object CommandParser {

    private val LAUNCH_PREFIXES = listOf(
        "открой приложение", "откройте приложение",
        "запусти приложение", "запустите приложение",
        "включи приложение", "включите приложение",
        "открой", "откройте",
        "запусти", "запустите",
        "включи", "включите",
        "перейди в", "перейти в",
        "старт", "пуск"
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
        // Remove common punctuation
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

        // 1. Volume commands
        parseVolumeCommand(rawText, normalized)?.let { return it }

        // 2. Media control commands
        parseMediaCommand(rawText, normalized)?.let { return it }

        // 3. System navigation commands
        parseNavigationCommand(rawText, normalized)?.let { return it }

        // 4. App Launch commands
        parseAppLaunchCommand(rawText, normalized)?.let { return it }

        // 5. Search & Media query commands
        parseSearchCommand(rawText, normalized)?.let { return it }

        // 6. Unknown / Unambiguous rejection
        return ParsedCommand(
            type = CommandType.UNKNOWN,
            rawText = rawText,
            normalizedText = normalized,
            action = "UNKNOWN",
            confidence = 0.0f,
            matchedRule = "no_match_found"
        )
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
                params = mapOf("delta" to -1),
                confidence = 1.0f,
                matchedRule = "volume_down"
            )
        }

        return null
    }

    private fun parseMediaCommand(rawText: String, norm: String): ParsedCommand? {
        // PAUSE
        val pauseTriggers = listOf(
            "пауза", "стоп", "останови", "остановить", "поставь на паузу", "замри"
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

        // PLAY
        val playTriggers = listOf(
            "плей", "играй", "продолжить", "возобнови", "возобновить", "пуск", "играть"
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
            "следующий", "следующая", "следующее", "дальше", "вперед",
            "следующий трек", "следующее видео", "следующая песня", "переключи вперед"
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
            "назад", "вернись назад", "вернись", "назад в меню"
        )
        if (backTriggers.any { norm == it }) {
            return ParsedCommand(
                type = CommandType.SYSTEM_NAV,
                rawText = rawText,
                normalizedText = norm,
                action = "BACK",
                confidence = 1.0f,
                matchedRule = "nav_back"
            )
        }

        return null
    }

    private fun parseAppLaunchCommand(rawText: String, norm: String): ParsedCommand? {
        // Direct app name match
        AppRegistry.findAppByAlias(norm)?.let { app ->
            return ParsedCommand(
                type = CommandType.OPEN_APP,
                rawText = rawText,
                normalizedText = norm,
                action = "OPEN",
                target = app.key,
                params = mapOf("displayName" to app.displayName),
                confidence = 0.95f,
                matchedRule = "direct_app_alias"
            )
        }

        // Prefix-based launch: "открой ...", "запусти ...", "включи ..."
        for (prefix in LAUNCH_PREFIXES) {
            if (norm.startsWith("$prefix ")) {
                val remainder = norm.substring(prefix.length).trim()
                AppRegistry.findAppByAlias(remainder)?.let { app ->
                    return ParsedCommand(
                        type = CommandType.OPEN_APP,
                        rawText = rawText,
                        normalizedText = norm,
                        action = "OPEN",
                        target = app.key,
                        params = mapOf("displayName" to app.displayName),
                        confidence = 1.0f,
                        matchedRule = "prefixed_app_alias"
                    )
                }
            }
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

        // Topical queries: "фильмы с ...", "фильм ...", "сериал ...", "прогноз погоды ...", "погода ..."
        val topicalPrefixes = listOf(
            "фильмы с", "фильмы про", "фильмы", "фильм", "сериал", "кино",
            "прогноз погоды", "погода", "что такое", "кто такой", "музыка",
            "включи классическую музыку", "включи музыку", "включи песню"
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
