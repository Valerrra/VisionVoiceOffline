# Руководство по разработке поисковых адаптеров (Search Adapters)

Модуль универсального голосового поиска **VisionVoiceOffline** построен на расширяемой архитектуре адаптеров (`SearchAdapter`). Каждый адаптер отвечает за взаимодействие с конкретным сторонним мультимедийным приложением для Android TV.

---

## 1. Архитектурные принципы и безопасность

При добавлении новых адаптеров строго соблюдаются следующие принципы:

1. **Исключительно стандартные Intent и Deep Link:**  
   Поиск осуществляется через штатные механизмы Android:
   - **Deep Link (`ACTION_VIEW`):** Открытие зарегистрированного URI (например, `https://www.youtube.com/results?search_query=...`).
   - **Exported Intent:** Запуск активности приложения с явным указанием `ComponentName` и дополнительных параметров (`putExtra("query", ...)`).
2. **Запрет на инспекцию окон (Screen Scraping):**  
   Адаптерам **запрещено** использовать интерфейс `AccessibilityNodeInfo` для чтения содержимого экранов других приложений или эмуляции кликов. Служба `VisionVoiceAccessibilityService` изолирована и работает с флагом `canRetrieveWindowContent="false"`.
3. **Безопасность разрешений:**  
   Адаптеры не должны требовать или предоставлять сторонним приложениям произвольный доступ к системным или опасным разрешениям.
4. **Неблокирующий запуск:**  
   Запуск интента выполняется с флагами `FLAG_ACTIVITY_NEW_TASK` и `FLAG_ACTIVITY_CLEAR_TOP`, чтобы исключить утечки фокуса или зависания стека активностей телевизора.

---

## 2. Интерфейс `SearchAdapter`

Каждый поисковый адаптер реализует контракт `SearchAdapter`:

```kotlin
package com.huawei.vision.voiceoffline.search

import android.content.Context

interface SearchAdapter {
    /** Уникальный идентификатор приложения (например: "youtube", "lazymedia", "kinopoisk") */
    val targetAppKey: String

    /** Отображаемое имя в голосовом оверлее и диалогах (например: "YouTube") */
    val displayName: String

    /** Список пакетов-кандидатов (включая форки и альтернативные версии) */
    val candidatePackages: List<String>

    /** Метод интеграции: DEEP_LINK или EXPORTED_INTENT */
    val integrationMethod: IntegrationMethod

    /** Статус последней проверки на физическом оборудовании */
    val lastVerificationStatus: String

    /** Проверяет, установлено ли хотя бы одно совместимое приложение */
    fun isAvailable(context: Context): Boolean

    /** Выполняет запуск поиска по переданному текстовому запросу */
    fun executeSearch(context: Context, query: String): SearchExecutionResult
}
```

### Модели результатов

Вызов `executeSearch` возвращает `SearchExecutionResult`:
- `SearchExecutionResult.success(...)` — интент успешно сформирован и отправлен в систему.
- `SearchExecutionResult.error(...)` — ошибка (пустой запрос, отсутствие приложения, `ActivityNotFoundException`).

---

## 3. Реестр адаптеров `SearchAdapterRegistry`

Реестр управляет списком всех доступных адаптеров:
- Регистрация стандартных адаптеров выполняется в `SearchAdapterRegistry.init()`.
- Регистрация внешних адаптеров: `SearchAdapterRegistry.registerAdapter(MyNewAdapter())`.
- Проверка доступности: `SearchAdapterRegistry.getAvailableAdapters(context)`.
- Исключение приложений: пользователи могут отключить приложение из голосового поиска в настройках (`isAppExcludedFromSearch`).

---

## 4. Примеры реализации

### А. Адаптер на базе Deep Link (на примере YouTube)

```kotlin
class YouTubeSearchAdapter : SearchAdapter {
    override val targetAppKey = "youtube"
    override val displayName = "YouTube"
    override val candidatePackages = listOf(
        "com.google.android.youtube.tv",
        "com.google.android.youtube",
        "com.liskovsoft.videomanager",
        "com.liskovsoft.smarttubetv.beta"
    )
    override val integrationMethod = IntegrationMethod.DEEP_LINK
    override val lastVerificationStatus = "Проверено на Huawei Vision S (ACTION_VIEW Leanback deep link)"

    override fun isAvailable(context: Context): Boolean {
        return candidatePackages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun executeSearch(context: Context, query: String): SearchExecutionResult {
        val clean = query.trim()
        if (clean.isEmpty()) {
            return SearchExecutionResult.error(displayName, query, SearchError.EXECUTION_FAILED, "Пустой поисковый запрос")
        }

        return try {
            val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(clean)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                setPackage("com.google.android.youtube.tv")
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                intent.setPackage(null)
            }
            context.startActivity(intent)
            SearchExecutionResult.success(displayName, clean, "Поиск в YouTube: «$clean»", integrationMethod)
        } catch (e: Exception) {
            SearchExecutionResult.error(displayName, clean, SearchError.EXECUTION_FAILED, e.message ?: "Ошибка запуска")
        }
    }
}
```

### Б. Адаптер на базе Exported Intent (на примере LazyMedia Deluxe)

```kotlin
class LazyMediaSearchAdapter : SearchAdapter {
    override val targetAppKey = "lazymedia"
    override val displayName = "LazyMedia Deluxe"
    override val candidatePackages = listOf(
        "com.lazycatsoftware.lmd",
        "com.lazycatsoftware.lazymediadeluxe"
    )
    override val integrationMethod = IntegrationMethod.EXPORTED_INTENT
    override val lastVerificationStatus = "Проверено на Huawei Vision S (ActivityTvSearch extra query)"

    override fun isAvailable(context: Context): Boolean {
        return candidatePackages.any { pkg ->
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun executeSearch(context: Context, query: String): SearchExecutionResult {
        val clean = query.trim()
        return try {
            val installedPkg = candidatePackages.firstOrNull { pkg ->
                try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            } ?: candidatePackages.first()

            val intent = Intent().apply {
                component = ComponentName(installedPkg, "com.lazycatsoftware.lazymediadeluxe.ui.tv.activities.ActivityTvSearch")
                putExtra("query", clean)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            SearchExecutionResult.success(displayName, clean, "Поиск в LazyMedia Deluxe: «$clean»", integrationMethod)
        } catch (e: Exception) {
            SearchExecutionResult.error(displayName, clean, SearchError.EXECUTION_FAILED, e.message ?: "Ошибка запуска")
        }
    }
}
```

---

## 5. Добавление голосовых паттернов в `SearchTargetResolver`

Чтобы парсер понимал естественную русскую речь («найди в приложении X фильм Y»), добавьте имя и склонения в `SearchTargetResolver.kt`:

```kotlin
TargetPattern(
    appKey = "kinopoisk",
    displayName = "Кинопоиск",
    prepositionalPhrases = listOf(
        "на кинопоиске", "в кинопоиске", "по кинопоиску",
        "на кинопоиск", "в кинопоиск", "на яндекс кино"
    ),
    directNames = listOf(
        "кинопоиск", "кино поиск", "яндекс кино", "кинопоиске"
    )
)
```

---

## 6. Тестирование и отправка Pull Request

1. **Unit-тесты:**  
   Добавьте сценарии распознавания запросов в `VoiceSearchDialogueTest.kt`:
   ```kotlin
   @Test
   fun testNewAppSearchRecognition() {
       val parsed = SearchQueryParser.parseSearchCommand("найди в новомприложении сериал друзья")
       assertEquals(SearchIntentType.DIRECT_TARGET, parsed.intentType)
       assertEquals("сериал друзья", parsed.cleanQuery)
   }
   ```
2. **Проверка компиляции и прогона тестов:**
   ```powershell
   ./gradlew testReleaseUnitTest
   ```
3. **Требования к Pull Request:**
   - Четкое указание имени пакета и проверенной версии приложения.
   - Подтверждение работоспособности на реальном ТВ.
   - Отсутствие нештатных зависимостей и обходных путей безопасности.
