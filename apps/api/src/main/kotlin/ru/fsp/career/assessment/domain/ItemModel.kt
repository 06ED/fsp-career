package ru.fsp.career.assessment.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.random.Random

/**
 * One concrete task produced by a family for a given seed. `answer` and `tolerance` are the
 * server-side key: they are stored in `assessment_items.answer_key` and never serialized to clients.
 *
 * Choice items: `options` are shown in this order with ids a, b, c…, `answer` is the correct index.
 * Numeric items: `options` is empty and `answer` is the exact value; a response is correct when it
 * parses as a number within `tolerance`.
 */
class Generated(
    val prompt: String,
    val code: String? = null,
    val language: String? = null,
    val options: List<String> = emptyList(),
    val answer: String,
    val tolerance: Double = 0.0,
    val unit: String? = null,
    /** Index of the scenario within a VARIANTS family (validation only: models what a leak reveals). */
    val variant: Int? = null,
) {
    val isChoice: Boolean get() = options.isNotEmpty()

    /**
     * Stable identity of the instance as a person would memorize it: text + code + the *set* of
     * options (their order is shuffled per seed but does not change what a leak reveals).
     */
    val fingerprint: String get() = prompt + "\u0000" + (code ?: "") + "\u0000" + options.sorted().joinToString("\u0001")

    fun isCorrect(response: String): Boolean = AnswerKey.check(isChoice, answer, tolerance, response)
}

object AnswerKey {
    val optionIds = listOf("a", "b", "c", "d", "e", "f")

    /** Accepts "3,5", "3.5", " 42 ", "12%", Unicode minus and "1 200" (plain or no-break space). */
    fun parseNumber(raw: String): Double? =
        raw.trim().removeSuffix("%").replace('\u2212', '-')
            .filterNot { it == ' ' || it == '\u00a0' || it == '\u202f' }
            .replace(',', '.').toDoubleOrNull()

    fun check(choice: Boolean, answer: String, tolerance: Double, response: String): Boolean =
        if (choice) optionIds.indexOf(response.trim()).toString() == answer
        else parseNumber(response)?.let { abs(it - answer.toDouble()) <= tolerance + 1e-9 } ?: false
}

enum class FamilyKind { PARAMETRIC, VARIANTS }

/**
 * An item family = one skill at one calibrated difficulty. `generate` must be a pure function of
 * the Random it receives, so an instance is reproducible from its seed (unit-tested on many seeds).
 *
 * Why this resists leaks: a candidate never sees "the" question, only an instance drawn from a large
 * space (parametric families: new numbers, tables, code; variant families: one of several equivalent
 * scenarios with shuffled distractors). Knowing someone else's instance and its answer does not answer
 * yours; what transfers is the method — which is exactly the skill being measured.
 */
class ItemFamily(
    val id: String,
    /** Short Russian name of the measured skill (shown in the vacancy preview, not during the test). */
    val title: String,
    val topic: String,
    val specializations: Set<String>,
    val level: Grade,
    val a: Double,
    val b: Double,
    val kind: FamilyKind,
    /** Number of options for choice families, 0 for numeric input (sets the guessing floor c). */
    val options: Int,
    private val generator: (Random) -> Generated,
) {
    val params: ItemParams get() = ItemParams(a, b, if (options > 0) 1.0 / options else 0.0)
    fun instance(seed: Long): Generated = generator(Random(seed))
}

internal val TECH = setOf("backend", "frontend", "data")

internal fun family(
    id: String, title: String, topic: String, specializations: Set<String>, level: Grade,
    a: Double, b: Double, kind: FamilyKind, options: Int, generator: (Random) -> Generated,
) = ItemFamily(id, title, topic, specializations, level, a, b, kind, options, generator)

/** Multiple choice: correct answer plus three distinct distractors, shuffled with the instance RNG. */
internal fun choice(
    rng: Random, prompt: String, correct: String, distractors: List<String>,
    code: String? = null, language: String? = null,
): Generated {
    val wrong = distractors.distinct().filter { it != correct }.shuffled(rng).take(3)
    require(wrong.size == 3) { "Need three distinct distractors for: $prompt" }
    val all = (wrong + correct).shuffled(rng)
    return Generated(prompt, code, language, all, all.indexOf(correct).toString())
}

/** Multiple choice with a fixed, meaningful option order (e.g. ordered ranges). */
internal fun orderedChoice(prompt: String, options: List<String>, correctIndex: Int, code: String? = null, language: String? = null) =
    Generated(prompt, code, language, options, correctIndex.toString())

internal fun numeric(
    prompt: String, value: Double, tolerance: Double = 0.0, code: String? = null,
    language: String? = null, unit: String? = null,
) = Generated(prompt, code, language, emptyList(), formatNumber(value), tolerance, unit)

internal fun numeric(prompt: String, value: Int, code: String? = null, language: String? = null, unit: String? = null) =
    numeric(prompt, value.toDouble(), 0.0, code, language, unit)

/** Equivalent-difficulty scenario for a VARIANTS family; the distractor pool may exceed three. */
internal class Variant(val prompt: String, val correct: String, val distractors: List<String>, val code: String? = null, val language: String? = null)

internal fun variant(rng: Random, variants: List<Variant>): Generated {
    val index = rng.nextInt(variants.size)
    val v = variants[index]
    val g = choice(rng, v.prompt, v.correct, v.distractors, v.code, v.language)
    return Generated(g.prompt, g.code, g.language, g.options, g.answer, g.tolerance, g.unit, index)
}

internal fun formatNumber(value: Double): String =
    if (value == round(value) && abs(value) < 1e15) value.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

/** Ru-locale decimal for prompts: 12.5 → "12,5". */
internal fun ru(value: Double, decimals: Int = 1): String {
    val text = "%.${decimals}f".format(java.util.Locale.ROOT, value).replace('.', ',')
    return if (decimals > 0) text.trimEnd('0').trimEnd(',') else text
}

/** Russian plural form: plural(3, "токен", "токена", "токенов") = "токена". */
internal fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

internal fun <T> Random.pick(items: List<T>): T = items[nextInt(items.size)]
internal fun Random.range(from: Int, toInclusive: Int): Int = nextInt(from, toInclusive + 1)

/** Renders a small result-set style table for SQL/pandas tasks. */
internal fun textTable(name: String, header: List<String>, rows: List<List<Any?>>): String {
    val cells = rows.map { row -> row.map { it?.toString() ?: "NULL" } }
    val widths = header.indices.map { col -> maxOf(header[col].length, cells.maxOfOrNull { it[col].length } ?: 0) }
    fun line(values: List<String>) = values.mapIndexed { i, v -> v.padEnd(widths[i]) }.joinToString(" | ").trimEnd()
    return buildString {
        appendLine("-- таблица $name")
        appendLine(line(header))
        appendLine(widths.joinToString("-+-") { "-".repeat(it) })
        cells.forEach { appendLine(line(it)) }
    }.trimEnd()
}

/** Topic registry: the unit of the blueprint (vacancy skills map onto these). */
object Topics {
    val labels: Map<String, String> = linkedMapOf(
        "algorithms" to "Алгоритмы и сложность",
        "http" to "HTTP и API",
        "security" to "Безопасность",
        "sql" to "SQL",
        "databases" to "Индексы и транзакции",
        "kotlin" to "Kotlin",
        "python" to "Python",
        "concurrency" to "Конкурентность",
        "distributed" to "Распределённые системы и кэш",
        "javascript" to "JavaScript",
        "typescript" to "TypeScript",
        "react" to "React",
        "css" to "CSS и вёрстка",
        "accessibility" to "Доступность (a11y)",
        "web-performance" to "Производительность веба",
        "statistics" to "Статистика",
        "experiments" to "A/B-эксперименты",
        "ml" to "Машинное обучение",
        "product-metrics" to "Продуктовые метрики",
        "unit-economics" to "Юнит-экономика",
        "prioritization" to "Приоритизация и цели",
        "research" to "Исследования пользователей",
        "ui-layout" to "Сетки и размеры",
        "typography" to "Типографика и цвет",
        "usability" to "Юзабилити",
        "design-systems" to "Дизайн-системы",
    )

    fun label(id: String): String = labels[id] ?: id
}
