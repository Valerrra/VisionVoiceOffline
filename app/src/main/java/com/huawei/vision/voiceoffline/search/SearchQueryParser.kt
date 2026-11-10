package com.huawei.vision.voiceoffline.search

import java.util.Locale

object SearchQueryParser {

    data class ParsedQuery(
        val cleanQuery: String,
        val contentType: String? = null,
        val detectedTargetAppKey: String? = null,
        val detectedTargetDisplayName: String? = null
    )

    data class RepeatIntent(
        val isRepeat: Boolean,
        val targetAppKey: String? = null,
        val targetDisplayName: String? = null,
        val useSameTarget: Boolean = false,
        val requestAlternative: Boolean = false
    )

    data class RefinementIntent(
        val isRefinement: Boolean,
        val updatedQuery: String,
        val refinementType: RefinementType,
        val detectedToken: String = ""
    )

    private val CONTENT_QUALIFIERS = listOf(
        "сериал", "фильм", "мультфильм", "мультик", "кино", "видео",
        "клип", "ролик", "шоу", "передачу", "песню", "трек", "аниме"
    )

    private val SEARCH_LEAD_VERBS = listOf(
        "найди мне", "поищи мне", "ищи мне",
        "открой и найди", "открой и поищи", "включи и найди", "запусти и найди",
        "найди", "поищи", "ищи", "найти", "поиск", "искать",
        "включи", "запусти", "открой", "покажи"
    )

    // Season matching patterns
    private val SEASON_REGEX = Regex(
        """\b(первый|второй|третий|четвертый|четвёртый|пятый|шестой|седьмой|восьмой|девятый|десятый|\d+)\s+сезон\b|\bсезон\s+(\d+|[а-я]+)\b|\bпоследний\s+сезон\b""",
        RegexOption.IGNORE_CASE
    )

    // Year matching patterns
    private val YEAR_REGEX = Regex(
        """\b(\d{4}\s*год[а-я]*|две\s+тысячи\s+[а-я\s]+\s*год[а-я]*|за\s+\d{4}\s*год[а-я]*)\b""",
        RegexOption.IGNORE_CASE
    )

    // Audio / Language patterns
    private val AUDIO_LANG_REGEX = Regex(
        """\b(только\s+на\s+русском|на\s+русском|в\s+дубляже|в\s+оригинале|оригинал)\b""",
        RegexOption.IGNORE_CASE
    )

    // Subtitle patterns
    private val SUBTITLE_REGEX = Regex(
        """\b(с\s+субтитрами|с\s+русскими\s+субтитрами|без\s+субтитров)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parseInitialQuery(rawText: String): ParsedQuery {
        val resolved = SearchTargetResolver.resolve(rawText)
        var text = if (resolved.cleanQuery.isNotEmpty()) resolved.cleanQuery else rawText.trim()
        var norm = SearchTargetResolver.normalize(text)

        // Extract content qualifier
        var detectedContentType: String? = null
        for (cq in CONTENT_QUALIFIERS) {
            if (norm.startsWith("$cq ") || norm == cq) {
                detectedContentType = cq
                break
            }
        }

        // Clean leading search verbs
        var cleaned = norm
        for (verb in SEARCH_LEAD_VERBS) {
            if (cleaned.startsWith("$verb ")) {
                cleaned = cleaned.substring(verb.length).trim()
                break
            }
        }

        // Clean content qualifier from query if it's purely leading
        for (cq in CONTENT_QUALIFIERS) {
            if (cleaned.startsWith("$cq ")) {
                detectedContentType = cq
                cleaned = cleaned.substring(cq.length).trim()
                break
            }
        }

        // If after cleaning it's empty, revert to original non-empty text
        if (cleaned.isBlank()) {
            cleaned = norm
        }

        return ParsedQuery(
            cleanQuery = cleaned,
            contentType = detectedContentType,
            detectedTargetAppKey = resolved.targetAppKey,
            detectedTargetDisplayName = resolved.targetDisplayName
        )
    }

    fun detectRepeatIntent(rawText: String): RepeatIntent {
        val norm = SearchTargetResolver.normalize(rawText)

        // 1. "Поищи там же" / "Там же"
        val sameTargetPhrases = listOf(
            "там же", "поищи там же", "найди там же", "ищи там же",
            "попробуй там же", "повтори там же"
        )
        if (sameTargetPhrases.contains(norm)) {
            return RepeatIntent(
                isRepeat = true,
                useSameTarget = true
            )
        }

        // 2. "В другом приложении" / "Другое приложение"
        val altTargetPhrases = listOf(
            "в другом приложении", "другое приложение", "в другом",
            "попробуй в другом", "поищи в другом", "другое", "не здесь"
        )
        if (altTargetPhrases.contains(norm)) {
            return RepeatIntent(
                isRepeat = true,
                requestAlternative = true
            )
        }

        // 3. Repeat in specific target: "а теперь в [app]", "попробуй в [app]", "повтори в [app]"
        val repeatPrefixes = listOf(
            "а теперь в ", "а теперь на ", "теперь в ", "теперь на ",
            "попробуй в ", "попробуй на ", "повтори в ", "повтори на ",
            "повтори поиск в ", "повтори поиск на ", "поищи в ", "поищи на "
        )

        for (prefix in repeatPrefixes) {
            if (norm.startsWith(prefix)) {
                val candidateTarget = norm.substring(prefix.length).trim()
                val resolved = SearchTargetResolver.resolveTargetFromReply(candidateTarget)
                    ?: SearchTargetResolver.resolveTargetFromReply("на $candidateTarget")
                    ?: SearchTargetResolver.resolveTargetFromReply("в $candidateTarget")
                    ?: SearchTargetResolver.resolve(candidateTarget)
                if (resolved?.targetAppKey != null) {
                    return RepeatIntent(
                        isRepeat = true,
                        targetAppKey = resolved.targetAppKey,
                        targetDisplayName = resolved.targetDisplayName
                    )
                }
            }
        }

        return RepeatIntent(isRepeat = false)
    }

    fun detectRefinement(rawText: String, previousQuery: String): RefinementIntent {
        if (previousQuery.isBlank()) {
            return RefinementIntent(isRefinement = false, updatedQuery = previousQuery, refinementType = RefinementType.UNKNOWN)
        }

        val norm = SearchTargetResolver.normalize(rawText)
        var stripped = norm
        val leadPrefixes = listOf("а теперь ", "теперь ", "а ", "еще ", "ещё ")
        for (lead in leadPrefixes) {
            if (stripped.startsWith(lead)) {
                stripped = stripped.substring(lead.length).trim()
                break
            }
        }

        // 1. Season refinement
        val seasonMatch = SEASON_REGEX.find(stripped)
        if (seasonMatch != null) {
            val seasonToken = seasonMatch.value.trim()
            val prevSeasonMatch = SEASON_REGEX.find(previousQuery)

            val updated = if (prevSeasonMatch != null) {
                // If it already has the same season, do not duplicate!
                if (prevSeasonMatch.value.equals(seasonToken, ignoreCase = true)) {
                    previousQuery
                } else {
                    // Replace previous season
                    previousQuery.replace(prevSeasonMatch.value, seasonToken)
                }
            } else {
                "$previousQuery $seasonToken"
            }
            return RefinementIntent(
                isRefinement = true,
                updatedQuery = updated.trim(),
                refinementType = RefinementType.SEASON,
                detectedToken = seasonToken
            )
        }

        // 2. Year refinement
        val yearMatch = YEAR_REGEX.find(stripped)
        if (yearMatch != null) {
            val yearToken = yearMatch.value.trim()
            val prevYearMatch = YEAR_REGEX.find(previousQuery)

            val updated = if (prevYearMatch != null) {
                if (prevYearMatch.value.equals(yearToken, ignoreCase = true)) {
                    previousQuery
                } else {
                    previousQuery.replace(prevYearMatch.value, yearToken)
                }
            } else {
                "$previousQuery $yearToken"
            }
            return RefinementIntent(
                isRefinement = true,
                updatedQuery = updated.trim(),
                refinementType = RefinementType.YEAR,
                detectedToken = yearToken
            )
        }

        // 3. Audio / Language refinement
        val audioMatch = AUDIO_LANG_REGEX.find(stripped)
        if (audioMatch != null) {
            val audioToken = audioMatch.value.trim()
            val prevAudioMatch = AUDIO_LANG_REGEX.find(previousQuery)

            val updated = if (prevAudioMatch != null) {
                if (prevAudioMatch.value.equals(audioToken, ignoreCase = true)) {
                    previousQuery
                } else {
                    previousQuery.replace(prevAudioMatch.value, audioToken)
                }
            } else {
                "$previousQuery $audioToken"
            }
            return RefinementIntent(
                isRefinement = true,
                updatedQuery = updated.trim(),
                refinementType = RefinementType.LANGUAGE_AUDIO,
                detectedToken = audioToken
            )
        }

        // 4. Subtitle refinement
        val subMatch = SUBTITLE_REGEX.find(stripped)
        if (subMatch != null) {
            val subToken = subMatch.value.trim()
            val prevSubMatch = SUBTITLE_REGEX.find(previousQuery)

            val updated = if (prevSubMatch != null) {
                if (prevSubMatch.value.equals(subToken, ignoreCase = true)) {
                    previousQuery
                } else {
                    previousQuery.replace(prevSubMatch.value, subToken)
                }
            } else {
                "$previousQuery $subToken"
            }
            return RefinementIntent(
                isRefinement = true,
                updatedQuery = updated.trim(),
                refinementType = RefinementType.SUBTITLES,
                detectedToken = subToken
            )
        }

        // 5. Explicit "с [актером/режиссером]" or extra keywords
        val extraKeywordsPrefixes = listOf("с ", "в 4k", "в 1080p", "в хорошем качестве", "в высоком качестве")
        for (kw in extraKeywordsPrefixes) {
            if (stripped.startsWith(kw) || stripped == kw) {
                if (previousQuery.contains(stripped, ignoreCase = true)) {
                    return RefinementIntent(
                        isRefinement = true,
                        updatedQuery = previousQuery,
                        refinementType = RefinementType.EXTRA_KEYWORDS,
                        detectedToken = stripped
                    )
                } else {
                    return RefinementIntent(
                        isRefinement = true,
                        updatedQuery = "$previousQuery $stripped".trim(),
                        refinementType = RefinementType.EXTRA_KEYWORDS,
                        detectedToken = stripped
                    )
                }
            }
        }

        return RefinementIntent(
            isRefinement = false,
            updatedQuery = previousQuery,
            refinementType = RefinementType.UNKNOWN
        )
    }
}
