package com.huawei.vision.voiceoffline.search

import com.huawei.vision.voiceoffline.AppRegistry
import java.util.Locale

object SearchTargetResolver {

    data class TargetPattern(
        val appKey: String,
        val displayName: String,
        val prepositionalPhrases: List<String>,
        val directNames: List<String>
    )

    private val TARGET_PATTERNS = listOf(
        TargetPattern(
            appKey = "youtube",
            displayName = "YouTube",
            prepositionalPhrases = listOf(
                "на ютубе", "в ютубе", "по ютубу", "в ютюбе", "на ютюбе",
                "в ютуб", "на ютуб", "в ютюб", "на ютюб", "по ютуб",
                "на youtube", "в youtube",
                "в смарттьюбе", "на смарттьюбе", "в smarttube", "на smarttube",
                "в ньюпайпе", "в newpipe"
            ),
            directNames = listOf(
                "ютуб", "youtube", "ютюб", "ютуп", "smarttube", "смарттьюб", "ньюпайп", "newpipe"
            )
        ),
        TargetPattern(
            appKey = "lazymedia",
            displayName = "LazyMedia Deluxe",
            prepositionalPhrases = listOf(
                "в лэйзи медиа делюкс", "в лейзи медиа делюкс", "в lazymedia deluxe",
                "в лэйзи медиа", "в лейзи медиа", "в лэйзимедиа", "в лейзимедиа",
                "в лэйзе медиа", "в лейзе медиа", "в лэйземедиа", "в лейземедиа",
                "на лэйзи медиа", "на лейзи медиа", "на лэйзе медиа", "на лейзе медиа",
                "в лэйзи", "в лейзи", "в лэйзе", "в лейзе",
                "в lazymedia", "в lazy media", "на lazymedia"
            ),
            directNames = listOf(
                "лэйзи медиа делюкс", "лейзи медиа делюкс", "lazymedia deluxe",
                "лэйзи медиа", "лейзи медиа", "лэйзимедиа", "лейзимедиа",
                "лэйзе медиа", "лейзе медиа", "лэйземедиа", "лейземедиа",
                "лэйзи", "лейзи", "лэйзе", "лейзе", "lazymedia", "lazy media"
            )
        ),
        TargetPattern(
            appKey = "kinopoisk",
            displayName = "Кинопоиск",
            prepositionalPhrases = listOf(
                "на кинопоиске", "в кинопоиске", "по кинопоиску",
                "на кинопоиск", "в кинопоиск", "на яндекс кино"
            ),
            directNames = listOf(
                "кинопоиск", "кино поиск", "яндекс кино"
            )
        ),
        TargetPattern(
            appKey = "lampa",
            displayName = "Lampa",
            prepositionalPhrases = listOf(
                "в лампе", "на лампе", "по лампе", "в лампу", "на лампу", "в lampa", "на lampa"
            ),
            directNames = listOf(
                "лампа", "лампу", "лампе", "lampa"
            )
        ),
        TargetPattern(
            appKey = "hdrezka",
            displayName = "HDRezka",
            prepositionalPhrases = listOf(
                "в хдрезке", "на хдрезке", "в резке", "на резке", "в хдрезка", "в hdrezka"
            ),
            directNames = listOf(
                "хдрезка", "хд резка", "резка", "hdrezka", "hd rezka"
            )
        )
    )

    private val SEARCH_LEAD_VERBS = listOf(
        "найди мне пожалуйста", "найди мне", "найдите пожалуйста", "найдите", "найди",
        "поищи мне пожалуйста", "поищи мне", "поищи пожалуйста", "поищи",
        "поиск в интернете", "поиск",
        "ищи мне", "ищи", "ищите",
        "покажи мне видео про", "покажи мне видео", "покажи видео про", "покажи видео о", "покажи видео",
        "покажи фильм", "покажи фильмы с", "покажи фильмы про", "покажи фильмы",
        "покажи сериал", "покажи сериалы",
        "покажи мне", "покажи", "покажите",
        "включи мне", "включи видео про", "включи видео", "включи",
        "хочу посмотреть фильм", "хочу посмотреть сериал", "хочу посмотреть",
        "смотреть фильм", "смотреть сериал", "смотреть"
    )

    fun normalize(text: String): String {
        var s = text.lowercase(Locale.ROOT).replace('ё', 'е')
        s = s.replace(Regex("[^a-zа-я0-9\\s-]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Resolves the target app if the user provides a standalone reply in a dialogue turn
     * e.g. "в лэйзи медиа", "лэйзи медиа", "ютуб", "на ютубе", "кинопоиск"
     */
    fun resolveTargetFromReply(replyText: String): ResolvedSearchTarget? {
        val norm = normalize(replyText)
        if (norm.isEmpty()) return null

        for (pattern in TARGET_PATTERNS) {
            for (prep in pattern.prepositionalPhrases) {
                if (norm == prep || norm == "приложение $prep") {
                    return ResolvedSearchTarget(
                        targetAppKey = pattern.appKey,
                        targetDisplayName = pattern.displayName,
                        cleanQuery = "",
                        matchedAlias = prep,
                        isExplicitTarget = true
                    )
                }
            }
            for (direct in pattern.directNames) {
                if (norm == direct || norm == "приложение $direct") {
                    return ResolvedSearchTarget(
                        targetAppKey = pattern.appKey,
                        targetDisplayName = pattern.displayName,
                        cleanQuery = "",
                        matchedAlias = direct,
                        isExplicitTarget = true
                    )
                }
            }
        }

        // Fallback check against dynamic AppRegistry
        val appMatch = AppRegistry.findApp(norm)
        if (appMatch != null && appMatch.score >= 0.85f) {
            return ResolvedSearchTarget(
                targetAppKey = appMatch.app.key,
                targetDisplayName = appMatch.app.displayName,
                cleanQuery = "",
                matchedAlias = appMatch.matchedAlias,
                isExplicitTarget = true
            )
        }

        return null
    }

    /**
     * Resolves target and cleans user query from a full voice sentence.
     * Examples:
     * - "найди на ютубе варламова" -> target: youtube, query: "варламова"
     * - "поищи варламова в ютубе" -> target: youtube, query: "варламова"
     * - "ютуб найди варламова" -> target: youtube, query: "варламова"
     * - "открой лэйзи медиа и найди доктор хаус" -> target: lazymedia, query: "доктор хаус"
     * - "найди сериал доктор хаус" -> target: null, query: "сериал доктор хаус"
     */
    fun resolve(phrase: String): ResolvedSearchTarget {
        var norm = normalize(phrase)

        // 1. Check for compound prefix: "открой [app] и найди [query]"
        val openAndFindRegex = Regex("^(?:открой|запусти)\\s+(.+?)\\s+и\\s+(?:найди|поищи|включи|покажи)\\s+(.+)$")
        val openMatch = openAndFindRegex.matchEntire(norm)
        if (openMatch != null) {
            val appPart = openMatch.groupValues[1].trim()
            val queryPart = openMatch.groupValues[2].trim()
            val target = resolveTargetFromReply(appPart)
            if (target != null) {
                return ResolvedSearchTarget(
                    targetAppKey = target.targetAppKey,
                    targetDisplayName = target.targetDisplayName,
                    cleanQuery = cleanQuery(queryPart),
                    matchedAlias = target.matchedAlias,
                    isExplicitTarget = true
                )
            }
        }

        // 2. Check for leading direct target name: "[app] найди [query]" / "[app] [verb] [query]"
        for (pattern in TARGET_PATTERNS) {
            for (direct in pattern.directNames) {
                if (norm.startsWith("$direct ")) {
                    val remainder = norm.substring(direct.length).trim()
                    for (verb in SEARCH_LEAD_VERBS) {
                        if (remainder.startsWith("$verb ")) {
                            val clean = remainder.substring(verb.length).trim()
                            if (clean.isNotEmpty()) {
                                return ResolvedSearchTarget(
                                    targetAppKey = pattern.appKey,
                                    targetDisplayName = pattern.displayName,
                                    cleanQuery = cleanQuery(clean),
                                    matchedAlias = direct,
                                    isExplicitTarget = true
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Extract search lead verb from beginning: "найди ...", "поищи ..."
        var activeQuery = norm
        var matchedVerb: String? = null
        for (verb in SEARCH_LEAD_VERBS) {
            if (activeQuery.startsWith("$verb ")) {
                matchedVerb = verb
                activeQuery = activeQuery.substring(verb.length).trim()
                break
            }
        }

        // 4. Check for target in prepositional phrase at start of query: "на ютубе варламова"
        for (pattern in TARGET_PATTERNS) {
            for (prep in pattern.prepositionalPhrases) {
                if (activeQuery.startsWith("$prep ")) {
                    val clean = activeQuery.substring(prep.length).trim()
                    return ResolvedSearchTarget(
                        targetAppKey = pattern.appKey,
                        targetDisplayName = pattern.displayName,
                        cleanQuery = cleanQuery(clean),
                        matchedAlias = prep,
                        isExplicitTarget = true
                    )
                }
            }
        }

        // 5. Check for target in prepositional phrase at END of query: "варламова в ютубе", "доктор хаус в лэйзи медиа"
        for (pattern in TARGET_PATTERNS) {
            for (prep in pattern.prepositionalPhrases) {
                if (activeQuery.endsWith(" $prep")) {
                    val clean = activeQuery.substring(0, activeQuery.length - prep.length).trim()
                    return ResolvedSearchTarget(
                        targetAppKey = pattern.appKey,
                        targetDisplayName = pattern.displayName,
                        cleanQuery = cleanQuery(clean),
                        matchedAlias = prep,
                        isExplicitTarget = true
                    )
                }
            }
        }

        // 6. No target found; target is ambiguous / unknown
        val cleaned = cleanQuery(activeQuery)
        return ResolvedSearchTarget(
            targetAppKey = null,
            targetDisplayName = null,
            cleanQuery = if (cleaned.isNotEmpty()) cleaned else norm,
            matchedAlias = null,
            isExplicitTarget = false
        )
    }

    private fun cleanQuery(text: String): String {
        var s = text.trim()
        // Strip common filler words at the start of search query if any
        val fillers = listOf("про", "о", "об", "видео про", "видео о", "фильм", "сериал")
        // Don't strip if the whole query is just the filler
        for (f in fillers) {
            if (s.startsWith("$f ") && s.length > f.length + 2) {
                if (f == "про" || f == "о" || f == "об" || f.startsWith("видео") || f == "фильм") {
                    s = s.substring(f.length).trim()
                    break
                }
            }
        }
        return s
    }
}
