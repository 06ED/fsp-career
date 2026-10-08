package ru.fsp.career.matching.domain

import ru.fsp.career.assessment.domain.AssessmentCatalog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Matching engine — pure, deterministic, unit-tested. No database or HTTP here.
 *
 * Two modes (spec §2.1 «Функциональные требования к поиску и подбору»):
 *
 * (a) NEED SELECTION — the employer described a need (specialization, grade, stack, industry, free text).
 *     1. Category gate. Only candidates with an ASSESSED category take part (survey + test, never the
 *        self-declared résumé grade). Specialization must match. Assessed grade vs required grade:
 *        exact → ×1.00; one level higher → ×0.85; two higher → ×0.70 (acceptable, flagged as
 *        overqualified); lower → excluded, unless the employer explicitly allows it (one level → ×0.60).
 *     2. Relevance (0..100) = gradeFit × Σ wᵢ·xᵢ, each xᵢ ∈ [0,1]:
 *          test      30  confirmed test score / 100
 *          text      25  TF-IDF cosine(need description + query + stack ×2, candidate narrative: experience,
 *                        CV roles/descriptions, target roles, soft skills) / 0.35, capped at 1.
 *                        Listed stack TAGS are not part of this document — they are scored once, below.
 *          stack     20  coverage of the required stack; per required skill: confirmed by assessment 1.0,
 *                        listed AND described in the narrative 0.8, only described 0.5,
 *                        only listed 0.5 × r, absent 0. r = (corroborated listed + 1) / (listed + 2) is the
 *                        reliability of the candidate's whole list: long tag lists the narrative never
 *                        mentions (keyword stuffing) lose most of their weight.
 *          fsp       15  Σ points × recency of verified FSP results / 20, capped at 1
 *          industry   5  candidate industry matches the need 1.0; industry only mentioned in text 0.5
 *          freshness  5  1.0 if active ≤ 30 days ago, linear down to 0.3 at one year
 *        Factors that do not apply to a need (no stack given, no industry given, no text at all) are
 *        dropped and the remaining weights are rescaled to sum to 100, so a short need is not penalised.
 *        A candidate without FSP history simply receives 0 for that factor — never a penalty beyond it.
 *     Ties: relevance ↓, then profile strength ↓, then candidate id ↑ (stable, reproducible).
 *
 * (b) BANK / CATEGORY BROWSE — no need text; inside a category candidates are ordered by the strength of
 *     the CONFIRMED profile: strength = 65·test + 25·fsp + 10·freshness (same xᵢ as above).
 *
 * Self-reported data (stack tags, résumé text) can only add need-specific relevance on top of the
 * confirmed category; it can never move a candidate into a category or above the grade gate.
 */

val GRADES = listOf("junior", "middle", "senior")
private val RU = java.util.Locale.forLanguageTag("ru")

/** Numbers inside Russian explanations use the Russian decimal comma regardless of the server locale. */
internal fun fmt2(value: Double): String = String.format(RU, "%.2f", value)

internal fun fmt1(value: Double): String = String.format(RU, "%.1f", value)

internal fun gradeIndex(grade: String?): Int = GRADES.indexOf(grade)

/** A verified achievement as seen by the ranking (FSP registry import or back-office verified record). */
data class AchievementSignal(
    val title: String,
    val issuer: String,
    val points: Int,
    val source: String = "manual",
    val eventName: String? = null,
    val discipline: String? = null,
    val eventDate: LocalDate? = null,
    val level: String? = null,
    val place: Int? = null,
    val result: String? = null,
    val verifiedAt: Instant? = null,
)

/**
 * Everything the engine may know about a candidate. Callers must already have applied the candidate's
 * privacy settings: hidden sections arrive empty and are marked with the *Hidden flags, so hidden data
 * neither contributes points nor leaks through explanations.
 */
data class CandidateSignals(
    val id: String,
    val displayName: String,
    val specialization: String,
    val industry: String,
    val assessedGrade: String?,
    val testScore: Int?,
    val assessedAt: Instant?,
    val selfReportedSkills: List<String>,
    /** Skill keys confirmed by assessment topics. Hook for the assessment module; empty when unavailable. */
    val confirmedSkills: Set<String> = emptySet(),
    val profileText: String,
    val achievements: List<AchievementSignal>,
    val fspLinked: Boolean,
    val lastActiveAt: Instant?,
    val stackHidden: Boolean = false,
    val textHidden: Boolean = false,
    val achievementsHidden: Boolean = false,
)

data class NeedQuery(
    val specialization: String,
    val grade: String,
    val industry: String = "",
    val stack: List<String> = emptyList(),
    val description: String = "",
)

/** Hard filters shared by both modes; `query` refines the need text (mode a) or filters by text (mode b). */
data class CandidateFilter(
    val specialization: String? = null,
    val grade: String? = null,
    val stack: List<String> = emptyList(),
    val fspOnly: Boolean = false,
    val industry: String? = null,
    val query: String? = null,
    val allowLowerGrade: Boolean = false,
)

/** Weights in points out of 100; set one to 0 for an ablation. */
data class MatchingWeights(
    val test: Double = 30.0,
    val text: Double = 25.0,
    val stack: Double = 20.0,
    val fsp: Double = 15.0,
    val industry: Double = 5.0,
    val freshness: Double = 5.0,
    val bankTest: Double = 65.0,
    val bankFsp: Double = 25.0,
    val bankFreshness: Double = 10.0,
    /** Grade-fit multipliers (exact fit is always 1.0). */
    val gradeOneUp: Double = 0.85,
    val gradeTwoUp: Double = 0.70,
    val gradeOneDown: Double = 0.60,
    /** Cosine at which the text factor reaches its maximum. */
    val textFullScoreCosine: Double = 0.35,
)

/** One explainable contribution: `points = weight × value`, where weight is the rescaled maximum. */
data class Factor(
    val code: String,
    val label: String,
    val weight: Double,
    val value: Double,
    val points: Double,
    val text: String,
    val short: String,
)

data class RankedCandidate(
    val candidate: CandidateSignals,
    val relevance: Double,
    val strength: Double,
    val gradeFit: Double,
    val factors: List<Factor>,
    val flags: List<String>,
    val summary: String,
    val textCosine: Double,
)

data class CategoryStat(
    val specialization: String,
    val grade: String,
    val candidates: Int,
    val withFsp: Int,
    val avgTestScore: Double,
    val avgStrength: Double,
    val fit: String,
    val recommended: Boolean,
    val reason: String,
    val avgTextRelevance: Double?,
)

data class Ranking(val results: List<RankedCandidate>, val categories: List<CategoryStat>)

object MatchingPolicy {
    const val STACK_CONFIRMED = 1.0
    const val STACK_LISTED_AND_DESCRIBED = 0.8
    const val STACK_DESCRIBED_ONLY = 0.5
    const val STACK_LISTED_ONLY_MAX = 0.5
    const val FSP_POINTS_FOR_FULL_SCORE = 20.0
    const val NEED_STACK_BOOST = 2.0
    const val GRADE_FIT_EXACT = 1.0

    /** null = excluded by the category gate. */
    fun gradeFit(
        assessed: String,
        required: String,
        allowLower: Boolean,
        w: MatchingWeights = MatchingWeights()
    ): Double? {
        val diff = gradeIndex(assessed) - gradeIndex(required)
        return when {
            diff == 0 -> GRADE_FIT_EXACT
            diff == 1 -> w.gradeOneUp
            diff >= 2 -> w.gradeTwoUp
            diff == -1 && allowLower -> w.gradeOneDown
            else -> null
        }
    }

    /** Older results still count but fade: full weight for 2 years, −20 %/year after, floor 40 %. */
    fun recency(date: LocalDate?, now: Instant): Double {
        if (date == null) return 1.0
        val years = ChronoUnit.DAYS.between(date, now.atZone(ZoneOffset.UTC).toLocalDate()) / 365.25
        return if (years <= 2.0) 1.0 else max(0.4, 1.0 - 0.2 * (years - 2.0))
    }

    fun freshness(lastActive: Instant?, now: Instant): Pair<Double, Long?> {
        val days = lastActive?.let { max(0L, ChronoUnit.DAYS.between(it, now)) } ?: return 0.3 to null
        val value = if (days <= 30) 1.0 else max(0.3, 1.0 - 0.7 * (days - 30) / 335.0)
        return value to days
    }
}

/**
 * Corpus prepared once per request: tokenised profiles plus a TF-IDF model over the whole published pool
 * (IDF over the whole pool, not only the gated category, keeps scores stable when filters change).
 *
 * Two views per candidate:
 *  - `vectors` — the NARRATIVE only (experience, CV, roles, soft skills). Used for need relevance and to
 *    check whether a listed skill is corroborated. Listed stack tags are deliberately left out: they are
 *    scored once, by the stack factor; counting a tag twice would reward keyword stuffing.
 *  - `searchVectors` — narrative + listed tags, used only to FILTER the bank by a free-text query
 *    (an employer typing «Kafka» expects people who listed Kafka).
 */
class PreparedPool(val candidates: List<CandidateSignals>) {
    private val textTerms: Map<String, List<String>> =
        candidates.associate { it.id to TextAnalysis.terms(it.profileText) }
    internal val termSets: Map<String, Set<String>> = textTerms.mapValues { it.value.toSet() }
    internal val skillKeys: Map<String, Set<String>> =
        candidates.associate { c -> c.id to (c.selfReportedSkills.map(TextAnalysis::skillKey).toSet()) }
    private val searchDocuments: Map<String, List<String>> = candidates.associate { c ->
        c.id to textTerms.getValue(c.id) + c.selfReportedSkills.flatMap(TextAnalysis::terms)
    }
    val model = TfIdfModel(candidates.map { searchDocuments.getValue(it.id) })
    internal val vectors: Map<String, Map<String, Double>> = textTerms.mapValues { model.vector(it.value) }
    internal val searchVectors: Map<String, Map<String, Double>> = searchDocuments.mapValues { model.vector(it.value) }

    /**
     * Reliability of a candidate's self-reported stack: Laplace-smoothed share of listed skills that the
     * narrative corroborates, (corroborated + 1) / (listed + 2). An honest, consistent profile → close to 1;
     * a long list of tags never mentioned in the experience → close to 0. Used to discount uncorroborated tags.
     */
    internal val listReliability: Map<String, Double> = candidates.associate { c ->
        val terms = termSets.getValue(c.id)
        val corroborated = c.selfReportedSkills.count { s ->
            TextAnalysis.terms(s).let { t -> t.isNotEmpty() && t.all { it in terms } }
        }
        c.id to (corroborated + 1.0) / (c.selfReportedSkills.size + 2.0)
    }
}

class MatchingEngine(
    private val weights: MatchingWeights = MatchingWeights(),
    private val now: Instant = Instant.now(),
) {
    // ---------------------------------------------------------------- mode (a): need selection

    fun rankForNeed(need: NeedQuery, pool: PreparedPool, filter: CandidateFilter = CandidateFilter()): Ranking {
        val specialization = filter.specialization ?: need.specialization
        val requiredGrade = filter.grade ?: need.grade
        val effectiveNeed = need.copy(specialization = specialization, grade = requiredGrade)
        val needVector = needVector(effectiveNeed, filter.query, pool)
        val surfaces = needSurfaces(effectiveNeed, filter.query)

        val results = pool.candidates.mapNotNull { candidate ->
            if (!assessed(candidate) || !candidate.specialization.equals(
                    specialization,
                    ignoreCase = true
                )
            ) return@mapNotNull null
            if (!passesCommonFilters(candidate, filter, pool)) return@mapNotNull null
            val fit = MatchingPolicy.gradeFit(candidate.assessedGrade!!, requiredGrade, filter.allowLowerGrade, weights)
                ?: return@mapNotNull null
            scoreForNeed(candidate, effectiveNeed, fit, needVector, surfaces, pool)
        }.sortedWith(
            compareByDescending<RankedCandidate> { it.relevance }
                .thenByDescending { it.strength }
                .thenBy { it.candidate.id },
        )
        return Ranking(results, needCategories(effectiveNeed, pool, needVector))
    }

    /** Explanation for one candidate against the need, even if hard filters would hide it (candidate card). */
    fun explain(need: NeedQuery, pool: PreparedPool, candidateId: String): RankedCandidate? {
        val candidate = pool.candidates.firstOrNull { it.id == candidateId } ?: return null
        if (!assessed(candidate) || !candidate.specialization.equals(
                need.specialization,
                ignoreCase = true
            )
        ) return null
        val fit = MatchingPolicy.gradeFit(candidate.assessedGrade!!, need.grade, allowLower = true, w = weights)
            ?: return null
        return scoreForNeed(candidate, need, fit, needVector(need, null, pool), needSurfaces(need, null), pool)
    }

    private fun needVector(need: NeedQuery, query: String?, pool: PreparedPool): Map<String, Double> {
        val stackTerms = need.stack.flatMap { TextAnalysis.terms(it) }
        val terms = TextAnalysis.terms(listOf(need.description, query.orEmpty()).joinToString(" ")) + stackTerms
        // Required-stack terms are boosted ×2: the employer stated them explicitly.
        return pool.model.vector(terms, stackTerms.associateWith { MatchingPolicy.NEED_STACK_BOOST })
    }

    private fun needSurfaces(need: NeedQuery, query: String?): Map<String, String> {
        val out = linkedMapOf<String, String>()
        need.stack.forEach { skill -> TextAnalysis.terms(skill).forEach { out.putIfAbsent(it, skill.trim()) } }
        TextAnalysis.tokens(listOf(need.description, query.orEmpty()).joinToString(" "))
            .forEach { out.putIfAbsent(it.stem, it.surface) }
        return out
    }

    private fun scoreForNeed(
        candidate: CandidateSignals,
        need: NeedQuery,
        gradeFit: Double,
        needVector: Map<String, Double>,
        surfaces: Map<String, String>,
        pool: PreparedPool,
    ): RankedCandidate {
        val textApplies = needVector.isNotEmpty()
        val stackApplies = need.stack.isNotEmpty()
        val industryApplies = need.industry.isNotBlank()

        val raw = mutableListOf<Pair<Double, (Double) -> Factor>>()
        val test = testValue(candidate)
        raw += weights.test to { w ->
            factor(
                "test",
                "Тест",
                w,
                test,
                testText(candidate),
                "тест ${candidate.testScore}/100"
            )
        }

        val cosine = TfIdfModel.cosine(needVector, pool.vectors.getValue(candidate.id))
        if (textApplies) {
            val value = min(1.0, cosine / weights.textFullScoreCosine)
            val shared = TfIdfModel.contributions(needVector, pool.vectors.getValue(candidate.id))
                .take(4).map { surfaces[it.first] ?: it.first }
            val text = when {
                candidate.textHidden && candidate.profileText.isBlank() -> "Описание опыта скрыто кандидатом — текстовая близость не оценивается"
                candidate.profileText.isBlank() -> "Описание опыта не заполнено"
                shared.isEmpty() -> "Описание опыта не пересекается с описанием потребности"
                else -> "Опыт близок к описанию потребности на ${(value * 100).roundToInt()}%: ${shared.joinToString(", ")}"
            }
            raw += weights.text to { w ->
                factor(
                    "text",
                    "Текст",
                    w,
                    value,
                    text,
                    "текст ${(value * 100).roundToInt()}%"
                )
            }
        }

        if (stackApplies) {
            val coverage = stackCoverage(need.stack, candidate, pool)
            raw += weights.stack to { w ->
                factor(
                    "stack",
                    "Стек",
                    w,
                    coverage.value,
                    coverage.text,
                    "стек ${coverage.matched.size}/${need.stack.size}"
                )
            }
        }

        val fsp = fspValue(candidate)
        raw += weights.fsp to { w -> factor("fsp", "ФСП", w, fsp, fspText(candidate), fspShort(candidate)) }

        if (industryApplies) {
            val (value, text) = industry(need.industry, candidate, pool)
            raw += weights.industry to { w ->
                factor(
                    "industry",
                    "Отрасль",
                    w,
                    value,
                    text,
                    if (value >= 1.0) "отрасль совпадает" else if (value > 0) "отрасль в опыте" else "другая отрасль"
                )
            }
        }

        val (freshValue, days) = MatchingPolicy.freshness(candidate.lastActiveAt, now)
        raw += weights.freshness to { w ->
            factor(
                "freshness",
                "Актуальность",
                w,
                freshValue,
                freshnessText(days),
                days?.let { "активен $it дн. назад" } ?: "активность неизвестна")
        }

        // Rescale the applicable weights so the maximum stays 100 points.
        val active = raw.filter { it.first > 0 }
        val scale = 100.0 / active.sumOf { it.first }.coerceAtLeast(1e-9)
        val factors = active.map { (w, build) -> build(w * scale) }
        val base = factors.sumOf { it.points }
        val relevance = base * gradeFit

        val gradeFactor = gradeFactor(candidate.assessedGrade!!, need.grade, gradeFit)
        val allFactors = factors + gradeFactor
        return RankedCandidate(
            candidate = candidate,
            relevance = relevance,
            strength = strength(candidate),
            gradeFit = gradeFit,
            factors = allFactors,
            flags = flags(candidate, need, pool),
            summary = (listOf(gradeFactor.short) + factors.sortedByDescending { it.points }
                .map { it.short }).joinToString(" · "),
            textCosine = cosine,
        )
    }

    // ---------------------------------------------------------------- mode (b): bank / category browse

    fun browse(pool: PreparedPool, filter: CandidateFilter = CandidateFilter()): Ranking {
        // A query of stop words only («и в на») has no terms: treat it as no query instead of filtering everyone out.
        val queryVector = filter.query?.takeIf { it.isNotBlank() }?.let { pool.model.vector(TextAnalysis.terms(it)) }
            ?.takeIf { it.isNotEmpty() }
        val surfaces = filter.query?.let { q -> TextAnalysis.tokens(q).associate { it.stem to it.surface } }.orEmpty()
        val results = pool.candidates.mapNotNull { candidate ->
            if (!assessed(candidate)) return@mapNotNull null
            if (filter.specialization != null && !candidate.specialization.equals(
                    filter.specialization,
                    ignoreCase = true
                )
            ) return@mapNotNull null
            if (filter.grade != null && candidate.assessedGrade != filter.grade) return@mapNotNull null
            if (!passesCommonFilters(candidate, filter, pool)) return@mapNotNull null
            val cosine = queryVector?.let { TfIdfModel.cosine(it, pool.searchVectors.getValue(candidate.id)) }
            if (cosine != null && cosine <= 0.0) return@mapNotNull null
            scoreForBank(candidate, queryVector, cosine, surfaces, pool)
        }.sortedWith(
            compareByDescending<RankedCandidate> { it.strength }
                .thenByDescending { it.candidate.testScore ?: 0 }
                .thenBy { it.candidate.id },
        )
        return Ranking(results, bankCategories(pool))
    }

    private fun scoreForBank(
        candidate: CandidateSignals,
        queryVector: Map<String, Double>?,
        cosine: Double?,
        surfaces: Map<String, String>,
        pool: PreparedPool,
    ): RankedCandidate {
        val (freshValue, days) = MatchingPolicy.freshness(candidate.lastActiveAt, now)
        val parts = listOf(
            Triple(weights.bankTest, "test", testValue(candidate)),
            Triple(weights.bankFsp, "fsp", fspValue(candidate)),
            Triple(weights.bankFreshness, "freshness", freshValue),
        ).filter { it.first > 0 }
        val scale = 100.0 / parts.sumOf { it.first }.coerceAtLeast(1e-9)
        val factors = parts.map { (w, code, value) ->
            when (code) {
                "test" -> factor(code, "Тест", w * scale, value, testText(candidate), "тест ${candidate.testScore}/100")
                "fsp" -> factor(code, "ФСП", w * scale, value, fspText(candidate), fspShort(candidate))
                else -> factor(
                    code,
                    "Актуальность",
                    w * scale,
                    value,
                    freshnessText(days),
                    days?.let { "активен $it дн. назад" } ?: "активность неизвестна")
            }
        }.toMutableList()
        if (queryVector != null && cosine != null) {
            val shared = TfIdfModel.contributions(queryVector, pool.searchVectors.getValue(candidate.id)).take(4)
                .map { surfaces[it.first] ?: it.first }
            // Informational only (weight 0): in the bank the order is the confirmed-profile strength.
            factors += factor(
                "text", "Запрос", 0.0, min(1.0, cosine / weights.textFullScoreCosine),
                "Совпадение с запросом: ${shared.joinToString(", ")}", "запрос: ${shared.firstOrNull().orEmpty()}"
            )
        }
        val strength = factors.sumOf { it.points }
        return RankedCandidate(
            candidate = candidate,
            relevance = strength,
            strength = strength,
            gradeFit = 1.0,
            factors = factors,
            flags = flags(candidate, null, pool),
            summary = factors.filter { it.weight > 0 }.sortedByDescending { it.points }
                .joinToString(" · ") { it.short },
            textCosine = cosine ?: 0.0,
        )
    }

    /** Confirmed-profile strength 0..100 (mode b ordering, secondary key in mode a). */
    fun strength(candidate: CandidateSignals): Double {
        val total = weights.bankTest + weights.bankFsp + weights.bankFreshness
        if (total <= 0) return 0.0
        val fresh = MatchingPolicy.freshness(candidate.lastActiveAt, now).first
        return 100.0 * (weights.bankTest * testValue(candidate) + weights.bankFsp * fspValue(candidate) +
                weights.bankFreshness * fresh) / total
    }

    // ---------------------------------------------------------------- categories

    private fun needCategories(
        need: NeedQuery,
        pool: PreparedPool,
        needVector: Map<String, Double>
    ): List<CategoryStat> {
        val groups = pool.candidates.filter(::assessed).groupBy { it.specialization.lowercase() to it.assessedGrade!! }
        val needSpec = need.specialization.lowercase()

        // Average of the 3 best text cosines per category: how close that category's people are to the need text.
        fun textAffinity(members: List<CandidateSignals>): Double? = if (needVector.isEmpty()) null else
            members.map { TfIdfModel.cosine(needVector, pool.vectors.getValue(it.id)) }.sortedDescending().take(3)
                .takeIf { it.isNotEmpty() }?.average()

        val sameSpec = GRADES.map { grade ->
            val members = groups[needSpec to grade].orEmpty()
            val diff = gradeIndex(grade) - gradeIndex(need.grade)
            val (fit, recommended, reason) = when {
                diff == 0 -> Triple("exact", true, "Совпадают специализация и требуемый грейд")
                diff > 0 -> Triple("higher", true, "Грейд выше требуемого: опытнее, но ожидания по зарплате выше")
                else -> Triple(
                    "lower",
                    false,
                    "Грейд ниже требуемого: кандидаты на вырост (включите «Показывать грейд ниже»)"
                )
            }
            stat(needSpec, grade, members, fit, recommended, reason, textAffinity(members))
        }
        val reference =
            sameSpec.filter { it.fit == "exact" || it.fit == "higher" }.mapNotNull { it.avgTextRelevance }.maxOrNull()
        val adjacent =
            groups.filterKeys { (spec, grade) -> spec != needSpec && gradeIndex(grade) >= gradeIndex(need.grade) }
                .mapNotNull { (key, members) ->
                    val affinity = textAffinity(members) ?: return@mapNotNull null
                    val threshold = max(0.05, 0.6 * (reference ?: 0.0))
                    if (affinity < threshold) return@mapNotNull null
                    stat(
                        key.first, key.second, members, "adjacent", true,
                        "Смежное направление: опыт кандидатов близок к описанию потребности (текстовая близость ${
                            fmt2(
                                affinity
                            )
                        })",
                        affinity
                    )
                }.sortedByDescending { it.avgTextRelevance }.take(2)
        return sameSpec.filter { it.candidates > 0 || it.fit == "exact" }
            .sortedBy { listOf("exact", "higher", "lower").indexOf(it.fit) } + adjacent
    }

    private fun bankCategories(pool: PreparedPool): List<CategoryStat> =
        pool.candidates.filter(::assessed).groupBy { it.specialization.lowercase() to it.assessedGrade!! }
            .map { (key, members) ->
                stat(
                    key.first,
                    key.second,
                    members,
                    "browse",
                    false,
                    "Категория банка кандидатов",
                    null
                )
            }
            .sortedWith(compareBy<CategoryStat> { it.specialization }.thenBy { gradeIndex(it.grade) })

    private fun stat(
        spec: String,
        grade: String,
        members: List<CandidateSignals>,
        fit: String,
        recommended: Boolean,
        reason: String,
        text: Double?
    ) =
        CategoryStat(
            specialization = spec,
            grade = grade,
            candidates = members.size,
            withFsp = members.count { it.achievements.any { a -> a.source == "fsp" } },
            avgTestScore = members.mapNotNull { it.testScore }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            avgStrength = members.map(::strength).takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            fit = fit,
            recommended = recommended,
            reason = reason,
            avgTextRelevance = text,
        )

    // ---------------------------------------------------------------- factors

    private fun assessed(c: CandidateSignals) = c.assessedGrade in GRADES && c.testScore != null

    private fun passesCommonFilters(c: CandidateSignals, f: CandidateFilter, pool: PreparedPool): Boolean {
        if (f.stack.isNotEmpty()) {
            val keys = pool.skillKeys.getValue(c.id) + c.confirmedSkills
            if (!f.stack.all { TextAnalysis.skillKey(it) in keys }) return false
        }
        if (f.fspOnly && c.achievements.isEmpty()) return false
        if (!f.industry.isNullOrBlank()) {
            val wanted = TextAnalysis.terms(f.industry).toSet()
            if (wanted.isNotEmpty() && TextAnalysis.terms(c.industry).none { it in wanted }) return false
        }
        return true
    }

    private fun testValue(c: CandidateSignals) = ((c.testScore ?: 0).coerceIn(0, 100)) / 100.0

    private fun testText(c: CandidateSignals): String {
        val date = c.assessedAt?.atZone(ZoneOffset.UTC)?.toLocalDate()?.let { ", тест от $it" }.orEmpty()
        return "Тест: ${c.testScore}/100, подтверждённый грейд ${c.assessedGrade}$date"
    }

    /** Σ points × recency, saturating at 20 points (≈ two strong federal results). */
    fun fspPoints(c: CandidateSignals): Double =
        c.achievements.sumOf { it.points * MatchingPolicy.recency(it.eventDate, now) }

    private fun fspValue(c: CandidateSignals) = min(1.0, fspPoints(c) / MatchingPolicy.FSP_POINTS_FOR_FULL_SCORE)

    private fun fspText(c: CandidateSignals): String = when {
        c.achievementsHidden -> "Достижения скрыты настройками приватности кандидата — фактор не начислен"
        c.achievements.isEmpty() && c.fspLinked -> "ФСП ID привязан, но результатов в реестре нет — фактор не начислен (это не штраф)"
        c.achievements.isEmpty() -> "История ФСП не привязана — фактор не начислен (это не штраф)"
        else -> "Достижения ФСП: " + c.achievements.sortedByDescending { it.points }.take(3)
            .joinToString("; ") { describeAchievement(it) } +
                (if (c.achievements.size > 3) "; ещё ${c.achievements.size - 3}" else "") +
                " (${fmt1(fspPoints(c))} балл. с учётом давности)"
    }

    private fun fspShort(c: CandidateSignals): String = when {
        c.achievementsHidden -> "ФСП скрыто"
        c.achievements.isEmpty() -> "нет истории ФСП"
        else -> c.achievements.maxBy { it.points }.let { best ->
            "ФСП: ${resultLabel(best.result) ?: "подтверждено"}${best.eventDate?.let { " (${it.year})" }.orEmpty()}"
        }
    }

    private fun stackCoverage(required: List<String>, c: CandidateSignals, pool: PreparedPool): StackCoverage {
        val listed = pool.skillKeys.getValue(c.id)
        val textTerms = pool.termSets.getValue(c.id)
        val reliability = pool.listReliability.getValue(c.id)
        val matched = mutableListOf<String>()
        val corroborated = mutableListOf<String>()
        val listedOnly = mutableListOf<String>()
        val missing = mutableListOf<String>()
        var credit = 0.0
        for (skill in required) {
            val key = TextAnalysis.skillKey(skill)
            val skillTerms = TextAnalysis.terms(skill)
            val inText = skillTerms.isNotEmpty() && skillTerms.all { it in textTerms }
            val inStack = key in listed
            val value = when {
                key in c.confirmedSkills -> MatchingPolicy.STACK_CONFIRMED
                inStack && inText -> MatchingPolicy.STACK_LISTED_AND_DESCRIBED
                inText -> MatchingPolicy.STACK_DESCRIBED_ONLY
                // A bare tag is worth at most 0.5 and is discounted by how consistent the whole list is.
                inStack -> MatchingPolicy.STACK_LISTED_ONLY_MAX * reliability
                else -> 0.0
            }
            credit += value
            if (value > 0) matched += skill.trim() else missing += skill.trim()
            if (value >= MatchingPolicy.STACK_LISTED_AND_DESCRIBED) corroborated += skill.trim()
            if (inStack && !inText && key !in c.confirmedSkills) listedOnly += skill.trim()
        }
        val value = credit / required.size
        val text = buildString {
            append("Стек: ${matched.size} из ${required.size}")
            if (matched.isNotEmpty()) append(" — ${matched.joinToString(", ")}")
            if (missing.isNotEmpty()) append("; нет: ${missing.joinToString(", ")}")
            if (corroborated.isNotEmpty()) append(". Подтверждено описанием опыта: ${corroborated.joinToString(", ")}")
            if (listedOnly.isNotEmpty()) append(
                ". Только заявлено (вес ×${fmt2(MatchingPolicy.STACK_LISTED_ONLY_MAX * reliability)}, " +
                        "согласованность стека с опытом ${(reliability * 100).roundToInt()}%): ${
                            listedOnly.joinToString(
                                ", "
                            )
                        }"
            )
            if (c.stackHidden) append(". Список технологий скрыт кандидатом")
        }
        return StackCoverage(value, matched, text)
    }

    private data class StackCoverage(val value: Double, val matched: List<String>, val text: String)

    /**
     * Industries are compared through the survey catalog key (candidates pick a catalog id, employers may type free
     * text such as «банки»), falling back to plain term overlap for industries outside the catalog.
     */
    private fun industry(needIndustry: String, c: CandidateSignals, pool: PreparedPool): Pair<Double, String> {
        val wanted = TextAnalysis.terms(needIndustry).toSet()
        val needId = AssessmentCatalog.industryId(needIndustry)
        val candidateId = AssessmentCatalog.industryId(c.industry)
        val needLabel = AssessmentCatalog.industryLabel(needIndustry)
        val candidateLabel = AssessmentCatalog.industryLabel(c.industry)
        val mentioned = if (needId != null) {
            val stems = AssessmentCatalog.industryStemsFor(needId)
            pool.termSets.getValue(c.id)
                .any { term -> stems.any { term.startsWith(it) || it.startsWith(term) && term.length >= 4 } }
        } else wanted.isNotEmpty() && pool.termSets.getValue(c.id).containsAll(wanted)
        return when {
            needId != null && needId == candidateId -> 1.0 to "Отрасль совпадает: $candidateLabel"
            needId == null && wanted.isNotEmpty() && TextAnalysis.terms(c.industry)
                .any { it in wanted } -> 1.0 to "Отрасль совпадает: $candidateLabel"

            mentioned -> 0.5 to "Отрасль «$needLabel» упоминается в опыте"
            c.industry.isBlank() -> 0.0 to "Отрасль кандидата не указана"
            else -> 0.0 to "Отрасль кандидата: $candidateLabel (нужна: $needLabel)"
        }
    }

    private fun freshnessText(days: Long?): String = when {
        days == null -> "Дата последней активности неизвестна"
        days <= 30 -> "Профиль актуален: активность $days дн. назад"
        days <= 180 -> "Последняя активность $days дн. назад"
        else -> "Профиль давно не обновлялся: $days дн. назад"
    }

    private fun gradeFactor(assessed: String, required: String, fit: Double): Factor {
        val text = when {
            fit == MatchingPolicy.GRADE_FIT_EXACT -> "Грейд $assessed — точное совпадение с требуемым"
            gradeIndex(assessed) > gradeIndex(required) -> "Грейд $assessed выше требуемого $required: итог ×${fmt2(fit)}"
            else -> "Грейд $assessed ниже требуемого $required: итог ×${fmt2(fit)}"
        }
        val short = if (fit == MatchingPolicy.GRADE_FIT_EXACT) "грейд $assessed" else "грейд $assessed ×${fmt2(fit)}"
        return Factor("grade", "Грейд", 0.0, fit, 0.0, text, short)
    }

    private fun flags(c: CandidateSignals, need: NeedQuery?, pool: PreparedPool): List<String> = buildList {
        if (need != null && gradeIndex(c.assessedGrade) > gradeIndex(need.grade)) add("Грейд выше требуемого — обсудите уровень зарплаты")
        if (need != null && gradeIndex(c.assessedGrade) < gradeIndex(need.grade)) add("Грейд ниже требуемого — кандидат на вырост")
        if (c.achievementsHidden) add("Достижения скрыты кандидатом")
        else if (c.achievements.isEmpty()) add(if (c.fspLinked) "ФСП ID привязан, результатов нет" else "Нет истории ФСП")
        if (c.stackHidden) add("Стек скрыт кандидатом")
        val days = MatchingPolicy.freshness(c.lastActiveAt, now).second
        if (days != null && days > 180) add("Профиль давно не обновлялся")
        // Keyword-stuffing hint: many listed skills, few of them backed by the experience text. Info only.
        val listed = c.selfReportedSkills
        if (listed.size >= 6 && !c.textHidden) {
            val terms = pool.termSets.getValue(c.id)
            val backed =
                listed.count { s -> TextAnalysis.terms(s).let { t -> t.isNotEmpty() && t.all { it in terms } } }
            if (backed * 10 < listed.size * 3) add("Большая часть заявленного стека не подтверждена описанием опыта")
        }
    }

    private fun factor(code: String, label: String, weight: Double, value: Double, text: String, short: String) =
        Factor(code, label, weight, value.coerceIn(0.0, 1.0), weight * value.coerceIn(0.0, 1.0), text, short)

    companion object {
        fun resultLabel(result: String?): String? = when (result) {
            "winner" -> "победитель"
            "prize" -> "призёр"
            "finalist" -> "финалист"
            "participant" -> "участник"
            else -> null
        }

        fun levelLabel(level: String?): String? = when (level) {
            "international" -> "международный уровень"
            "federal" -> "всероссийский уровень"
            "regional" -> "региональный уровень"
            else -> null
        }

        fun describeAchievement(a: AchievementSignal): String {
            val head = resultLabel(a.result)?.let { r -> a.place?.let { "$r ($it место)" } ?: r }
            val event = a.eventName ?: a.title
            val year = a.eventDate?.year?.let { ", $it" }.orEmpty()
            return listOfNotNull(head, "«$event»$year").joinToString(" — ")
        }
    }
}
