package com.englishcoach.app.data.practice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DrillBank(val sets: List<DrillSet>)

/** Файл тренировок уровня: <уровень>_drills.json. */
object DrillFile {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<DrillSet> = json.decodeFromString<DrillBank>(text).sets.onEach(::check)

    /** Битый набор (пустой, пропуск без слова, неизвестный вид) не должен попасть в приложение — ни встроенным, ни обновлением. */
    fun check(d: DrillSet) {
        require(d.kind in PracticeKind.DRILLS) { "${d.id}: неизвестный вид ${d.kind}" }
        require(d.size > 0) { "${d.id}: пустой набор" }
        if (d.kind == PracticeKind.CLOZE) require(Cloze.gaps(d.text).all { it.isNotBlank() }) { "${d.id}: пустой пропуск" }
        if (d.kind == PracticeKind.REPLY) require(d.items.all { it.answer in it.options.indices }) { "${d.id}: нет верного ответа" }
    }
}

/**
 * Текст с пропусками: «I work as a [developer]. I [build|make] apps.» — в скобках верное слово,
 * через | — другие допустимые. В банк слов идёт первое.
 */
object Cloze {
    sealed interface Token
    data class Word(val text: String) : Token
    /** Пропуск номер [index]; [tail] — знаки препинания сразу после него («.», «,»). */
    data class Gap(val index: Int, val accept: List<String>, val tail: String) : Token

    private val GAP = Regex("\\[([^\\]]*)\\]([.,!?;:]*)")

    fun parse(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var last = 0
        var n = 0
        fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { out += Word(it) }
        GAP.findAll(text).forEach { m ->
            words(text.substring(last, m.range.first))
            out += Gap(n++, m.groupValues[1].split('|').map { it.trim() }, m.groupValues[2])
            last = m.range.last + 1
        }
        words(text.substring(last))
        return out
    }

    /** Верные слова пропусков по порядку (первый вариант каждого). */
    fun gaps(text: String): List<String> = parse(text).filterIsInstance<Gap>().map { it.accept.first() }

    /** Банк слов: по слову на каждый пропуск (повторы тоже) плюс лишние. */
    fun bank(text: String, extra: List<String>): List<String> = gaps(text) + extra

    fun isRight(gap: Gap, filled: String?): Boolean = filled != null && Answers.matches(filled, gap.accept)
}

/** Перевод фразы из плиток-слов. */
object Tiles {
    private val TRAIL = Regex("[.,!?;:]+$")

    /**
     * Плитки: слова эталона без знаков препинания (иначе «station?» подсказывает конец фразы) и лишние слова.
     * Первое слово — со строчной буквы, чтобы заглавная не выдавала начало (кроме I и I'm).
     */
    fun of(p: Phrase): List<String> {
        val words = p.en.split(" ").filter { it.isNotBlank() }.map { it.replace(TRAIL, "") }.toMutableList()
        if (words.isNotEmpty() && words[0] != "I" && !words[0].startsWith("I'")) words[0] = words[0].replaceFirstChar { it.lowercase() }
        return words + p.extra
    }

    fun isRight(p: Phrase, picked: List<String>): Boolean = Answers.matches(picked.joinToString(" "), listOf(p.en) + p.alt)
}

/** Сопоставление пар: каждая ошибка снимает один балл, но не ниже нуля. */
object Match {
    fun score(pairs: Int, mistakes: Int): Int = (pairs - mistakes).coerceAtLeast(0)
}
