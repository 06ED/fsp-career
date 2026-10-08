package ru.fsp.career.assessment

import ru.fsp.career.assessment.domain.*

import kotlinx.serialization.json.Json
import ru.fsp.career.assessment.domain.content.contrastRatio
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ItemBankTest {
    private val seeds = 0L until 400L

    @Test fun everyFamilyIsDeterministicWellFormedAndSelfConsistent() {
        for (family in ItemBank.families) for (seed in seeds) {
            val item = family.instance(seed)
            val again = family.instance(seed)
            assertEquals(item.fingerprint, again.fingerprint, "${family.id} not deterministic for seed $seed")
            assertEquals(item.answer, again.answer)
            assertTrue(item.prompt.isNotBlank(), family.id)
            if (family.options > 0) {
                assertEquals(family.options, item.options.size, "${family.id}: option count, seed $seed")
                assertEquals(item.options.size, item.options.toSet().size, "${family.id}: duplicate options for seed $seed: ${item.options}")
                assertTrue(item.options.none { it.isBlank() }, family.id)
                val index = item.answer.toInt()
                assertTrue(index in item.options.indices, family.id)
                assertTrue(item.isCorrect(AnswerKey.optionIds[index]), family.id)
                assertFalse(item.isCorrect(AnswerKey.optionIds[(index + 1) % item.options.size]), family.id)
            } else {
                assertTrue(item.options.isEmpty(), family.id)
                val value = assertNotNull(item.answer.toDoubleOrNull(), "${family.id}: numeric key")
                assertTrue(value.isFinite(), family.id)
                assertTrue(item.isCorrect(item.answer), family.id)
                assertTrue(item.isCorrect(item.answer.replace('.', ',')), family.id)
                assertFalse(item.isCorrect(formatNumber(value + item.tolerance + 1.0)), family.id)
            }
        }
    }

    @Test fun generatorsSurviveManySeeds() {
        for (family in ItemBank.families) for (seed in 1_000L until 6_000L) {
            val item = family.instance(seed * 7_919)
            if (family.options > 0) assertEquals(family.options, item.options.toSet().size, "${family.id} seed $seed")
            else assertTrue(item.answer.toDouble().isFinite(), "${family.id} seed $seed")
        }
    }

    @Test fun itemViewNeverCarriesTheKey() {
        for (family in ItemBank.families) {
            val instance = family.instance(7)
            val json = Json.encodeToString(AssessmentItemView.serializer(), itemView(family, instance))
            assertFalse(json.contains("answer"), family.id)
            assertFalse(json.contains("tolerance"), family.id)
        }
    }

    @Test fun parametricFamiliesProduceManyDistinctInstances() {
        for (family in ItemBank.families) {
            val distinct = (0L until 300L).map { family.instance(it).fingerprint }.toSet().size
            val minimum = if (family.kind == FamilyKind.PARAMETRIC) 40 else 4
            assertTrue(distinct >= minimum, "${family.id}: only $distinct distinct instances in 300 draws")
        }
    }

    @Test fun coverageAndParameterRanges() {
        val specializations = AssessmentCatalog.specializations.map { it.id }
        for (spec in specializations) {
            val own = ItemBank.families.count { it.specializations == setOf(spec) || (spec in it.specializations && it.specializations.size <= 2) }
            assertTrue(own >= 8, "$spec has only $own families")
            val pool = ItemBank.pool(spec)
            assertTrue(pool.size >= Cat.TEST_LENGTH + 1, "$spec pool too small: ${pool.size}")
            assertTrue(pool.map { it.level }.toSet().size == 3, "$spec must cover junior, middle and senior")
        }
        assertTrue(ItemBank.families.count { it.id.startsWith("gen.") } >= 6)
        for (spec in listOf("backend", "frontend", "data")) {
            val pool = ItemBank.pool(spec)
            assertTrue(pool.count { it.kind == FamilyKind.PARAMETRIC } * 2 > pool.size, "$spec: parametric families must dominate")
        }
        for (f in ItemBank.families) {
            assertTrue(f.a in 0.8..2.0, f.id)
            assertTrue(f.b in -1.6..2.0, f.id)
            assertTrue(f.topic in Topics.labels, f.id)
        }
    }

    // --- Independent oracles: re-derive the key from the rendered text, not from the generator's variables. ---

    private fun item(id: String, seed: Long) = ItemBank.family(id)!!.instance(seed)
    private fun ints(text: String) = Regex("-?\\d+").findAll(text).map { it.value.toInt() }.toList()

    @Test fun bitwiseOracle() = seeds.forEach { seed ->
        val g = item("gen.bits", seed)
        val (a, b) = ints(g.code!!)
        val expected = when {
            "a & b" in g.code!! -> a and b
            "a | b" in g.code!! -> a or b
            else -> a xor b
        }
        assertEquals(expected.toString(), g.answer)
    }

    @Test fun lruOracle() = seeds.forEach { seed ->
        val g = item("be.cache.lru", seed)
        val capacity = ints(g.prompt).first()
        val sequence = g.prompt.substringAfter("порядке: ").removeSuffix("?").split(" ")
        val cache = ArrayList<String>()
        var misses = 0
        for (k in sequence) {
            if (cache.remove(k)) cache.add(k) else {
                misses++
                if (cache.size == capacity) cache.removeAt(0)
                cache.add(k)
            }
        }
        assertEquals(misses.toString(), g.answer)
    }

    @Test fun recursionOracle() = seeds.forEach { seed ->
        val g = item("gen.recursion.calls", seed)
        val code = g.code!!
        val base = Regex("n <= (\\d+)").find(code)!!.groupValues[1].toInt()
        val first = Regex("(?:(\\d+) \\* )?f\\(n - 1\\)").find(code)!!
        val second = Regex("(?:(\\d+) \\* )?f\\(n - ([23])\\)").find(code)!!
        val m1 = first.groupValues[1].ifEmpty { "1" }.toLong()
        val m2 = second.groupValues[1].ifEmpty { "1" }.toLong()
        val step = second.groupValues[2].toInt()
        val k = Regex("f\\((\\d+)\\)").findAll(code).last().groupValues[1].toInt()
        var calls = 0
        fun f(n: Int): Long { calls++; return if (n <= base) 1 else m1 * f(n - 1) + m2 * f(n - step) }
        val value = f(k)
        assertEquals(if ("вызвана" in g.prompt) calls.toString() else value.toString(), g.answer)
    }

    @Test fun closuresOracle() = seeds.forEach { seed ->
        val g = item("fe.js.closures", seed)
        val n = Regex("i < (\\d+)").find(g.code!!)!!.groupValues[1].toInt()
        val m = Regex("j < (\\d+)").find(g.code!!)!!.groupValues[1].toInt()
        val p = Regex("=> i(?: \\* (\\d+))?\\)").find(g.code!!)!!.groupValues[1].ifEmpty { "1" }.toInt()
        val q = Regex("=> j(?: \\* (\\d+))?\\)").find(g.code!!)!!.groupValues[1].ifEmpty { "1" }.toInt()
        assertEquals((p * n * n + q * (0 until m).sum()).toString(), g.answer)
    }

    @Test fun susOracle() = seeds.forEach { seed ->
        val g = item("ds.sus", seed)
        val answers = g.prompt.substringAfter("по порядку: ").substringBefore(".").split(", ").map { it.toInt() }
        val sum = answers.withIndex().sumOf { (i, x) -> if (i % 2 == 0) x - 1 else 5 - x }
        assertEquals(sum * 2.5, g.answer.toDouble())
    }

    @Test fun gridOracle() = seeds.forEach { seed ->
        val g = item("ds.grid", seed)
        val (width, columns, gutter, margin) = ints(g.prompt)
        assertEquals(((width - 2 * margin - (columns - 1) * gutter) / columns).toString(), g.answer)
    }

    @Test fun fittsOracle() = seeds.forEach { seed ->
        val g = item("ds.fitts", seed)
        val numbers = ints(g.prompt.substringAfter("a = "))
        val (a, b) = numbers
        val width = ints(g.prompt.substringAfter("шириной ")).first()
        val distance = ints(g.prompt.substringAfter("расстоянии ")).first()
        assertEquals(a + b * log2(distance.toDouble() / width + 1), g.answer.toDouble(), 1e-9)
    }

    @Test fun quorumOracle() = seeds.forEach { seed ->
        val g = item("be.distributed.quorum", seed)
        val n = ints(g.prompt.substringAfter("N = ")).first()
        val expected = when {
            "кворум Q" in g.prompt -> n / 2 + 1
            "W = " in g.prompt && "минимальное число узлов R" in g.prompt -> n - ints(g.prompt.substringAfter("W = ")).first() + 1
            "R = " in g.prompt -> n - ints(g.prompt.substringAfter("R = ")).first() + 1
            else -> n - ints(g.prompt.substringAfter("W = ")).first()
        }
        assertEquals(expected.toString(), g.answer)
    }

    @Test fun percentileOracle() = seeds.forEach { seed ->
        val g = item("da.stats.percentile", seed)
        val n = ints(g.prompt).first()
        val values = g.prompt.substringAfter("запросов: ").substringBefore(". Найдите").split(", ").map { it.toInt() }
        assertEquals(n, values.size)
        val p = ints(g.prompt.substringAfter("Найдите ")).first()
        assertEquals(values.sorted()[ceil(p * n / 100.0).toInt() - 1].toString(), g.answer)
    }

    @Test fun sqlGroupByOracle() = seeds.forEach { seed ->
        val g = item("be.sql.groupby", seed)
        val rows = g.code!!.lines().filter { Regex("^\\d+\\s*\\|").containsMatchIn(it) }.map { line -> line.split("|").map { it.trim() } }
        val threshold = ints(g.code!!.substringAfter("HAVING")).last()
        val totals = rows.groupBy { it[1] }.mapValues { (_, r) -> r.sumOf { it[2].toInt() } }
        assertEquals(totals.values.filter { it > threshold }.sum().toString(), g.answer)
    }

    @Test fun sqlNullOracle() = seeds.forEach { seed ->
        val g = item("be.sql.null", seed)
        val bonuses = g.code!!.lines().filter { Regex("^\\d+\\s*\\|").containsMatchIn(it) }
            .map { it.split("|").last().trim() }.mapNotNull { if (it == "NULL") null else it.toInt() }
        val query = g.code!!.lines().last()
        val expected = when {
            "AVG" in query -> bonuses.average()
            "NOT IN" in query -> { val (x, y) = ints(query.substringAfter("NOT IN")); bonuses.count { it != x && it != y }.toDouble() }
            else -> { val x = ints(query.substringAfter("<>")).first(); bonuses.count { it != x }.toDouble() }
        }
        assertEquals(expected, g.answer.toDouble(), 1e-3)
    }

    @Test fun contrastLevelOracle() = seeds.forEach { seed ->
        val g = item("ds.contrast", seed)
        val colors = Regex("#([0-9A-F]{6})").findAll(g.prompt).map { m -> (0..2).map { m.groupValues[1].substring(it * 2, it * 2 + 2).toInt(16) } }.toList()
        val ratio = contrastRatio(colors[0], colors[1])
        val level = when { ratio >= 7 -> 0; ratio >= 4.5 -> 1; ratio >= 3 -> 2; else -> 3 }
        assertEquals(level.toString(), g.answer)
    }

    @Test fun reactBatchingOracle() = seeds.forEach { seed ->
        val g = item("fe.react.batching", seed)
        val start = Regex("useState\\((\\d+)\\)").find(g.code!!)!!.groupValues[1].toInt()
        var state = start
        for (line in g.code!!.lines().filter { "setN(" in it }) {
            val v = ints(line).last()
            state = if ("prev" in line) state + v else start + v
        }
        assertEquals(state.toString(), g.answer)
    }

    @Test fun eventLoopOracle() = seeds.forEach { seed ->
        val g = item("fe.js.eventloop", seed)
        val lines = g.code!!.lines()
        fun label(line: String) = Regex("\"([A-H])\"").find(line)!!.groupValues[1]
        val sync = lines.filter { it.startsWith("console.log") }.map(::label)
        val micro = lines.filter { it.startsWith("Promise") || it.startsWith("queueMicrotask") }.map(::label)
        val timers = lines.filter { it.startsWith("setTimeout") }.map(::label)
        assertEquals((sync + micro + timers).joinToString(" "), g.options[g.answer.toInt()])
    }

    @Test fun bayesOracle() = seeds.forEach { seed ->
        val g = item("da.prob.bayes", seed)
        val numbers = Regex("\\d+(?:,\\d+)?").findAll(g.prompt).map { it.value.replace(',', '.').toDouble() }.toList()
        val (p, tpr, fpr) = numbers
        val expected = 100 * p / 100 * tpr / 100 / (p / 100 * tpr / 100 + (1 - p / 100) * fpr / 100)
        assertTrue(abs(expected - g.answer.toDouble()) < 1e-3, "stored key keeps 4 decimals")
        assertTrue(g.isCorrect(expected.roundToInt().toString()))
    }

    @Test fun numericParsingAcceptsLocalFormats() {
        assertEquals(3.5, AnswerKey.parseNumber(" 3,5 "))
        assertEquals(-2.0, AnswerKey.parseNumber("−2"))
        assertEquals(1200.0, AnswerKey.parseNumber("1 200"))
        assertEquals(12.0, AnswerKey.parseNumber("12%"))
        assertEquals(null, AnswerKey.parseNumber("двенадцать"))
    }
}
