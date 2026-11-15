package com.englishcoach.app.data

/** Приоритет темы новых слов. */
enum class TopicMode(val label: String) { FIRST("Сначала"), NORMAL("Обычно"), LATER("Позже") }

object Topics {
    fun mode(s: Settings, topic: String): TopicMode = when (topic) {
        in s.firstTopics -> TopicMode.FIRST
        in s.laterTopics -> TopicMode.LATER
        else -> TopicMode.NORMAL
    }

    fun withMode(s: Settings, topic: String, mode: TopicMode): Settings = s.copy(
        firstTopics = if (mode == TopicMode.FIRST) s.firstTopics + topic else s.firstTopics - topic,
        laterTopics = if (mode == TopicMode.LATER) s.laterTopics + topic else s.laterTopics - topic,
    )

    /** База, без которой не обойтись ни в одной теме, и всё про учёбу и общение. */
    private val STUDY_AND_TALK = setOf(
        "Основа", "Люди", "Главные глаголы", "Время и числа", "Вопросы и связки",
        "Университет", "Общение", "Учёба", "Общение и знакомства",
        "Чувства и характер", "Время и планы", "Глаголы A2", "Общение и мнения",
    )

    /** Узкие темы, которые можно отложить ради разговорной речи. */
    private val NARROW = listOf("Путешеств", "Строител", "Стройка", "переезд", "за границей")

    /**
     * Готовый набор «учёба и общение»: база, учёба и общение — сначала; путешествия, переезд и стройка — позже.
     * Берутся только темы, которые есть в базе, остальные выборы пользователя не трогаются.
     */
    fun studyPreset(s: Settings, topics: Collection<String>): Settings {
        val first = topics.filter { it in STUDY_AND_TALK }.toSet()
        val later = topics.filter { t -> NARROW.any { t.contains(it, ignoreCase = true) } }.toSet()
        return s.copy(firstTopics = (s.firstTopics - later) + first, laterTopics = (s.laterTopics - first) + later)
    }
}
