package com.huawei.vision.voiceoffline

data class SttMetrics(
    val audioDurationMs: Long = 0,
    val postReleaseLatencyMs: Long = 0,
    val totalDecodeDurationMs: Long = 0,
    val rtf: Float = 0.0f,
    val peakRamMb: Double = 0.0,
    val cpuTimeMs: Long = 0,
    val finalTranscription: String = ""
)

data class TestPhrase(
    val index: Int,
    val total: Int,
    val category: String,
    val phrase: String,
    val hint: String
)

object TestPhrases {
    val items = listOf(
        TestPhrase(1, 10, "Системная команда", "открой настройки", "Команда открытия системных настроек"),
        TestPhrase(2, 10, "Системная команда", "выключи звук", "Команда отключения звука (Mute)"),
        TestPhrase(3, 10, "Системная команда", "сделай громче", "Команда регулировки громкости"),
        TestPhrase(4, 10, "Название приложения", "Кинопоиск", "Запуск онлайн-кинотеатра"),
        TestPhrase(5, 10, "Название приложения", "ютуб", "Запуск видеохостинга YouTube"),
        TestPhrase(6, 10, "Название приложения", "Телеграм", "Запуск мессенджера Telegram"),
        TestPhrase(7, 10, "Поиск контента", "фильмы с Джеки Чаном", "Поисковый запрос медиатеки"),
        TestPhrase(8, 10, "Информационный запрос", "прогноз погоды на завтра", "Запрос сводки погоды"),
        TestPhrase(9, 10, "Воспроизведение медиа", "включи классическую музыку", "Команда запуска плеера"),
        TestPhrase(10, 10, "Свободная диктовка", "завтра в десять утра запланирована рабочая встреча", "Длинная фраза со связной речью")
    )
}
