package com.huawei.vision.voiceoffline

import android.content.Context
import com.huawei.vision.voiceoffline.search.DialogueManager
import com.huawei.vision.voiceoffline.search.DialogueState
import com.huawei.vision.voiceoffline.search.IntegrationMethod
import com.huawei.vision.voiceoffline.search.SearchAdapter
import com.huawei.vision.voiceoffline.search.SearchAdapterRegistry
import com.huawei.vision.voiceoffline.search.SearchExecutionResult
import com.huawei.vision.voiceoffline.search.SearchPolicy
import com.huawei.vision.voiceoffline.search.SearchQueryParser
import com.huawei.vision.voiceoffline.search.SearchTargetResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceSearchDialogueTest {

    private class MockSearchAdapter(
        override val targetAppKey: String,
        override val displayName: String,
        override val candidatePackages: List<String> = listOf("com.test.$targetAppKey"),
        var available: Boolean = true,
        override val integrationMethod: IntegrationMethod = IntegrationMethod.DEEP_LINK,
        override val lastVerificationStatus: String = "Mock Verified"
    ) : SearchAdapter {
        var lastQuery: String? = null
        var executeCount: Int = 0

        override fun isAvailable(context: Context): Boolean = available

        override fun executeSearch(context: Context, query: String): SearchExecutionResult {
            lastQuery = query
            executeCount++
            return SearchExecutionResult.success(
                targetApp = targetAppKey,
                query = query,
                message = "Поиск в $displayName: «$query»",
                method = integrationMethod
            )
        }
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = Editor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val map: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor { temp[key ?: ""] = values; return this }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor { temp[key ?: ""] = value; return this }
            override fun remove(key: String?): android.content.SharedPreferences.Editor { temp.remove(key); return this }
            override fun clear(): android.content.SharedPreferences.Editor { temp.clear(); return this }
            override fun commit(): Boolean { map.putAll(temp); return true }
            override fun apply() { map.putAll(temp) }
        }
    }

    private class DummyContext : android.content.ContextWrapper(null) {
        val fakePrefs = FakeSharedPreferences()
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = fakePrefs
        override fun getApplicationContext(): Context = this
    }

    private lateinit var mockYouTube: MockSearchAdapter
    private lateinit var mockLazyMedia: MockSearchAdapter
    private val dummyContext = DummyContext()

    @Before
    fun setUp() {
        SearchAdapterRegistry.resetForTesting()
        DialogueManager.clearMemory()

        mockYouTube = MockSearchAdapter("youtube", "YouTube", listOf("com.google.android.youtube.tv"))
        mockLazyMedia = MockSearchAdapter("lazymedia", "LazyMedia Deluxe", listOf("com.lazycatsoftware.lmd"))

        SearchAdapterRegistry.registerAdapter(mockYouTube)
        SearchAdapterRegistry.registerAdapter(mockLazyMedia)

        VisionVoiceAccessibilityService.currentActivePackage = null
    }

    @After
    fun tearDown() {
        DialogueManager.clearMemory()
        SearchAdapterRegistry.resetForTesting()
        VisionVoiceAccessibilityService.currentActivePackage = null
    }

    // --- Scenario 1: YouTube Active -> "Найди Доктор Кто" -> "Да" -> YouTube ---
    @Test
    fun testScenario1_ActiveYouTube_ConfirmationYes() {
        VisionVoiceAccessibilityService.currentActivePackage = "com.google.android.youtube.tv"
        val prefs = VoiceOverlayPreferences(dummyContext)
        prefs.searchPolicy = SearchPolicy.PREFER_ACTIVE

        val startRes = DialogueManager.startSearchDialogue("Доктор Кто", "Найди Доктор Кто", dummyContext)
        assertEquals(DialogueState.AWAITING_SEARCH_CONFIRMATION, startRes.state)
        assertEquals("Искать «Доктор Кто» в YouTube?", startRes.promptToUser)

        val confirmRes = DialogueManager.handleInput("Да", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, confirmRes.state)
        assertTrue(confirmRes.isActionComplete)
        assertEquals(1, mockYouTube.executeCount)
        assertEquals("Доктор Кто", mockYouTube.lastQuery)
    }

    @Test
    fun testScenario1_ActiveYouTube_ConfirmationCurrentApp() {
        VisionVoiceAccessibilityService.currentActivePackage = "com.google.android.youtube.tv"
        val prefs = VoiceOverlayPreferences(dummyContext)
        prefs.searchPolicy = SearchPolicy.PREFER_ACTIVE

        DialogueManager.startSearchDialogue("Интерстеллар", "Найди Интерстеллар", dummyContext)
        val confirmRes = DialogueManager.handleInput("В текущем приложении", dummyContext)

        assertEquals(DialogueState.SEARCH_COMPLETED, confirmRes.state)
        assertTrue(confirmRes.isActionComplete)
        assertEquals(1, mockYouTube.executeCount)
        assertEquals("Интерстеллар", mockYouTube.lastQuery)
    }

    // --- Scenario 2: YouTube Active -> "Найди Доктор Кто" -> "Лэйзи Медиа" -> LazyMedia ---
    @Test
    fun testScenario2_ActiveYouTube_SwitchToLazyMedia() {
        VisionVoiceAccessibilityService.currentActivePackage = "com.google.android.youtube.tv"
        val prefs = VoiceOverlayPreferences(dummyContext)
        prefs.searchPolicy = SearchPolicy.PREFER_ACTIVE

        DialogueManager.startSearchDialogue("Доктор Кто", "Найди Доктор Кто", dummyContext)
        val replyRes = DialogueManager.handleInput("В Лэйзи Медиа", dummyContext)

        assertEquals(DialogueState.SEARCH_COMPLETED, replyRes.state)
        assertTrue(replyRes.isActionComplete)
        assertEquals(1, mockLazyMedia.executeCount)
        assertEquals("Доктор Кто", mockLazyMedia.lastQuery)
        assertEquals(0, mockYouTube.executeCount)
    }

    // --- Scenario 3: YouTube Active -> "Найди Доктор Кто" -> "Нет" -> Rejects YouTube ---
    @Test
    fun testScenario3_ActiveYouTube_RejectShowsAlternatives() {
        VisionVoiceAccessibilityService.currentActivePackage = "com.google.android.youtube.tv"
        val prefs = VoiceOverlayPreferences(dummyContext)
        prefs.searchPolicy = SearchPolicy.PREFER_ACTIVE

        DialogueManager.startSearchDialogue("Доктор Кто", "Найди Доктор Кто", dummyContext)
        val rejectRes = DialogueManager.handleInput("Нет", dummyContext)

        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, rejectRes.state)
        // Should present alternatives without rejected YouTube
        assertEquals(listOf("LazyMedia Deluxe"), rejectRes.availableOptions)
    }

    // --- Scenario 4: "Найди на Ютубе осень в Норвегии 4К" -> Direct YouTube search ---
    @Test
    fun testScenario4_DirectYouTubeSearch() {
        val parsed = CommandParser.parse("Найди на Ютубе осень в Норвегии 4К")
        assertEquals(CommandType.SEARCH, parsed.type)
        val resolved = SearchTargetResolver.resolve("Найди на Ютубе осень в Норвегии 4К")
        assertEquals("youtube", resolved.targetAppKey)
        assertEquals("осень в норвегии 4к", resolved.cleanQuery)
    }

    // --- Scenario 5: "Найди сериал Доктор Хаус" (no active app) -> Target Clarification ---
    @Test
    fun testScenario5_AmbiguousQueryPrompt() {
        VisionVoiceAccessibilityService.currentActivePackage = null
        val prefs = VoiceOverlayPreferences(dummyContext)
        prefs.searchPolicy = SearchPolicy.PREFER_ACTIVE

        val res = DialogueManager.startSearchDialogue("сериал Доктор Хаус", "Найди сериал Доктор Хаус", dummyContext)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertEquals("Где искать «сериал Доктор Хаус»?", res.promptToUser)
    }

    // --- Scenario 6: Search completed in YouTube -> "А теперь в Лэйзи Медиа" -> repeats in LazyMedia ---
    @Test
    fun testScenario6_RepeatInAnotherApp() {
        mockYouTube.executeSearch(dummyContext, "Доктор Хаус")
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Хаус", mockYouTube.executeSearch(dummyContext, "Доктор Хаус"))

        assertTrue(DialogueManager.hasRecentSearchMemory(120))

        val repeatRes = DialogueManager.handleInput("А теперь в Лэйзи Медиа", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, repeatRes.state)
        assertEquals("Доктор Хаус", mockLazyMedia.lastQuery)
        assertEquals(1, mockLazyMedia.executeCount)
    }

    // --- Scenario 7: Search completed -> "Попробуй на Кинопоиске" ---
    @Test
    fun testScenario7_RepeatInKinopoiskUninstalled() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Интерстеллар", mockYouTube.executeSearch(dummyContext, "Интерстеллар"))

        val mockKinopoisk = MockSearchAdapter("kinopoisk", "Кинопоиск", available = false)
        SearchAdapterRegistry.registerAdapter(mockKinopoisk)

        val res = DialogueManager.handleInput("Попробуй на Кинопоиске", dummyContext)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertTrue(res.statusMessage.contains("не доступно"))
    }

    // --- Scenario 8: Search completed -> "А теперь второй сезон" -> Refines query ---
    @Test
    fun testScenario8_RefineSeason() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Кто", mockYouTube.executeSearch(dummyContext, "Доктор Кто"))

        val res = DialogueManager.handleInput("А теперь второй сезон", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Доктор Кто второй сезон", mockYouTube.lastQuery)
        assertEquals("Доктор Кто второй сезон", DialogueManager.dialogueContext.currentQuery)
    }

    // --- Scenario 9: Duplicate "второй сезон" does not duplicate string ---
    @Test
    fun testScenario9_DuplicateRefinementPrevention() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Кто второй сезон", mockYouTube.executeSearch(dummyContext, "Доктор Кто второй сезон"))

        val res = DialogueManager.handleInput("второй сезон", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Доктор Кто второй сезон", mockYouTube.lastQuery)
    }

    // --- Scenario 10: "Первый сезон" replaces "второй сезон" ---
    @Test
    fun testScenario10_ReplaceSeason() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Кто второй сезон", mockYouTube.executeSearch(dummyContext, "Доктор Кто второй сезон"))

        val res = DialogueManager.handleInput("первый сезон", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Доктор Кто первый сезон", mockYouTube.lastQuery)
    }

    // --- Scenario 11: "Только на русском" complements query ---
    @Test
    fun testScenario11_LanguageRefinement() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Кто", mockYouTube.executeSearch(dummyContext, "Доктор Кто"))

        val res = DialogueManager.handleInput("только на русском", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Доктор Кто только на русском", mockYouTube.lastQuery)
    }

    // --- Scenario 12: "Поищи там же" uses same source ---
    @Test
    fun testScenario12_SearchSameTarget() {
        DialogueManager.recordSearchExecution("lazymedia", "LazyMedia Deluxe", "Интерстеллар", mockLazyMedia.executeSearch(dummyContext, "Интерстеллар"))

        val res = DialogueManager.handleInput("Поищи там же", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Интерстеллар", mockLazyMedia.lastQuery)
        assertEquals(2, mockLazyMedia.executeCount)
    }

    // --- Scenario 13: "В другом приложении" shows alternatives ---
    @Test
    fun testScenario13_SearchOtherApp() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Интерстеллар", mockYouTube.executeSearch(dummyContext, "Интерстеллар"))

        val res = DialogueManager.handleInput("В другом приложении", dummyContext)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertEquals(listOf("LazyMedia Deluxe"), res.availableOptions)
    }

    // --- Scenario 14: Cancellation ---
    @Test
    fun testScenario14_Cancellation() {
        DialogueManager.startSearchDialogue("осень в Норвегии 4К", "Найди осень в Норвегии 4К", dummyContext)
        val cancelRes = DialogueManager.handleInput("отмена", dummyContext)

        assertTrue(cancelRes.handled)
        assertEquals(DialogueState.CANCELLED, cancelRes.state)
        assertFalse(DialogueManager.hasActiveSession(30))
    }

    // --- Scenario 15: Timeout expiration ---
    @Test
    fun testScenario15_TimeoutExpiration() {
        val oldSession = com.huawei.vision.voiceoffline.search.SearchSession(
            query = "Старый фильм",
            rawText = "найди",
            createdAt = System.currentTimeMillis() - 40000L
        )
        assertTrue(oldSession.isExpired(30))
    }

    // --- Scenario 16: Unknown target name prompts alternatives ---
    @Test
    fun testScenario16_UnknownTargetPrompt() {
        DialogueManager.startSearchDialogue("Фильм", "найди фильм", dummyContext)
        val res = DialogueManager.handleInput("В космосе", dummyContext)

        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertTrue(res.statusMessage.contains("Не удалось определить источник"))
    }

    // --- Scenario 17: App without search adapter ---
    @Test
    fun testScenario17_AppWithoutSearchAdapter() {
        DialogueManager.startSearchDialogue("Фильм", "найди фильм", dummyContext)
        // User asks for byebyedpi which has no search adapter
        val res = DialogueManager.handleInput("байбай дипиай", dummyContext)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertTrue(res.statusMessage.contains("не поддерживается"))
    }

    // --- Scenario 18: Window state change doesn't break active dialogue ---
    @Test
    fun testScenario18_WindowStatePreservation() {
        DialogueManager.startSearchDialogue("Матрица", "найди матрицу", dummyContext)
        // User or system switches window
        VisionVoiceAccessibilityService.currentActivePackage = "com.other.app"

        assertTrue(DialogueManager.hasActiveSession(30))
        val res = DialogueManager.handleInput("В Лэйзи Медиа", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Матрица", mockLazyMedia.lastQuery)
    }

    // --- Scenario 19: Command "громче" bypasses dialogue and leaves memory intact ---
    @Test
    fun testScenario19_VolumeBypassesDialogue() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Доктор Кто", mockYouTube.executeSearch(dummyContext, "Доктор Кто"))

        val router = CommandRouter(dummyContext)
        val volumeResult = router.route("громче")

        assertEquals(CommandType.VOLUME_CONTROL, volumeResult.command.type)
        assertTrue(DialogueManager.hasRecentSearchMemory(120))
        assertEquals("Доктор Кто", DialogueManager.dialogueContext.currentQuery)
    }

    // --- Scenario 20: Missing prior context for follow-up ---
    @Test
    fun testScenario20_MissingPriorContext() {
        DialogueManager.clearMemory()
        val res = DialogueManager.handleInput("А теперь второй сезон", dummyContext)
        assertEquals(DialogueState.ERROR, res.state)
        assertTrue(res.statusMessage.contains("Предыдущий поиск не найден"))
    }

    // --- Scenario 21: Subtitle refinement ---
    @Test
    fun testScenario21_SubtitleRefinement() {
        DialogueManager.recordSearchExecution("youtube", "YouTube", "Аватар", mockYouTube.executeSearch(dummyContext, "Аватар"))

        val res = DialogueManager.handleInput("с субтитрами", dummyContext)
        assertEquals(DialogueState.SEARCH_COMPLETED, res.state)
        assertEquals("Аватар с субтитрами", mockYouTube.lastQuery)
    }

    // --- Scenario 22: Content query via "открой доктор кто" ---
    @Test
    fun testScenario22_OpenContentTitleTreatedAsSearch() {
        val parsed = CommandParser.parse("открой доктор кто")
        assertEquals(CommandType.SEARCH, parsed.type)
        assertEquals("доктор кто", parsed.params["query"])
    }

    // --- Scenario 23: Search exclusion toggle in SearchAdapterRegistry ---
    @Test
    fun testScenario23_SearchExclusion() {
        assertFalse(SearchAdapterRegistry.isAppExcludedFromSearch(dummyContext, "youtube"))
        SearchAdapterRegistry.setAppExcludedFromSearch(dummyContext, "youtube", true)
        assertTrue(SearchAdapterRegistry.isAppExcludedFromSearch(dummyContext, "youtube"))

        val available = SearchAdapterRegistry.getAvailableAdapters(dummyContext)
        assertFalse(available.any { it.targetAppKey == "youtube" })

        SearchAdapterRegistry.setAppExcludedFromSearch(dummyContext, "youtube", false)
        val availableRestored = SearchAdapterRegistry.getAvailableAdapters(dummyContext)
        assertTrue(availableRestored.any { it.targetAppKey == "youtube" })
    }

    // --- Scenario 24: Direct repetition check helper ---
    @Test
    fun testScenario24_RepeatIntentParser() {
        val repeat1 = SearchQueryParser.detectRepeatIntent("а теперь в лэйзи медиа")
        assertTrue(repeat1.isRepeat)
        assertEquals("lazymedia", repeat1.targetAppKey)

        val repeat2 = SearchQueryParser.detectRepeatIntent("поищи там же")
        assertTrue(repeat2.isRepeat)
        assertTrue(repeat2.useSameTarget)

        val repeat3 = SearchQueryParser.detectRepeatIntent("в другом приложении")
        assertTrue(repeat3.isRepeat)
        assertTrue(repeat3.requestAlternative)
    }
}
