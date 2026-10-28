package com.englishcoach.app.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishcoach.app.AppContainer
import com.englishcoach.app.data.db.CardWithWord
import com.englishcoach.app.data.fsrs.Rating
import com.englishcoach.app.data.fsrs.formatInterval
import com.englishcoach.app.data.toMemory
import kotlinx.coroutines.launch

data class ReviewUi(
    val loading: Boolean = true,
    val current: CardWithWord? = null,
    val revealed: Boolean = false,
    val previews: Map<Rating, String> = emptyMap(),
    val done: Int = 0,
    /** Сколько карточек ещё в очереди, включая текущую и вернувшиеся на короткий шаг. */
    val remaining: Int = 0,
)

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    var ui by mutableStateOf(ReviewUi())
        private set

    private val queue = ArrayDeque<CardWithWord>()
    private var shownAt = 0L

    init {
        viewModelScope.launch {
            c.seeder.seedIfNeeded() // на случай первого запуска: дождаться загрузки курса
            queue.addAll(c.study.loadQueue(System.currentTimeMillis()))
            ui = ui.copy(loading = false)
            showNext()
        }
    }

    fun reveal() {
        val cur = ui.current ?: return
        val now = System.currentTimeMillis()
        val previews = Rating.entries.associateWith { r ->
            formatInterval(c.study.fsrs.schedule(cur.card.toMemory(), r, now).due - now)
        }
        ui = ui.copy(revealed = true, previews = previews)
        if (cur.card.direction == 1) speak(cur.word.en)
    }

    fun rate(rating: Rating) {
        val cur = ui.current ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            val updated = c.study.answer(cur.card, rating, now, now - shownAt)
            // Карточка в коротком шаге (минуты) возвращается в конец очереди этой же сессии
            val again = updated.due - now < RELEARN_WINDOW_MS
            if (again) queue.addLast(cur.copy(card = updated))
            if (!again) ui = ui.copy(done = ui.done + 1)
            showNext()
        }
    }

    fun speak(text: String, slow: Boolean = false) = c.tts.speak(text, slow)

    private fun showNext() {
        val next = queue.removeFirstOrNull()
        shownAt = System.currentTimeMillis()
        ui = ui.copy(current = next, revealed = false, previews = emptyMap(), remaining = queue.size + if (next != null) 1 else 0)
        if (next != null && next.card.direction == 0) speak(next.word.en)
    }

    private companion object {
        const val RELEARN_WINDOW_MS = 20 * 60_000L
    }
}
