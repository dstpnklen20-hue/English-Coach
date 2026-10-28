package com.englishcoach.app.data.practice

/** Сравнение ответов без придирок к регистру, пунктуации и сокращениям (don't = do not). */
object Answers {
    private val contractions = mapOf(
        "don't" to "do not", "doesn't" to "does not", "didn't" to "did not",
        "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
        "can't" to "cannot", "can not" to "cannot", "i'm" to "i am", "it's" to "it is",
        "he's" to "he is", "she's" to "she is", "let's" to "let us", "you're" to "you are",
        "we're" to "we are", "they're" to "they are", "what's" to "what is", "where's" to "where is",
    )

    fun normalize(s: String): String {
        var t = s.lowercase().replace('’', '\'')
        t = t.replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        // Раскрываем сокращения по словам, чтобы «I'm» и «I am» совпадали
        t = " $t "
        contractions.forEach { (short, full) -> t = t.replace(" $short ", " $full ") }
        return t.trim()
    }

    fun matches(input: String, accepted: List<String>): Boolean =
        normalize(input).isNotEmpty() && accepted.any { normalize(it) == normalize(input) }

    /** Подсветка диктанта: каждое слово эталона и угадано ли оно (порядок не важен). */
    fun wordDiff(input: String, target: String): List<Pair<String, Boolean>> {
        val got = normalize(input).split(" ").toMutableList()
        return target.split(" ").filter { it.isNotBlank() }.map { w ->
            val n = normalize(w)
            w to (n.isNotEmpty() && n.split(" ").all { got.remove(it) })
        }
    }

    /** Варианты начальной формы для поиска перевода: plays → play, liked → like, cities → city. */
    fun stems(word: String): List<String> {
        val w = word.lowercase().removeSuffix("'s")
        val out = mutableListOf(w)
        if (w.endsWith("ies")) out += w.dropLast(3) + "y"
        if (w.endsWith("es")) out += w.dropLast(2)
        if (w.endsWith("s")) out += w.dropLast(1)
        if (w.endsWith("ed")) out += listOf(w.dropLast(2), w.dropLast(1))
        if (w.endsWith("ing")) out += listOf(w.dropLast(3), w.dropLast(3) + "e")
        return out.distinct()
    }
}
