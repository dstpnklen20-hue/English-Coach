package com.englishcoach.app.data.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
}

/** Состояние памяти карточки — то, что FSRS читает и пересчитывает. Время в миллисекундах. */
data class Memory(
    val state: Int = CardState.NEW,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/**
 * Планировщик FSRS-5 (Free Spaced Repetition Scheduler) с параметрами по умолчанию.
 * stability — через сколько дней вероятность вспомнить упадёт до 90%; difficulty — 1..10.
 * Короткие шаги (минуты) для новых и забытых карточек — как в Anki.
 */
class Fsrs(
    private val w: DoubleArray = DEFAULT_W,
    private val retention: Double = 0.9,
    private val maxIntervalDays: Int = 36500,
) {
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1 + FACTOR * elapsedDays / stability).pow(DECAY)

    fun intervalDays(stability: Double): Int =
        (stability / FACTOR * (retention.pow(1 / DECAY) - 1)).roundToInt().coerceIn(1, maxIntervalDays)

    fun schedule(m: Memory, rating: Rating, now: Long): Memory {
        val r = rating.value
        val base = m.copy(reps = m.reps + 1, lastReview = now)
        return when (m.state) {
            CardState.NEW -> {
                val s = initStability(r)
                val d = initDifficulty(r)
                when (rating) {
                    Rating.Again -> base.learning(CardState.LEARNING, s, d, now + 1 * MINUTE)
                    Rating.Hard -> base.learning(CardState.LEARNING, s, d, now + 5 * MINUTE)
                    Rating.Good -> base.learning(CardState.LEARNING, s, d, now + 10 * MINUTE)
                    Rating.Easy -> base.review(s, d, now, intervalDays(s))
                }
            }
            CardState.LEARNING, CardState.RELEARNING -> {
                val s = nextStabilityAfterSuccess(m, r, now)
                val d = nextDifficulty(m.difficulty, r)
                when (rating) {
                    Rating.Again -> base.learning(m.state, s, d, now + 1 * MINUTE)
                    Rating.Hard -> base.learning(m.state, s, d, now + 5 * MINUTE)
                    Rating.Good, Rating.Easy -> base.review(s, d, now, intervalDays(s))
                }
            }
            else -> { // REVIEW
                val d = nextDifficulty(m.difficulty, r)
                if (rating == Rating.Again) {
                    val rNow = retrievability(elapsedDays(m, now), m.stability)
                    val s = forgetStability(m.difficulty, m.stability, rNow)
                    base.copy(lapses = m.lapses + 1).learning(CardState.RELEARNING, s, d, now + 10 * MINUTE)
                } else {
                    // Интервалы «трудно ≤ хорошо < легко» — иначе кнопки выглядят нелогично
                    val hard = intervalDays(nextStabilityAfterSuccess(m, 2, now))
                    val good = max(intervalDays(nextStabilityAfterSuccess(m, 3, now)), hard + 1)
                    val easy = max(intervalDays(nextStabilityAfterSuccess(m, 4, now)), good + 1)
                    val days = when (rating) { Rating.Hard -> hard; Rating.Good -> good; else -> easy }
                    base.review(nextStabilityAfterSuccess(m, r, now), d, now, days)
                }
            }
        }
    }

    private fun Memory.learning(state: Int, s: Double, d: Double, due: Long) =
        copy(state = state, stability = s, difficulty = d, due = due)

    private fun Memory.review(s: Double, d: Double, now: Long, days: Int) =
        copy(state = CardState.REVIEW, stability = s, difficulty = d, due = now + days * DAY)

    private fun elapsedDays(m: Memory, now: Long): Double =
        m.lastReview?.let { (now - it).coerceAtLeast(0) / DAY.toDouble() } ?: 0.0

    private fun nextStabilityAfterSuccess(m: Memory, r: Int, now: Long): Double {
        val t = elapsedDays(m, now)
        return if (t < 1) {
            m.stability * exp(w[17] * (r - 3 + w[18])) // повтор в тот же день
        } else {
            recallStability(m.difficulty, m.stability, retrievability(t, m.stability), r)
        }
    }

    private fun initStability(r: Int) = max(w[r - 1], 0.1)

    private fun initDifficulty(r: Int) = (w[4] - exp(w[5] * (r - 1)) + 1).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, r: Int): Double {
        val delta = -w[6] * (r - 3)
        val damped = d + delta * (10 - d) / 9
        return (w[7] * initDifficulty(4) + (1 - w[7]) * damped).coerceIn(1.0, 10.0)
    }

    private fun recallStability(d: Double, s: Double, rNow: Double, r: Int): Double {
        val hardPenalty = if (r == 2) w[15] else 1.0
        val easyBonus = if (r == 4) w[16] else 1.0
        return s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) * (exp(w[10] * (1 - rNow)) - 1) * hardPenalty * easyBonus)
    }

    private fun forgetStability(d: Double, s: Double, rNow: Double): Double =
        min(w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp(w[14] * (1 - rNow)), s)

    companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0

        val DEFAULT_W = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )
    }
}

/** Подпись интервала для кнопок: «10 мин», «3 дн», «2 мес». */
fun formatInterval(ms: Long): String {
    val min = ms / Fsrs.MINUTE
    val days = ms / Fsrs.DAY
    return when {
        min < 60 -> "${max(min, 1)} мин"
        days < 1 -> "${min / 60} ч"
        days < 31 -> "$days дн"
        days < 365 -> "${days / 30} мес"
        else -> "${days / 365} г"
    }
}
