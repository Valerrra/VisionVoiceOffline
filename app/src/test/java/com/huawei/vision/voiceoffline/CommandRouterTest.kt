package com.huawei.vision.voiceoffline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRouterTest {

    @Test
    fun testTextNormalization() {
        val raw = "  ВКЛЮЧИ,   пожалуйста...  ЗВУК!!!  "
        val norm = CommandParser.normalize(raw)
        assertEquals("включи пожалуйста звук", norm)
    }

    @Test
    fun testRussianYoNormalization() {
        val raw = "всё ещё тёплый"
        val norm = CommandParser.normalize(raw)
        assertEquals("все еще теплый", norm)
    }

    // --- 1. Volume Control Tests ---

    @Test
    fun testVolumeMuteSynonyms() {
        val synonyms = listOf(
            "выключи звук",
            "выключить звук",
            "отключи звук",
            "без звука",
            "заглуши",
            "мьют",
            "тишина"
        )
        for (phrase in synonyms) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.VOLUME_CONTROL, cmd.type)
            assertEquals("Failed action for: $phrase", "MUTE", cmd.action)
        }
    }

    @Test
    fun testVolumeUnmuteSynonyms() {
        val synonyms = listOf(
            "включи звук",
            "включить звук",
            "верни звук",
            "размьют",
            "unmute"
        )
        for (phrase in synonyms) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.VOLUME_CONTROL, cmd.type)
            assertEquals("Failed action for: $phrase", "UNMUTE", cmd.action)
        }
    }

    @Test
    fun testVolumeUpSynonyms() {
        val synonyms = listOf(
            "сделай громче",
            "громче",
            "погромче",
            "прибавь звук",
            "прибавь громкость",
            "увеличь громкость",
            "добавь звук",
            "плюс громкость"
        )
        for (phrase in synonyms) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.VOLUME_CONTROL, cmd.type)
            assertEquals("Failed action for: $phrase", "UP", cmd.action)
        }
    }

    @Test
    fun testVolumeDownSynonyms() {
        val synonyms = listOf(
            "сделай тише",
            "тише",
            "потише",
            "убавь звук",
            "убавь громкость",
            "уменьши громкость",
            "сбавь звук"
        )
        for (phrase in synonyms) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.VOLUME_CONTROL, cmd.type)
            assertEquals("Failed action for: $phrase", "DOWN", cmd.action)
        }
    }

    // --- 2. App Launch Tests ---

    @Test
    fun testOpenSettingsVariationsAndStem() {
        val phrases = listOf(
            "открой настройки",
            "открой настрой", // Live stem recorded on KANT-359 in M3!
            "параметры",
            "настройки",
            "запусти настройки"
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.OPEN_APP, cmd.type)
            assertEquals("Failed target for: $phrase", "settings", cmd.target)
        }
    }

    @Test
    fun testOpenYouTubeVariations() {
        val phrases = listOf(
            "ютуб",
            "открой ютуб",
            "запусти ютуб",
            "youtube",
            "смарттьюб",
            "приложение ютуб"
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.OPEN_APP, cmd.type)
            assertEquals("Failed target for: $phrase", "youtube", cmd.target)
        }
    }

    @Test
    fun testOpenKinopoiskVariations() {
        val phrases = listOf(
            "кинопоиск",
            "приложение кинопоиск",
            "открой кинопоиск",
            "запусти кинопоиск"
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.OPEN_APP, cmd.type)
            assertEquals("Failed target for: $phrase", "kinopoisk", cmd.target)
        }
    }

    @Test
    fun testOpenLampaVariations() {
        val phrases = listOf(
            "лампа",
            "открой лампу",
            "запусти лампу",
            "lampa"
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.OPEN_APP, cmd.type)
            assertEquals("Failed target for: $phrase", "lampa", cmd.target)
        }
    }

    @Test
    fun testOpenHDRezkaVariations() {
        val phrases = listOf(
            "хдрезка",
            "резка",
            "открой резку",
            "hdrezka"
        )
        for (phrase in phrases) {
            val cmd = CommandParser.parse(phrase)
            assertEquals("Failed for: $phrase", CommandType.OPEN_APP, cmd.type)
            assertEquals("Failed target for: $phrase", "hdrezka", cmd.target)
        }
    }

    @Test
    fun testOpenBrowserAndFiles() {
        val bCmd = CommandParser.parse("открой браузер")
        assertEquals(CommandType.OPEN_APP, bCmd.type)
        assertEquals("browser", bCmd.target)

        val fCmd = CommandParser.parse("открой проводник")
        assertEquals(CommandType.OPEN_APP, fCmd.type)
        assertEquals("files", fCmd.target)
    }

    // --- 3. Media Control Tests ---

    @Test
    fun testMediaControls() {
        assertEquals("PAUSE", CommandParser.parse("пауза").action)
        assertEquals("PAUSE", CommandParser.parse("стоп").action)
        assertEquals("PAUSE", CommandParser.parse("поставь на паузу").action)

        assertEquals("PLAY", CommandParser.parse("плей").action)
        assertEquals("PLAY", CommandParser.parse("играй").action)
        assertEquals("PLAY", CommandParser.parse("продолжить").action)

        assertEquals("NEXT", CommandParser.parse("следующий").action)
        assertEquals("NEXT", CommandParser.parse("следующий трек").action)
        assertEquals("NEXT", CommandParser.parse("вперед").action)

        assertEquals("PREVIOUS", CommandParser.parse("предыдущий").action)
        assertEquals("PREVIOUS", CommandParser.parse("предыдущий трек").action)
    }

    // --- 4. Search and Query Extraction Tests ---

    @Test
    fun testSearchQueriesExtraction() {
        val cmd1 = CommandParser.parse("найди фильмы с Джеки Чаном")
        assertEquals(CommandType.SEARCH, cmd1.type)
        assertEquals("фильмы с джеки чаном", cmd1.params["query"])

        val cmd2 = CommandParser.parse("фильмы с Джеки Чаном")
        assertEquals(CommandType.SEARCH, cmd2.type)
        assertTrue((cmd2.params["query"] as String).contains("джеки чаном"))

        val cmd3 = CommandParser.parse("прогноз погоды на завтра")
        assertEquals(CommandType.SEARCH, cmd3.type)
        assertEquals("прогноз погоды на завтра", cmd3.params["query"])

        val cmd4 = CommandParser.parse("включи классическую музыку")
        assertEquals(CommandType.SEARCH, cmd4.type)
    }

    // --- 5. System Navigation Tests ---

    @Test
    fun testNavigationCommands() {
        val homeCmd = CommandParser.parse("домой")
        assertEquals(CommandType.SYSTEM_NAV, homeCmd.type)
        assertEquals("HOME", homeCmd.action)

        val home2 = CommandParser.parse("на главный экран")
        assertEquals(CommandType.SYSTEM_NAV, home2.type)
        assertEquals("HOME", home2.action)

        val backCmd = CommandParser.parse("назад")
        assertEquals(CommandType.SYSTEM_NAV, backCmd.type)
        assertEquals("BACK", backCmd.action)
    }

    // --- 6. Negative Scenarios & Ambiguity Protection ---

    @Test
    fun testNegativeAndAmbiguousInputs() {
        // Random non-command speech must be safely rejected
        val noise1 = CommandParser.parse("трактор в поле едет быстро")
        assertEquals(CommandType.UNKNOWN, noise1.type)
        assertEquals("no_match_found", noise1.matchedRule)

        // Numbers only
        val numbers = CommandParser.parse("один два три четыре пять")
        assertEquals(CommandType.UNKNOWN, numbers.type)

        // Incomplete command words without target
        val incomplete1 = CommandParser.parse("открой")
        assertEquals(CommandType.UNKNOWN, incomplete1.type)

        val incomplete2 = CommandParser.parse("запусти")
        assertEquals(CommandType.UNKNOWN, incomplete2.type)

        // Empty input
        val empty = CommandParser.parse("   ")
        assertEquals(CommandType.UNKNOWN, empty.type)
    }

    // --- 7. Dictation Mode Isolation Tests ---

    @Test
    fun testDictationModeIsolation() {
        val testPhrase = "выключи звук и открой настройки"
        val parsed = CommandParser.parse(testPhrase)
        val dictCmd = ParsedCommand(
            type = CommandType.DICTATION_TEXT,
            rawText = testPhrase,
            normalizedText = CommandParser.normalize(testPhrase),
            action = "NONE"
        )
        assertEquals(CommandType.DICTATION_TEXT, dictCmd.type)
        assertEquals("NONE", dictCmd.action)
    }

    // --- 8. M4.1 Regression Tests: Clipped Words, Rapid Key, Debounce ---

    @Test
    fun testClippedLastWordsAndStems() {
        // "открой настрой" -> Settings app launch
        val cmdSettings = CommandParser.parse("открой настрой")
        assertEquals(CommandType.OPEN_APP, cmdSettings.type)
        assertEquals("settings", cmdSettings.target)

        // "кинопои" -> Kinopoisk launch
        val cmdKinopoisk = CommandParser.parse("кинопои")
        assertEquals(CommandType.OPEN_APP, cmdKinopoisk.type)
        assertEquals("kinopoisk", cmdKinopoisk.target)

        // "сделай громч" -> Volume UP
        val cmdVolUp = CommandParser.parse("сделай громч")
        assertEquals(CommandType.VOLUME_CONTROL, cmdVolUp.type)
        assertEquals("UP", cmdVolUp.action)

        // "громч" -> Volume UP
        val cmdVolUpShort = CommandParser.parse("громч")
        assertEquals(CommandType.VOLUME_CONTROL, cmdVolUpShort.type)
        assertEquals("UP", cmdVolUpShort.action)

        // "тиш" -> Volume DOWN
        val cmdVolDown = CommandParser.parse("тиш")
        assertEquals(CommandType.VOLUME_CONTROL, cmdVolDown.type)
        assertEquals("DOWN", cmdVolDown.action)
    }

    @Test
    fun testLongDictationPhrasePreservation() {
        val longPhrase = "завтра в десять утра запланирована рабочая встреча"
        val parsed = CommandParser.parse(longPhrase)
        // Ensure rawText is completely intact without truncation
        assertEquals(longPhrase, parsed.rawText)
        assertEquals("завтра в десять утра запланирована рабочая встреча", parsed.normalizedText)
    }

    @Test
    fun testSttDiagnosticsModelIntegrity() {
        val diag = SttDiagnostics(
            timeKeyDownMs = 1000L,
            timeKeyUpMs = 2500L,
            timeFirstChunkMs = 1050L,
            timeLastChunkMs = 2650L,
            totalSamplesRead = 24000L,
            totalSamplesFed = 24000L,
            remainingQueueAtKeyUp = 5,
            timeInputFinishedMs = 2660L,
            tailDecodeIterations = 3,
            rawPartialStt = "открой настрой",
            rawFinalStt = "открой настройки",
            normalizedCommand = "открой настройки",
            chosenIntent = "APP_LAUNCH"
        )

        assertEquals(1500L, diag.timeKeyUpMs - diag.timeKeyDownMs)
        assertEquals(diag.totalSamplesRead, diag.totalSamplesFed)
        assertEquals("открой настройки", diag.rawFinalStt)
        assertEquals("APP_LAUNCH", diag.chosenIntent)

        val metrics = SttMetrics(
            audioDurationMs = 1500L,
            postReleaseLatencyMs = 180L,
            totalDecodeDurationMs = 450L,
            rtf = 0.30f,
            peakRamMb = 112.5,
            cpuTimeMs = 350L,
            finalTranscription = diag.rawFinalStt,
            diagnostics = diag
        )

        assertEquals(diag.rawFinalStt, metrics.finalTranscription)
        assertEquals(180L, metrics.postReleaseLatencyMs)
        assertEquals(diag, metrics.diagnostics)
    }

    // --- 9. M4.5 Overlay State Machine & Autostart Tests ---

    @Test
    fun testVoiceOverlayStateTransitions() {
        val states = VoiceOverlayState.values()
        assertEquals(5, states.size)
        assertTrue(states.contains(VoiceOverlayState.IDLE))
        assertTrue(states.contains(VoiceOverlayState.LISTENING))
        assertTrue(states.contains(VoiceOverlayState.PROCESSING))
        assertTrue(states.contains(VoiceOverlayState.RESULT))
        assertTrue(states.contains(VoiceOverlayState.ERROR))
    }
}
