package com.huawei.vision.voiceoffline

import android.content.Context
import com.huawei.vision.voiceoffline.search.DialogueManager
import com.huawei.vision.voiceoffline.search.DialogueState
import com.huawei.vision.voiceoffline.search.SearchAdapter
import com.huawei.vision.voiceoffline.search.SearchAdapterRegistry
import com.huawei.vision.voiceoffline.search.SearchExecutionResult
import com.huawei.vision.voiceoffline.search.SearchPolicy
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
        var available: Boolean = true
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
                message = "Поиск в $displayName: «$query»"
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
        private val fakePrefs = FakeSharedPreferences()
        override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = fakePrefs
        override fun getApplicationContext(): Context = this
    }

    private lateinit var mockYouTube: MockSearchAdapter
    private lateinit var mockLazyMedia: MockSearchAdapter
    private val dummyContext = DummyContext()

    @Before
    fun setUp() {
        SearchAdapterRegistry.resetForTesting()
        DialogueManager.resetSession()

        mockYouTube = MockSearchAdapter("youtube", "YouTube")
        mockLazyMedia = MockSearchAdapter("lazymedia", "LazyMedia Deluxe")

        SearchAdapterRegistry.registerAdapter(mockYouTube)
        SearchAdapterRegistry.registerAdapter(mockLazyMedia)
    }

    @After
    fun tearDown() {
        DialogueManager.resetSession()
        SearchAdapterRegistry.resetForTesting()
    }

    // --- 1. Phrasing Resolution Tests (Stage 6) ---

    @Test
    fun testYouTubePhrasingScenarios() {
        val testCases = mapOf(
            "Найди на Ютубе Варламова" to "варламова",
            "Поищи Варламова в Ютубе" to "варламова",
            "Ютуб, найди Варламова" to "варламова",
            "Покажи видео про ремонт Форд Мондео" to "ремонт форд мондео"
        )

        for ((phrase, expectedQuery) in testCases) {
            val resolved = SearchTargetResolver.resolve(phrase)
            if (phrase.contains("Ютуб") || phrase.contains("ютуб")) {
                assertEquals("Target failed for '$phrase'", "youtube", resolved.targetAppKey)
            }
            assertEquals("Query failed for '$phrase'", expectedQuery, resolved.cleanQuery)
        }
    }

    @Test
    fun testLazyMediaPhrasingScenarios() {
        val testCases = mapOf(
            "Найди в Лэйзи Медиа Доктора Хауса" to "доктора хауса",
            "Поищи Доктор Хаус в LazyMedia" to "доктор хаус",
            "Открой Лэйзи Медиа и найди Доктора Хауса" to "доктора хауса"
        )

        for ((phrase, expectedQuery) in testCases) {
            val resolved = SearchTargetResolver.resolve(phrase)
            assertEquals("Target failed for '$phrase'", "lazymedia", resolved.targetAppKey)
            assertEquals("Query failed for '$phrase'", expectedQuery, resolved.cleanQuery)
        }
    }

    @Test
    fun testKinopoiskPhrasingScenarios() {
        val testCases = mapOf(
            "Найди на Кинопоиске Интерстеллар" to "интерстеллар",
            "Поищи фильм Интерстеллар на Кинопоиске" to "интерстеллар"
        )

        for ((phrase, expectedQuery) in testCases) {
            val resolved = SearchTargetResolver.resolve(phrase)
            assertEquals("Target failed for '$phrase'", "kinopoisk", resolved.targetAppKey)
            assertEquals("Query failed for '$phrase'", expectedQuery, resolved.cleanQuery)
        }
    }

    @Test
    fun testAmbiguousSearchPhrasing() {
        val ambiguousPhrases = listOf(
            "Найди Доктора Хауса",
            "Покажи фильмы с Джеки Чаном",
            "Хочу посмотреть Назад в будущее"
        )

        for (phrase in ambiguousPhrases) {
            val resolved = SearchTargetResolver.resolve(phrase)
            assertNull("Target should be null for ambiguous phrase '$phrase'", resolved.targetAppKey)
            assertTrue("Query should not be empty for '$phrase'", resolved.cleanQuery.isNotEmpty())
        }
    }

    @Test
    fun testDialogueReplyResolution() {
        val validReplies = mapOf(
            "в лэйзи медиа" to "lazymedia",
            "лейзи медиа" to "lazymedia",
            "лэйзи" to "lazymedia",
            "ютуб" to "youtube",
            "на ютубе" to "youtube",
            "кинопоиск" to "kinopoisk"
        )

        for ((reply, expectedKey) in validReplies) {
            val resolved = SearchTargetResolver.resolveTargetFromReply(reply)
            assertNotNull("Reply '$reply' should resolve", resolved)
            assertEquals("Target mismatch for '$reply'", expectedKey, resolved?.targetAppKey)
        }
    }

    // --- 2. Contextual Dialogue State Machine Tests (Stage 4) ---

    @Test
    fun testDialogueFullTwoTurnFlow() {
        // Turn 1: User says ambiguous query
        val turn1 = CommandParser.parse("Найди сериал Доктор Хаус")
        assertEquals(CommandType.SEARCH, turn1.type)
        assertNull(turn1.target)

        val res1 = DialogueManager.startSearchDialogue(
            query = "сериал Доктор Хаус",
            rawText = "Найди сериал Доктор Хаус",
            context = dummyContext
        )

        assertTrue(res1.handled)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res1.state)
        assertEquals("Где искать «сериал Доктор Хаус»?", res1.promptToUser)
        assertTrue(res1.availableOptions.contains("LazyMedia Deluxe"))
        assertTrue(res1.availableOptions.contains("YouTube"))
        assertTrue(DialogueManager.hasActiveSession(30))

        // Turn 2: User responds via PTT: "В Лэйзи Медиа"
        val res2 = DialogueManager.handleInput("В Лэйзи Медиа", dummyContext)

        assertTrue(res2.handled)
        assertEquals(DialogueState.SEARCH_COMPLETED, res2.state)
        assertTrue(res2.isActionComplete)
        assertNotNull(res2.executionResult)
        assertEquals("Поиск в LazyMedia Deluxe: «сериал Доктор Хаус»", res2.statusMessage)
        assertEquals("сериал Доктор Хаус", mockLazyMedia.lastQuery)
        assertEquals(1, mockLazyMedia.executeCount)

        // Verify session reset after completion
        assertFalse(DialogueManager.hasActiveSession(30))
        assertEquals(DialogueState.SEARCH_COMPLETED, DialogueManager.state)
    }

    @Test
    fun testDialogueCancellation() {
        DialogueManager.startSearchDialogue("Варламов", "Найди Варламов", dummyContext)
        assertTrue(DialogueManager.hasActiveSession(30))

        val cancelResult = DialogueManager.handleInput("отмена", dummyContext)
        assertTrue(cancelResult.handled)
        assertEquals(DialogueState.CANCELLED, cancelResult.state)
        assertEquals("Поиск отменён", cancelResult.statusMessage)
        assertFalse(DialogueManager.hasActiveSession(30))
    }

    @Test
    fun testDialogueRepeat() {
        DialogueManager.startSearchDialogue("Интерстеллар", "Найди Интерстеллар", dummyContext)
        val repeatResult = DialogueManager.handleInput("повтори", dummyContext)

        assertTrue(repeatResult.handled)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, repeatResult.state)
        assertEquals("Где искать «Интерстеллар»?", repeatResult.promptToUser)
        assertTrue(DialogueManager.hasActiveSession(30))
    }

    @Test
    fun testDialogueTargetNotInstalled() {
        DialogueManager.startSearchDialogue("Человек паук", "Найди Человек паук", dummyContext)

        // User asks for Kinopoisk which is registered but not installed on this TV
        val mockKinopoisk = MockSearchAdapter("kinopoisk", "Кинопоиск", available = false)
        SearchAdapterRegistry.registerAdapter(mockKinopoisk)

        val res = DialogueManager.handleInput("Кинопоиск", dummyContext)

        assertTrue(res.handled)
        assertEquals(DialogueState.AWAITING_SEARCH_TARGET, res.state)
        assertTrue(res.statusMessage.contains("не установлено"))
        // Session must be preserved so user can choose an installed app
        assertTrue(DialogueManager.hasActiveSession(30))
    }

    @Test
    fun testDialogueTimeout() {
        val session = com.huawei.vision.voiceoffline.search.SearchSession(
            query = "test",
            rawText = "find test",
            createdAt = System.currentTimeMillis() - 35000L // 35 seconds ago
        )
        assertTrue("Session older than 30s must be expired", session.isExpired(30))
        assertFalse("Session must not be expired for 60s timeout", session.isExpired(60))
    }
}
