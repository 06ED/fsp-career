package ru.fsp.career.assessment.validation

import ru.fsp.career.assessment.domain.*
import java.io.File
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Validation study for the adaptive assessment: `gradle validateAssessment` writes
 * docs/validation/assessment-results.md (report, Russian) and assessment-results.json (raw numbers).
 * Fully deterministic for a given SEED; runs in about a minute.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "../../docs/validation")
    val started = System.currentTimeMillis()
    val study = AssessmentStudy(SEED)
    val results = study.run()
    out.mkdirs()
    File(out, "assessment-results.json").writeText(toJson(results) + "\n")
    File(out, "assessment-results.md").writeText(Report(results).markdown())
    println("Assessment validation written to ${out.canonicalPath} in ${(System.currentTimeMillis() - started) / 1000} s")
}

const val SEED = 20261006L

private val SPECS = AssessmentCatalog.specializations.map { it.id }

class AssessmentStudy(private val seed: Long) {
    private val legacy = LegacyBank()
    private fun rng(offset: Int) = Random(seed * 1_000_003 + offset)

    private fun <T> timed(label: String, block: () -> T): T {
        val t0 = System.currentTimeMillis()
        return block().also { println("  $label: ${(System.currentTimeMillis() - t0) / 1000.0} s") }
    }

    fun run(): Map<String, Any?> {
        val main = timed("main study") { SPECS.mapIndexed { i, spec -> spec to mainStudy(spec, N_MAIN, rng(10 + i)) }.toMap() }
        val pooled = pool(main.values.toList())
        return linkedMapOf(
            "meta" to meta(),
            "main" to main.mapValues { it.value.summary() },
            "pooled" to pooled,
            "itemAnalysis" to timed("item analysis") { itemAnalysis(main) },
            "comparability" to timed("comparability") { comparability() },
            "leak" to timed("leak") { leak(main) },
            "sensitivity" to timed("sensitivity") { sensitivity() },
        )
    }

    private fun meta() = linkedMapOf(
        "seed" to seed,
        "simuleesPerSpecialization" to N_MAIN,
        "testLength" to Cat.TEST_LENGTH,
        "decisionMarginK" to Grading.K,
        "cuts" to Grade.entries.associate { it.id to it.cut },
        "anchors" to Grade.entries.associate { it.id to it.anchor },
        "sigmaInstance" to SimConfig().sigmaInstance,
        "families" to ItemBank.families.size,
        "parametricFamilies" to ItemBank.families.count { it.kind == FamilyKind.PARAMETRIC },
        "poolSizes" to SPECS.associateWith { ItemBank.pool(it).size },
        "declarationModel" to "declared = grade(theta + 0.15 + 0.6*N(0,1))",
        "cooldownDays" to RetakePolicy.COOLDOWN_DAYS,
    )

    // ---------------------------------------------------------------- 1. main study (accuracy, retest, baseline)

    class Record(
        val theta: Double, val reference: Grade, val declared: Grade,
        val first: SimPath, val second: SimPath,
        val legacyFirst: LegacyBank.LegacyResult, val legacySecond: LegacyBank.LegacyResult,
    )

    class MainResult(val spec: String, val records: List<Record>, val simulator: Simulator) {
        fun summary(): Map<String, Any?> = summarize(records) + mapOf("exposure" to exposure())
        fun exposure(): Map<String, Double> = simulator.pool.associate { it.id to (simulator.exposure[it.id] ?: 0L).toDouble() / simulator.attempts }
    }

    private fun mainStudy(spec: String, n: Int, rng: Random, config: SimConfig = SimConfig()): MainResult {
        val simulator = Simulator(spec, config)
        val records = List(n) {
            val theta = rng.gaussian()
            val declared = declaredGrade(theta, rng)
            Record(theta, Grade.reference(theta), declared,
                simulator.path(theta, declared, rng), simulator.path(theta, declared, rng),
                legacy.attempt(spec, theta, rng), legacy.attempt(spec, theta, rng))
        }
        return MainResult(spec, records, simulator)
    }

    private fun pool(results: List<MainResult>) = summarize(results.flatMap { it.records })

    // ---------------------------------------------------------------- 2. item analysis (linear pilot design)

    private fun itemAnalysis(main: Map<String, MainResult>): Map<String, Any?> {
        val rows = mutableListOf<Map<String, Any?>>()
        SPECS.forEachIndexed { index, spec ->
            val home = ItemBank.pool(spec).filter { it.specializations.first() == spec }
            val pool = ItemBank.pool(spec)
            val rng = rng(200 + index)
            val config = SimConfig()
            val scores = List(N_PILOT) {
                val theta = rng.gaussian()
                theta to pool.map { f ->
                    val instance = f.instance(rng.nextLong())
                    val key = QuestionKey.of(f, instance)
                    val delta = Random(key.hashCode().toLong() * 7919 + 17).gaussian() * config.sigmaInstance
                    rng.nextDouble() < Irt.probability(f.params.copy(b = f.b + delta), theta)
                }
            }
            val totals = scores.map { (_, r) -> r.count { it } }
            val exposure = main.getValue(spec).exposure()
            for (f in home) {
                val j = pool.indexOf(f)
                val item = scores.map { (_, r) -> if (r[j]) 1.0 else 0.0 }
                val rest = scores.indices.map { (totals[it] - item[it]) }
                val withTheta = Metrics.pearson(item, scores.map { it.first })
                rows += linkedMapOf(
                    "id" to f.id, "title" to f.title, "specialization" to spec, "topic" to f.topic, "kind" to f.kind.name,
                    "level" to f.level.id, "a" to f.a, "b" to f.b, "c" to f.params.c,
                    "pValue" to item.average(),
                    "itemRest" to Metrics.pearson(item, rest),
                    "pointBiserialTheta" to withTheta,
                    "meanInformation" to meanInformation(f),
                    "catExposure" to exposure[f.id],
                )
            }
        }
        val itemRest = rows.map { it["itemRest"] as Double }
        return linkedMapOf(
            "simulees" to N_PILOT,
            "families" to rows,
            "meanItemRest" to itemRest.average(),
            "minItemRest" to itemRest.min(),
            "flaggedBelow020" to rows.filter { (it["itemRest"] as Double) < 0.2 }.map { it["id"] },
            "maxCatExposure" to rows.maxOf { (it["catExposure"] as Double?) ?: 0.0 },
        )
    }

    /** E_θ~N(0,1)[I(θ)] by quadrature. */
    private fun meanInformation(f: ItemFamily): Double {
        val step = 0.05
        return Irt.grid.sumOf { t -> Irt.information(f.params, t) * exp(-t * t / 2) / sqrt(2 * Math.PI) * step }
    }

    // ---------------------------------------------------------------- 3. comparability of forms

    private fun comparability(): Map<String, Any?> {
        val points = listOf(-1.5, -0.5, 0.15, 0.8, 1.5)
        val conditional = points.mapIndexed { pi, theta ->
            val target = Grade.reference(theta)
            val perSpec = SPECS.mapIndexed { si, spec ->
                val sim = Simulator(spec, SimConfig())
                val rng = rng(300 + pi * 10 + si)
                List(N_CONDITIONAL) { sim.attempt(theta, target, rng) }
            }.flatten()
            val estimates = perSpec.map { it.estimate.theta }
            linkedMapOf(
                "theta" to theta, "target" to target.id,
                "bias" to estimates.average() - theta,
                "sdTheta" to Metrics.sd(estimates),
                "meanSe" to perSpec.map { it.estimate.se }.average(),
                "sdScore" to Metrics.sd(perSpec.map { it.decision.score.toDouble() }),
                "passRate" to Metrics.rate(perSpec.map { it.decision.passed }),
            )
        }
        // Linear-form view: expected % correct at θ = 0 differs between randomly drawn forms.
        val rng = rng(390)
        val newForms = SPECS.flatMap { spec ->
            val pool = ItemBank.pool(spec)
            List(N_FORMS) { pool.shuffled(rng).take(Cat.TEST_LENGTH).map { Irt.probability(it.params, 0.0) }.average() * 100 }
        }
        val oldForms = SPECS.flatMap { spec -> List(N_FORMS) { legacy.form(spec, rng).map { Irt.probability(it.second, 0.0) }.average() * 100 } }
        return linkedMapOf(
            "conditional" to conditional,
            "linearFormsAtTheta0" to linkedMapOf(
                "newBankExpectedPercentSd" to Metrics.sd(newForms), "newBankExpectedPercentMean" to newForms.average(),
                "oldBankExpectedPercentSd" to Metrics.sd(oldForms), "oldBankExpectedPercentMean" to oldForms.average(),
            ),
        )
    }

    // ---------------------------------------------------------------- 4. leak resistance

    private fun leak(main: Map<String, MainResult>): Map<String, Any?> {
        val overlap = SPECS.associateWith { spec ->
            val firsts = main.getValue(spec).records.map { it.first.attempts.first() }
            val rng = rng(400 + SPECS.indexOf(spec))
            val pairs = List(N_PAIRS) { firsts[rng.nextInt(firsts.size)] to firsts[rng.nextInt(firsts.size)] }.filter { it.first !== it.second }
            val same = pairs.map { (a, b) -> a.items.map { it.key }.toSet().intersect(b.items.map { it.key }.toSet()).size.toDouble() / Cat.TEST_LENGTH }
            val families = pairs.map { (a, b) -> a.items.map { it.family.id }.toSet().intersect(b.items.map { it.family.id }.toSet()).size.toDouble() / Cat.TEST_LENGTH }
            val legacyRecords = main.getValue(spec).records.map { it.legacyFirst }
            val legacyPairs = List(N_PAIRS) { legacyRecords[rng.nextInt(legacyRecords.size)] to legacyRecords[rng.nextInt(legacyRecords.size)] }
            linkedMapOf(
                "identicalInstanceShare" to same.average(),
                "pairsWithAnyIdenticalInstance" to Metrics.rate(same.map { it > 0 }),
                "sameFamilyShare" to families.average(),
                "legacyIdenticalQuestionShare" to legacyPairs.map { (a, b) -> a.itemIds.toSet().intersect(b.itemIds.toSet()).size / 5.0 }.average(),
            )
        }
        val collisions = ItemBank.families.map { f ->
            val counts = HashMap<String, Int>()
            val rng = rng(500 + ItemBank.families.indexOf(f))
            repeat(N_COLLISION) { counts.merge(QuestionKey.of(f, f.instance(rng.nextLong())).text, 1, Int::plus) }
            val m = N_COLLISION.toDouble()
            linkedMapOf(
                "id" to f.id, "kind" to f.kind.name,
                "distinctQuestions" to counts.size,
                "collisionProbability" to counts.values.sumOf { it.toDouble() * (it - 1) } / (m * (m - 1)),
            )
        }
        val attacks = SPECS.associateWith { spec -> attack(spec, main.getValue(spec)) }
        return linkedMapOf(
            "overlap" to overlap,
            "collisions" to linkedMapOf(
                "drawsPerFamily" to N_COLLISION,
                "meanParametric" to collisions.filter { it["kind"] == "PARAMETRIC" }.map { it["collisionProbability"] as Double }.average(),
                "meanVariants" to collisions.filter { it["kind"] == "VARIANTS" }.map { it["collisionProbability"] as Double }.average(),
                "families" to collisions,
            ),
            "attack" to attacks,
        )
    }

    /** Twins: identical RNG streams, one with a memorized leak. Gains are attacker − honest. */
    private fun attack(spec: String, honest: MainResult): Map<String, Any?> {
        val served = honest.records.flatMap { r -> (r.first.attempts + r.second.attempts).flatMap { a -> a.items.map { it.key } } }
        val variantKeys = ItemBank.pool(spec).filter { it.kind == FamilyKind.VARIANTS }.flatMap { f ->
            val rng = rng(600 + ItemBank.families.indexOf(f))
            List(2000) { QuestionKey.of(f, f.instance(rng.nextLong())) }
        }.toSet()
        val scenarios = linkedMapOf<String, Set<QuestionKey>>()
        val pick = rng(700 + SPECS.indexOf(spec))
        val distinct = served.toSet()
        for (k in LEAK_SIZES) {
            // A leak is a dump of questions real candidates saw: drawn by administration, so popular questions leak first.
            val leak = HashSet<QuestionKey>()
            while (leak.size < minOf(k, distinct.size)) leak += served[pick.nextInt(served.size)]
            scenarios["leak$k"] = leak
        }
        scenarios["allVariantFamilies"] = variantKeys
        val result = linkedMapOf<String, Any?>("servedAdministrations" to served.size, "distinctServed" to distinct.size)
        for ((name, leak) in scenarios) {
            val sim = Simulator(spec, SimConfig())
            val rng = rng(800 + SPECS.indexOf(spec) * 10 + scenarios.keys.indexOf(name))
            val rows = List(N_ATTACK) {
                val theta = rng.gaussian(); val declared = declaredGrade(theta, rng); val s = rng.nextLong()
                val h = sim.path(theta, declared, Random(s)); val a = sim.path(theta, declared, Random(s), leak)
                Triple(theta, h, a)
            }
            val hits = rows.flatMap { it.third.attempts.first().items }.let { items -> Metrics.rate(items.map { it.key in leak }) }
            result[name] = linkedMapOf(
                "leakedQuestions" to leak.size,
                "hitRate" to hits,
                "scoreGain" to rows.map { (it.third.attempts.first().decision.score - it.second.attempts.first().decision.score).toDouble() }.average(),
                "thetaGain" to rows.map { it.third.attempts.first().estimate.theta - it.second.attempts.first().estimate.theta }.average(),
                "overGradingHonest" to Metrics.rate(rows.map { it.second.confirmed > Grade.reference(it.first) }),
                "overGradingAttack" to Metrics.rate(rows.map { it.third.confirmed > Grade.reference(it.first) }),
            )
        }
        val rng = rng(900 + SPECS.indexOf(spec))
        val legacyRows = List(N_ATTACK) { val t = rng.gaussian(); Triple(t, legacy.attempt(spec, t, rng), legacy.attempt(spec, t, rng, memorized = true)) }
        result["legacyFullBankMemorized"] = linkedMapOf(
            "leakedQuestions" to 25,
            "hitRate" to 1.0,
            "scoreGain" to legacyRows.map { (it.third.score - it.second.score).toDouble() }.average(),
            "overGradingHonest" to Metrics.rate(legacyRows.map { it.second.grade > Grade.reference(it.first) }),
            "overGradingAttack" to Metrics.rate(legacyRows.map { it.third.grade > Grade.reference(it.first) }),
        )
        return result
    }

    // ---------------------------------------------------------------- 5. sensitivity (k, σ, length)

    private fun sensitivity(): Map<String, Any?> {
        fun sweep(label: String, offset: Int, configs: List<Pair<Any, SimConfig>>) = configs.map { (value, config) ->
            // Common random numbers: every configuration sees the same simulees, so differences come from the configuration.
            val records = SPECS.flatMapIndexed { si, spec -> mainStudy(spec, N_SWEEP, rng(offset + si), config).records }
            linkedMapOf(label to value) + summarize(records).filterKeys { it in SWEEP_KEYS }
        }
        return linkedMapOf(
            "simuleesPerSpecialization" to N_SWEEP,
            "k" to sweep("k", 1000, listOf(0.0, 0.1, 0.25, 0.5, 0.75, 1.0).map { it to SimConfig(k = it) }),
            "sigmaInstance" to sweep("sigmaInstance", 1100, listOf(0.0, 0.25, 0.5).map { it to SimConfig(sigmaInstance = it) }),
            "length" to sweep("length", 1200, listOf(8, 12, 16).map { it to SimConfig(length = it) }),
            "kUpgrade" to sweep("kUpgrade", 1300, listOf(0.0, 0.25, 0.5).map { it to SimConfig(kUpgrade = it) }),
            "selection" to sweep("selection", 1400, listOf("theta" to SimConfig(), "cut" to SimConfig(cutSelection = true))),
        )
    }

    companion object {
        const val N_MAIN = 2000
        const val N_PILOT = 1500
        const val N_CONDITIONAL = 300
        const val N_FORMS = 1000
        const val N_PAIRS = 3000
        const val N_COLLISION = 3000
        const val N_ATTACK = 600
        const val N_SWEEP = 1000
        val LEAK_SIZES = listOf(200, 1000, 5000)
        val SWEEP_KEYS = setOf("accuracy", "macroF1", "overGrading", "underGrading", "retestQwk", "retestAgreement", "meanAttempts", "meanSe", "thetaCorrelation")

        fun summarize(records: List<Record>): Map<String, Any?> {
            val reference = records.map { it.reference }
            val confirmed = records.map { it.first.confirmed }
            val retest = records.map { it.second.confirmed }
            val firstAttempts = records.map { it.first.attempts.first() }
            val secondAttempts = records.map { it.second.attempts.first() }
            val truth = records.map { it.theta }
            val legacyGrades = records.map { it.legacyFirst.grade }
            val passedTruth = records.map { it.theta >= it.declared.cut }
            val passed = firstAttempts.map { it.decision.passed }
            fun group(filter: (Record) -> Boolean): Map<String, Any?> {
                val subset = records.filter(filter)
                return linkedMapOf("n" to subset.size,
                    "accuracy" to if (subset.isEmpty()) null else Metrics.accuracy(subset.map { it.reference }, subset.map { it.first.confirmed }),
                    "meanAttempts" to subset.map { it.first.attempts.size.toDouble() }.average())
            }
            return linkedMapOf(
                "n" to records.size,
                "referenceDistribution" to Grade.entries.associate { g -> g.id to reference.count { it == g } },
                "accuracy" to Metrics.accuracy(reference, confirmed),
                "macroF1" to Metrics.macroF1(reference, confirmed),
                "confusion" to Metrics.confusion(reference, confirmed),
                "overGrading" to Metrics.rate(records.map { it.first.confirmed > it.reference }),
                "underGrading" to Metrics.rate(records.map { it.first.confirmed < it.reference }),
                "retestAgreement" to Metrics.accuracy(confirmed, retest),
                "retestQwk" to Metrics.weightedKappa(confirmed, retest),
                "retestLinearKappa" to Metrics.weightedKappa(confirmed, retest, quadratic = false),
                "retestThetaCorrelation" to Metrics.pearson(firstAttempts.map { it.estimate.theta }, secondAttempts.map { it.estimate.theta }),
                "thetaCorrelation" to Metrics.pearson(firstAttempts.map { it.estimate.theta }, truth),
                "meanSe" to firstAttempts.map { it.estimate.se }.average(),
                "marginalReliability" to firstAttempts.map { it.estimate.theta }.let { e ->
                    val v = Metrics.sd(e).let { it * it }; v / (v + firstAttempts.map { it.estimate.se * it.estimate.se }.average())
                },
                "decisionSensitivity" to records.indices.filter { passedTruth[it] }.let { idx -> Metrics.rate(idx.map { passed[it] }) },
                "decisionSpecificity" to records.indices.filter { !passedTruth[it] }.let { idx -> Metrics.rate(idx.map { !passed[it] }) },
                "decisionConsistency" to Metrics.rate(records.map { it.first.attempts.first().decision.passed == it.second.attempts.first().decision.passed }),
                "meanAttempts" to records.map { it.first.attempts.size.toDouble() }.average(),
                "byDeclaration" to linkedMapOf(
                    "overDeclared" to group { it.declared > it.reference },
                    "exact" to group { it.declared == it.reference },
                    "underDeclared" to group { it.declared < it.reference },
                ),
                "legacy" to linkedMapOf(
                    "accuracy" to Metrics.accuracy(reference, legacyGrades),
                    "macroF1" to Metrics.macroF1(reference, legacyGrades),
                    "confusion" to Metrics.confusion(reference, legacyGrades),
                    "overGrading" to Metrics.rate(records.map { it.legacyFirst.grade > it.reference }),
                    "retestAgreement" to Metrics.accuracy(legacyGrades, records.map { it.legacySecond.grade }),
                    "retestQwk" to Metrics.weightedKappa(legacyGrades, records.map { it.legacySecond.grade }),
                    "scoreThetaCorrelation" to Metrics.pearson(records.map { it.legacyFirst.score.toDouble() }, truth),
                    "retestScoreCorrelation" to Metrics.pearson(records.map { it.legacyFirst.score.toDouble() }, records.map { it.legacySecond.score.toDouble() }),
                    "seniorShare" to Metrics.rate(legacyGrades.map { it == Grade.SENIOR }),
                ),
            )
        }
    }
}

/** Minimal JSON writer for the nested maps/lists of the study (keeps the harness dependency-free). */
fun toJson(value: Any?, indent: String = ""): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    is Double -> if (value.isNaN() || value.isInfinite()) "null" else "%.6f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')
    is Number, is Boolean -> value.toString()
    is Grade -> "\"${value.id}\""
    is Map<*, *> -> if (value.isEmpty()) "{}" else value.entries.joinToString(",\n", "{\n", "\n$indent}") { (k, v) -> "$indent  \"$k\": ${toJson(v, "$indent  ")}" }
    is Iterable<*> -> {
        val items = value.toList()
        if (items.all { it == null || it is Number || it is Boolean || it is String && it.length < 40 }) items.joinToString(", ", "[", "]") { toJson(it) }
        else items.joinToString(",\n", "[\n", "\n$indent]") { "$indent  ${toJson(it, "$indent  ")}" }
    }
    else -> toJson(value.toString())
}
