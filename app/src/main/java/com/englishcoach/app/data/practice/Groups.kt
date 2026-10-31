package com.englishcoach.app.data.practice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * Слово или выражение группы. forms — три формы неправильного глагола (go, went, gone; варианты через «/»: learnt/learned).
 * answer — что пропускается в примере (по умолчанию en или прошедшая форма глагола).
 */
@Serializable
data class GroupItem(
    val en: String = "",
    val ru: String,
    val forms: List<String> = emptyList(),
    val example: String = "",
    val exampleRu: String = "",
    val answer: String = "",
    /** Пояснение: «не магазин», правило предлога — показывается в списке и в подсказке. */
    val note: String = "",
) {
    /** Как слово показывается в списке: «go — went — gone» или само выражение. */
    val title: String get() = if (forms.isNotEmpty()) forms.joinToString(" — ") else en

    /** Подсказка после ошибки: само слово, перевод и пояснение. */
    val hint: String get() = "$title — $ru" + if (note.isNotBlank()) " ($note)" else ""

    /** Ключ для учёта ответов: не зависит от порядка слов в файле. */
    val key: String get() = forms.firstOrNull() ?: en

    /** Пропуск в примере: явный, иначе для глагола — первая из прошедших форм, найденная в примере. */
    val gap: String get() = answer.ifBlank {
        if (forms.isEmpty()) en else forms.drop(1).flatMap { it.split('/') }.firstOrNull { Groups.hasWord(example, it) } ?: ""
    }
}

/**
 * Группа слов. modes — какие упражнения подходят: forms — вспомнить формы глагола, meaning — выбрать перевод,
 * gap — вставить в пример, order — собрать пример из слов.
 */
@Serializable
data class WordGroup(
    val id: String,
    val title: String,
    val about: String,
    val level: String,
    val modes: List<String>,
    val items: List<GroupItem>,
)

@Serializable
private data class GroupBank(val groups: List<WordGroup>)

object Groups {
    const val FILE = "groups.json"
    /** Сколько слов в одной тренировке. */
    const val SESSION = 10
    /** Слово выучено, когда верных ответов на столько больше, чем ошибок. */
    const val MASTERED_NET = 2
    val MODES = listOf("forms", "meaning", "gap", "order")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<WordGroup> = json.decodeFromString<GroupBank>(text).groups.onEach(::check)

    /** Битая группа не должна попасть в приложение: у каждого упражнения должно получиться задание. */
    fun check(g: WordGroup) {
        require(g.items.size >= 3) { "${g.id}: мало слов" }
        require(g.modes.isNotEmpty() && g.modes.all { it in MODES }) { "${g.id}: неизвестный вид упражнения" }
        g.items.forEach { i ->
            require(i.key.isNotBlank()) { "${g.id}: пустое слово" }
            if ("forms" in g.modes) require(i.forms.size == 3) { "${g.id}/${i.key}: нужны три формы" }
            if ("gap" in g.modes) require(i.gap.isNotBlank() && hasWord(i.example, i.gap)) { "${g.id}/${i.key}: в примере нет «${i.gap}»" }
            if ("order" in g.modes) require(i.example.isNotBlank()) { "${g.id}/${i.key}: нет примера" }
        }
    }

    fun itemId(g: WordGroup, i: GroupItem) = "${g.id}:${i.key}"

    /** Есть ли в предложении слово (или выражение) целиком, без учёта регистра. */
    fun hasWord(sentence: String, word: String): Boolean = wordRegex(word).containsMatchIn(sentence)

    private fun wordRegex(word: String) = Regex("(?<![A-Za-z'])${Regex.escape(word)}(?![A-Za-z'])", RegexOption.IGNORE_CASE)

    /**
     * Слова для тренировки: сначала невыученные и те, где ошибались чаще (net — верные минус ошибки),
     * при равенстве — случайно; в конце перемешиваются.
     */
    fun pick(g: WordGroup, net: Map<String, Int>, count: Int = SESSION, random: Random = Random.Default): List<GroupItem> =
        g.items.shuffled(random).sortedBy { net[itemId(g, it)] ?: 0 }.take(count).shuffled(random)

    fun mastered(g: WordGroup, net: Map<String, Int>): Int = g.items.count { (net[itemId(g, it)] ?: 0) >= MASTERED_NET }

    /**
     * Упражнение по слову. mode — вид из [WordGroup.modes]; hard — без вариантов: ввод с клавиатуры.
     * Варианты-ловушки берутся из других слов группы (для глагола — типичные ошибки: goed, gone вместо went).
     */
    fun exercise(g: WordGroup, item: GroupItem, mode: String, hard: Boolean, random: Random = Random.Default): Exercise = when (mode) {
        "forms" -> Exercise(
            type = "input",
            q = "${item.forms[0]} — ? — ?",
            ru = "${item.ru}. Напишите 2-ю и 3-ю формы через пробел",
            accept = combos(item.forms[1], item.forms[2]),
            hint = item.title,
            placeholder = "например: went gone",
        )
        "meaning" -> {
            val wrong = g.items.filter { it.ru != item.ru }.shuffled(random).take(2).map { it.ru }
            choice(item.title, null, item.ru, wrong, random, hint = item.hint)
        }
        "gap" -> {
            val gap = item.gap
            val q = wordRegex(gap).replaceFirst(item.example, "___")
            val ru = item.exampleRu.ifBlank { null }
            if (hard) {
                Exercise(type = "input", q = q, ru = ru, accept = variants(item, gap), hint = item.hint)
            } else {
                choice(q, ru, gap, gapTraps(g, item, gap).shuffled(random).take(2), random, hint = item.hint)
            }
        }
        else -> if (hard) {
            Exercise(type = "input", q = item.exampleRu.ifBlank { item.ru }, accept = listOf(item.example), hint = item.example, placeholder = "Переведите на английский")
        } else {
            Exercise(type = "order", q = item.exampleRu, accept = listOf(item.example), hint = item.hint)
        }
    }

    /** Неверные варианты для пропуска: у глагола — его же другие формы и «правильная» ошибка на -ed, у остальных — ответы других слов. */
    private fun gapTraps(g: WordGroup, item: GroupItem, gap: String): List<String> {
        val traps = if (item.forms.isNotEmpty()) {
            // У коротких (be) «правильная» форма — не ошибка, а другое слово (bed), её не предлагаем
            item.forms.flatMap { it.split('/') } + listOfNotNull(regularPast(item.forms[0]).takeIf { item.forms[0].length > 2 })
        } else {
            g.items.map { it.gap }
        }
        // Ловушка не должна оказаться тоже верной: learnt/learned — обе формы правильные
        val right = variants(item, gap).map { it.lowercase() }
        return traps.filter { it.lowercase() !in right }.distinctBy { it.lowercase() }
    }

    /** Все верные написания пропуска: для глагола — варианты той же формы (learnt/learned). */
    private fun variants(item: GroupItem, gap: String): List<String> =
        item.forms.map { it.split('/') }.firstOrNull { v -> v.any { it.equals(gap, ignoreCase = true) } } ?: listOf(gap)

    /** Ошибка, которую делают чаще всего: неправильный глагол как правильный (go → goed, buy → buyed). */
    fun regularPast(base: String): String = when {
        base.endsWith("e") -> base + "d"
        base.endsWith("y") && base.length > 2 && base[base.length - 2] !in "aeiou" -> base.dropLast(1) + "ied"
        else -> base + "ed"
    }

    /** Все сочетания вариантов форм: learnt/learned + learnt/learned → 4 допустимых ответа. */
    private fun combos(past: String, participle: String) =
        past.split('/').flatMap { p -> participle.split('/').map { pp -> "$p $pp" } }

    private fun choice(q: String, ru: String?, right: String, wrong: List<String>, random: Random, hint: String): Exercise {
        val options = (wrong + right).shuffled(random)
        return Exercise(type = "choice", q = q, ru = ru, options = options, answer = options.indexOf(right), hint = hint)
    }
}
