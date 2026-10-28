package com.englishcoach.app.ui.placement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishcoach.app.AppContainer
import com.englishcoach.app.data.db.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlacementQuestion(val q: String, val options: List<String>, val answer: Int)

@Serializable
data class PlacementLevel(val level: String, val questions: List<PlacementQuestion>)

@Serializable
private data class PlacementBank(val levels: List<PlacementLevel>)

sealed interface Phase {
    data object Intro : Phase
    data class Question(val level: String, val number: Int, val q: PlacementQuestion, val chosen: Int? = null) : Phase
    data class WordCheck(val word: WordEntity, val index: Int, val total: Int) : Phase
    data class Result(val level: String?, val known: Int) : Phase
}

/**
 * Стартовый тест: по 4 вопроса на уровень, от A1 вверх. Уровень засчитан при 3 из 4;
 * первый незасчитанный — тест окончен. Затем быстрая проверка слов курса «знаю / не знаю».
 */
class PlacementViewModel(private val c: AppContainer) : ViewModel() {
    var phase by mutableStateOf<Phase>(Phase.Intro)
        private set

    private var levels: List<PlacementLevel> = emptyList()
    private var levelIdx = 0
    private var qIdx = 0
    private var correct = 0
    private var passed: String? = null
    private var words: List<WordEntity> = emptyList()
    private var wordIdx = 0
    private var known = 0

    init {
        viewModelScope.launch {
            levels = withContext(Dispatchers.IO) {
                val text = c.app.assets.open("course/placement.json").bufferedReader().use { it.readText() }
                Json.decodeFromString<PlacementBank>(text).levels
            }
        }
    }

    fun start() = showQuestion()

    fun answer(option: Int) {
        val p = phase as? Phase.Question ?: return
        if (p.chosen != null) return
        phase = p.copy(chosen = option)
        if (option == p.q.answer) correct++
        viewModelScope.launch {
            delay(700) // успеть увидеть, верно ли
            nextQuestion()
        }
    }

    fun knowWord(yes: Boolean) {
        val p = phase as? Phase.WordCheck ?: return
        if (yes) {
            known++
            viewModelScope.launch { c.study.markKnown(p.word.id, System.currentTimeMillis()) }
        }
        wordIdx++
        showWord()
    }

    fun speak(text: String) = c.tts.speak(text)

    fun finishWords() = finish()

    private fun showQuestion() {
        val lvl = levels.getOrNull(levelIdx) ?: return
        phase = Phase.Question(lvl.level, levelIdx * QUESTIONS_PER_LEVEL + qIdx + 1, lvl.questions[qIdx])
    }

    private fun nextQuestion() {
        qIdx++
        if (qIdx < QUESTIONS_PER_LEVEL) return showQuestion()
        if (correct >= PASS) {
            passed = levels[levelIdx].level
            levelIdx++
            qIdx = 0
            correct = 0
            if (levelIdx < levels.size) return showQuestion()
        }
        viewModelScope.launch {
            words = c.study.wordsForCheck("A1")
            wordIdx = 0
            showWord()
        }
    }

    private fun showWord() {
        val w = words.getOrNull(wordIdx) ?: return finish()
        phase = Phase.WordCheck(w, wordIdx + 1, words.size)
    }

    private fun finish() {
        val level = passed
        viewModelScope.launch {
            c.settings.update { it.copy(placementDone = true, level = level ?: "A1") }
        }
        phase = Phase.Result(level, known)
    }

    companion object {
        const val QUESTIONS_PER_LEVEL = 4
        private const val PASS = 3
    }
}
