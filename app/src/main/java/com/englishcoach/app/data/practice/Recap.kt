package com.englishcoach.app.data.practice

import com.englishcoach.app.data.db.CardWithWord
import kotlin.random.Random

/**
 * Повторение уже пройденных слов вне расписания: те же карточки, но ответы не меняют состояние FSRS,
 * поэтому проходить можно в любой момент и сколько угодно раз.
 */
object Recap {
    /** Как на экране «Слова»: выучено, когда все карточки слова держатся в памяти от 21 дня. */
    private const val LEARNED_DAYS = 21.0

    enum class Source(val label: String, val about: String) {
        TODAY("Пройденные сегодня", "слова, которые вы уже видели сегодня"),
        LEARNING("Учу", "начатые, но ещё не выученные"),
        HARD("Трудные", "слова, которые вы уже забывали"),
        LEARNED("Выученные", "держатся в памяти от 3 недель"),
        KNOWN("Отмеченные «знаю»", "проверить, правда ли помните"),
        ALL("Все пройденные", "всё, что вы уже начинали или отметили"),
    }

    /** Слова источника: на слово — все его карточки. Отмеченные «знаю» есть только в своём списке и во «Всех». */
    private fun words(cards: List<CardWithWord>, source: Source, dayStart: Long): List<List<CardWithWord>> =
        cards.groupBy { it.word.id }.values.filter { w ->
            val known = w.first().word.known
            when (source) {
                Source.TODAY -> !known && w.any { (it.card.lastReview ?: 0) >= dayStart }
                Source.LEARNING -> !known && w.minOf { it.card.stability } < LEARNED_DAYS
                Source.HARD -> !known && w.any { it.card.lapses > 0 }
                Source.LEARNED -> !known && w.minOf { it.card.stability } >= LEARNED_DAYS
                Source.KNOWN -> known
                Source.ALL -> true
            }
        }

    fun counts(cards: List<CardWithWord>, dayStart: Long): Map<Source, Int> =
        Source.entries.associateWith { words(cards, it, dayStart).size }

    /**
     * Карточки на занятие: по одной на слово, направление — случайно из уже показанных
     * (у слова, отмеченного «знаю» до первого показа, — EN→RU). Трудные — сначала самые трудные. count = null — все.
     */
    fun pick(cards: List<CardWithWord>, source: Source, dayStart: Long, count: Int?, random: Random = Random.Default): List<CardWithWord> {
        val shuffled = words(cards, source, dayStart).shuffled(random)
        val ordered = if (source == Source.HARD) shuffled.sortedByDescending { w -> w.maxOf { it.card.lapses } } else shuffled
        return ordered.take(count ?: ordered.size).map { w ->
            w.filter { it.card.reps > 0 }.randomOrNull(random) ?: w.minBy { it.card.direction }
        }.shuffled(random)
    }
}

/** Прохождение: «не помню» возвращает слово в конец очереди; в счёт идёт только ответ с первого раза, как в уроках. */
class RecapSession(cards: List<CardWithWord>) {
    private val queue = ArrayDeque(cards)
    private val answered = HashSet<Long>()
    private val failed = HashSet<Long>()

    var total = cards.size
        private set
    var correct = 0
        private set

    val current: CardWithWord? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size
    /** Сколько слов уже получили ответ — для записи результата, если занятие прервали. */
    val answeredCount: Int get() = answered.size

    fun answer(remembered: Boolean) {
        val c = queue.removeFirstOrNull() ?: return
        answered += c.card.id
        if (!remembered) {
            failed += c.card.id
            queue.addLast(c)
        } else if (c.card.id !in failed) {
            correct++
        }
    }

    /** Убрать текущее слово из занятия (отмечено «знаю» или возвращено в изучение) — на счёт не влияет. */
    fun dropCurrent() {
        val c = queue.removeFirstOrNull() ?: return
        total--
        answered -= c.card.id
    }
}
