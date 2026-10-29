package com.englishcoach.app.data.importer

import com.englishcoach.app.data.practice.Answers

/** Пара «слово — перевод» из списка (CSV, экспорт Anki, заметка). */
data class WordPair(val en: String, val ru: String)

/** Незнакомое слово из текста: сколько раз встретилось и предложение-пример оттуда же. */
data class Candidate(val word: String, val count: Int, val context: String?)

/**
 * Разбор импортируемого текста без сети. Два случая:
 * список пар («word — слово», «word<TAB>слово», «word;слово») или обычный текст/субтитры,
 * из которого выбираются незнакомые слова.
 */
object Importer {
    private val CYRILLIC = Regex("[А-Яа-яЁё]")
    private val LATIN = Regex("[A-Za-z]")
    private val PAIR_SEPARATOR = Regex("\\t|\\s+[-–—=:]\\s+|\\s*;\\s*")
    private val WORD = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

    /** Служебные и самые частые слова — их нет смысла учить карточками. */
    private val STOP = """
        the a an and or but if then than so as at by for from in into of off on onto out over to up down with without
        about after again against all also am are is was were be been being do does did doing done have has had having
        i me my mine you your yours he him his she her hers it its we us our ours they them their theirs this that these those
        what which who whom whose when where why how not no nor yes yeah yep nope oh ok okay hey hi hello uh um hmm huh ah
        can could will would shall should may might must just very too also only even still here there now then some any
        each every both few more most other such own same one two three get got gonna wanna gotta let lets like well really
        right back know see come go went going want say said tell told think thing things way make made time yes sir
        under above below between through while because until around near again ever never always often much many
    """.trim().split(Regex("\\s+")).toSet()

    /** Убрать служебное: метку BOM, номера и тайминги субтитров, HTML и теги, строки-заголовки Anki (#…). */
    fun clean(raw: String): String = raw.removePrefix("﻿").lineSequence()
        .filterNot { line ->
            val t = line.trim()
            t.matches(Regex("\\d+")) || "-->" in t || t.startsWith("#")
        }
        .map { it.replace(Regex("<[^>]+>"), " ").replace(Regex("\\{[^}]*\\}"), " ").replace("&nbsp;", " ") }
        .joinToString("\n")

    fun parsePairs(text: String): List<WordPair> = clean(text).lines().mapNotNull { line ->
        val t = line.trim().removePrefix("-").removePrefix("•").trim()
        val m = PAIR_SEPARATOR.find(t) ?: return@mapNotNull null
        val left = t.substring(0, m.range.first).trim()
        val right = t.substring(m.range.last + 1).trim()
        val tab = m.value == "\t"
        // Английское слева; справа — перевод (кириллица) или любая колонка, если это таблица с табуляцией
        if (left.isNotEmpty() && right.isNotEmpty() && LATIN.containsMatchIn(left) && !CYRILLIC.containsMatchIn(left) &&
            (tab || CYRILLIC.containsMatchIn(right))
        ) WordPair(left, right) else null
    }.distinctBy { it.en.lowercase() }

    /** Список пар, если так оформлено хотя бы 60% непустых строк. */
    fun looksLikePairs(text: String): Boolean {
        val lines = clean(text).lines().filter { it.isNotBlank() }
        return lines.isNotEmpty() && parsePairs(text).size * 10 >= lines.size * 6
    }

    /**
     * Незнакомые слова по частоте. Отбрасываются служебные, короткие, с апострофом, уже известные (с учётом
     * окончаний: plays → play) и имена — слова, которые в тексте ни разу не встретились со строчной буквы.
     */
    fun extractWords(text: String, known: Set<String>, limit: Int = 80): List<Candidate> {
        val clean = clean(text)
        val counts = LinkedHashMap<String, Int>()
        val lowerSeen = HashSet<String>()
        val firstAt = HashMap<String, Int>()
        WORD.findAll(clean).forEach { m ->
            val raw = m.value
            if ('\'' in raw || raw.length < 3) return@forEach
            val w = raw.lowercase()
            if (w in STOP) return@forEach
            if (raw[0].isLowerCase()) lowerSeen += w
            counts[w] = (counts[w] ?: 0) + 1
            firstAt.putIfAbsent(w, m.range.first)
        }
        return counts.entries
            .filter { (w, _) -> w in lowerSeen && Answers.stems(w).none { it in known } }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { firstAt[it.key] })
            .take(limit)
            .map { (w, n) -> Candidate(w, n, sentenceAround(clean, firstAt.getValue(w))) }
    }

    /** Предложение вокруг позиции — пример употребления для карточки. */
    private fun sentenceAround(text: String, at: Int): String? {
        val start = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), at).let { if (it < 0) 0 else it + 1 }
        val endIdx = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), at)
        val end = if (endIdx < 0) text.length else endIdx + 1
        return text.substring(start, end).replace(Regex("\\s+"), " ").trim().takeIf { it.length in 4..160 }
    }
}
