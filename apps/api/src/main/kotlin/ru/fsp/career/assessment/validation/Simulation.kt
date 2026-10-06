package ru.fsp.career.assessment.validation

import ru.fsp.career.assessment.Blueprints
import ru.fsp.career.assessment.Cat
import ru.fsp.career.assessment.Decision
import ru.fsp.career.assessment.Estimate
import ru.fsp.career.assessment.FamilyKind
import ru.fsp.career.assessment.Generated
import ru.fsp.career.assessment.Grade
import ru.fsp.career.assessment.Grading
import ru.fsp.career.assessment.Irt
import ru.fsp.career.assessment.ItemBank
import ru.fsp.career.assessment.ItemFamily
import ru.fsp.career.assessment.ItemParams
import ru.fsp.career.assessment.LikelihoodGrid
import ru.fsp.career.assessment.PastAttempt
import ru.fsp.career.assessment.RetakePolicy
import ru.fsp.career.assessment.RetakeReason
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * Synthetic examinees for the validation study. The simulation drives the PRODUCTION engine objects
 * (ItemBank generators, Blueprints, Cat.nextFamily, LikelihoodGrid/EAP, Grading, RetakePolicy) and only
 * replaces the human with a response model.
 */

fun Random.gaussian(): Double {
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

/**
 * What a person would memorize and share. Parametric families: the exact question (text + code, plus the
 * correct option text for choice items) — new numbers make a new question. Variant families: only the
 * scenario (variant index; for hand-built ones the correct answer with digits stripped) — an attacker who
 * saw one wording recognizes all its rewordings. This deliberately favours the attacker.
 */
data class QuestionKey(val family: String, val text: String) {
    companion object {
        fun of(family: ItemFamily, instance: Generated): QuestionKey = if (family.kind == FamilyKind.VARIANTS) {
            QuestionKey(family.id, instance.variant?.toString() ?: instance.options[instance.answer.toInt()].replace(Regex("\\d+"), "#"))
        } else {
            val correct = if (instance.isChoice) instance.options[instance.answer.toInt()] else ""
            QuestionKey(family.id, instance.prompt + "\u0000" + (instance.code ?: "") + "\u0000" + correct)
        }
    }
}

data class SimItem(val family: ItemFamily, val key: QuestionKey, val correct: Boolean)

data class SimAttempt(val target: Grade, val estimate: Estimate, val decision: Decision, val items: List<SimItem>)

data class SimPath(val declared: Grade, val confirmed: Grade, val attempts: List<SimAttempt>)

class SimConfig(
    /** SD of instance-level difficulty around the family b (imperfect isomorphism). */
    val sigmaInstance: Double = 0.25,
    val k: Double = Grading.K,
    val length: Int = Cat.TEST_LENGTH,
    val kUpgrade: Double = Grading.K_UPGRADE,
    /** Experimental: pick items by information at the decision cut nearest to θ̂ instead of at θ̂. */
    val cutSelection: Boolean = false,
)

class Simulator(val specialization: String, val config: SimConfig) {
    val pool: List<ItemFamily> = ItemBank.pool(specialization)
    private val quotas = Blueprints.blueprintFor(specialization, null, config.length).quotas
    /** Shared across all simulees of this simulator, like the production exposure counters. */
    val exposure = HashMap<String, Long>()
    var attempts = 0L
        private set

    /**
     * Instance difficulty offset δ ~ N(0, σ²), deterministic per question so a repeated instance keeps its
     * difficulty. The engine never sees δ: it scores with the family parameters only.
     */
    private fun delta(key: QuestionKey): Double =
        if (config.sigmaInstance == 0.0) 0.0 else Random(key.hashCode().toLong() * 7919 + 17).gaussian() * config.sigmaInstance

    fun attempt(theta: Double, target: Grade, rng: Random, leak: Set<QuestionKey>? = null): SimAttempt {
        val grid = LikelihoodGrid()
        val administered = ArrayList<ItemFamily>()
        val items = ArrayList<SimItem>()
        attempts++
        repeat(config.length) {
            val routing = Cat.routingEstimate(target, grid).theta
            val point = if (!config.cutSelection) routing
            else listOfNotNull(target.cut, target.next()?.cut).minBy { kotlin.math.abs(it - routing) }
            val family = Cat.nextFamily(pool, quotas, administered, point, { exposure[it] ?: 0L }, rng) ?: return@repeat
            exposure.merge(family.id, 1L, Long::plus)
            val instance = family.instance(rng.nextLong())
            val key = QuestionKey.of(family, instance)
            val p = family.params
            val correct = if (leak != null && key in leak) true
            else rng.nextDouble() < Irt.probability(ItemParams(p.a, p.b + delta(key), p.c), theta)
            grid.add(p, correct)
            administered += family
            items += SimItem(family, key, correct)
        }
        val estimate = Cat.finalEstimate(grid)
        return SimAttempt(target, estimate, Grading.decide(target, estimate, config.k, config.kUpgrade), items)
    }

    /**
     * Full candidate path: test for the declared grade, then the immediate follow-ups RetakePolicy allows
     * (lower after a failure, higher after a confident pass) while they can still raise the category.
     */
    fun path(theta: Double, declared: Grade, rng: Random, leak: Set<QuestionKey>? = null): SimPath {
        val history = ArrayList<PastAttempt>()
        val attempts = ArrayList<SimAttempt>()
        var target = declared
        var now = Instant.parse("2026-01-01T00:00:00Z")
        var confirmed: Grade? = null
        while (true) {
            val attempt = attempt(theta, target, rng, leak)
            attempts += attempt
            val after = Grading.confirmedAfter(confirmed, target, attempt.decision.passed)
            confirmed = after
            history += PastAttempt(target, attempt.decision.passed, attempt.decision.confident, after, now)
            now = now.plusSeconds(3600)
            val next = RetakePolicy.options(history, now).firstOrNull {
                it.allowed && it.grade > after &&
                    (it.reason == RetakeReason.LOWER_AFTER_FAIL || it.reason == RetakeReason.UPGRADE_AFTER_CONFIDENT_PASS)
            } ?: break
            target = next.grade
        }
        return SimPath(declared, confirmed!!, attempts)
    }
}

/** Self-assessment model: candidates over-estimate themselves on average (+0.15 SD) with noise SD 0.6. */
fun declaredGrade(theta: Double, rng: Random): Grade = Grade.reference(theta + 0.15 + 0.6 * rng.gaussian())

/**
 * The previous mechanism, reproduced for the baseline: 5 random questions (3 from the specialization, 2
 * general) from the public 25-item bank, 3 options each, grade by percent correct (≥ 80 senior, ≥ 60 middle).
 * Its items were never calibrated; we assign expert priors matching their content (very easy, weakly
 * discriminating: a ∈ [0.6; 1.0], b ∈ [−2.6; −1.4], c = 1/3).
 */
class LegacyBank(seed: Long = 1925) {
    private val rng = Random(seed)
    private fun item() = ItemParams(rng.nextDouble(0.6, 1.0), rng.nextDouble(-2.6, -1.4), 1.0 / 3)
    val general: List<ItemParams> = List(10) { item() }
    val specialty: Map<String, List<ItemParams>> = listOf("backend", "frontend", "data", "product", "design").associateWith { List(3) { item() } }

    data class LegacyResult(val score: Int, val grade: Grade, val itemIds: List<Int>)

    fun form(specialization: String, rng: Random): List<Pair<Int, ItemParams>> {
        val spec = specialty.getValue(specialization).mapIndexed { i, p -> 100 + i to p }.shuffled(rng).take(3)
        val gen = general.mapIndexed { i, p -> i to p }.shuffled(rng).take(2)
        return spec + gen
    }

    fun attempt(specialization: String, theta: Double, rng: Random, memorized: Boolean = false): LegacyResult {
        val form = form(specialization, rng)
        val correct = form.count { (_, p) -> memorized || rng.nextDouble() < Irt.probability(p, theta) }
        val score = correct * 100 / form.size
        val grade = when { score >= 80 -> Grade.SENIOR; score >= 60 -> Grade.MIDDLE; else -> Grade.JUNIOR }
        return LegacyResult(score, grade, form.map { it.first })
    }
}
