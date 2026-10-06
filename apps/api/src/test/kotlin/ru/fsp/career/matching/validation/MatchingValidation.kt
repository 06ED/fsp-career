package ru.fsp.career.matching.validation

import ru.fsp.career.matching.CandidateFilter
import ru.fsp.career.matching.GRADES
import ru.fsp.career.matching.MatchingEngine
import ru.fsp.career.matching.MatchingPolicy
import ru.fsp.career.matching.MatchingWeights
import ru.fsp.career.matching.PreparedPool
import ru.fsp.career.matching.TextAnalysis
import java.util.Random

/** Per-need metric values of one ranking system. */
data class NeedMetrics(
    val p5: Double,
    val p10: Double,
    val ndcg10: Double,
    val mrr: Double,
    val stufferShare10: Double,
    val returned: Int,
    val relevantReturned: Int,
)

data class SystemRun(val key: String, val name: String, val description: String, val perNeed: Map<String, NeedMetrics>, val rankings: Map<String, List<String>>) {
    fun values(metric: (NeedMetrics) -> Double) = SyntheticMarket.needs.map { metric(perNeed.getValue(it.id)) }
}

/**
 * Offline evaluation harness: the same synthetic pool and the same needs for every system, so metric
 * differences come only from the ranking logic. Systems see observable signals only.
 */
class Evaluation(val market: SyntheticMarket) {
    val pool = PreparedPool(market.candidates.map(market::signals))
    val byId = market.candidates.associateBy { it.id }
    val labels: Map<String, Map<String, Int>> = SyntheticMarket.needs.associate { need ->
        need.id to market.candidates.associate { it.id to market.label(need.query, it) }
    }

    fun metrics(needId: String, ranked: List<String>): NeedMetrics {
        val needLabels = labels.getValue(needId)
        val rankedLabels = ranked.map { needLabels.getValue(it) }
        val top10 = ranked.take(10)
        return NeedMetrics(
            p5 = RankingMetrics.precisionAt(5, rankedLabels),
            p10 = RankingMetrics.precisionAt(10, rankedLabels),
            ndcg10 = RankingMetrics.ndcgAt(10, rankedLabels, needLabels.values),
            mrr = RankingMetrics.reciprocalRank(rankedLabels),
            stufferShare10 = if (top10.isEmpty()) 0.0 else top10.count { byId.getValue(it).stuffer } / top10.size.toDouble(),
            returned = ranked.size,
            relevantReturned = rankedLabels.count { it >= RankingMetrics.RELEVANT },
        )
    }

    fun run(key: String, name: String, description: String, ranker: (SynthNeed) -> List<String>): SystemRun {
        val rankings = SyntheticMarket.needs.associate { it.id to ranker(it) }
        return SystemRun(key, name, description, rankings.mapValues { (id, ranked) -> metrics(id, ranked) }, rankings)
    }

    /** Random order averaged over repetitions (expected value of an unranked list). */
    fun runRandom(key: String, name: String, description: String, repetitions: Int = 50, eligible: (SynthNeed) -> List<String>): SystemRun {
        val perNeed = SyntheticMarket.needs.associate { need ->
            val base = eligible(need).sorted()
            val runs = (0 until repetitions).map { rep ->
                metrics(need.id, base.shuffled(Random(1000L * need.id.hashCode() + rep)))
            }
            need.id to NeedMetrics(
                runs.map { it.p5 }.average(), runs.map { it.p10 }.average(), runs.map { it.ndcg10 }.average(),
                runs.map { it.mrr }.average(), runs.map { it.stufferShare10 }.average(), base.size,
                runs.first().relevantReturned,
            )
        }
        return SystemRun(key, name, description, perNeed, emptyMap())
    }

    fun engine(weights: MatchingWeights = MatchingWeights()): (SynthNeed) -> List<String> = { need ->
        MatchingEngine(weights, market.now).rankForNeed(need.query, pool).results.map { it.candidate.id }
    }

    /** «Как на обычных площадках»: self-declared grade filter + overlap of the listed stack and keywords. */
    fun keyword(need: SynthNeed): List<String> {
        val q = need.query
        val needStack = q.stack.map(TextAnalysis::skillKey).toSet()
        val keywords = (TextAnalysis.terms(q.description) + q.stack.flatMap(TextAnalysis::terms)).toSet()
        return market.candidates
            .filter { it.specialization == q.specialization && GRADES.indexOf(it.declaredGrade) >= GRADES.indexOf(q.grade) }
            .map { c ->
                val listed = c.selfStack.map(TextAnalysis::skillKey).toSet()
                val doc = (TextAnalysis.terms(c.experience) + c.selfStack.flatMap(TextAnalysis::terms)).toSet()
                val score = needStack.count { it in listed }.toDouble() / needStack.size.coerceAtLeast(1) +
                    0.5 * keywords.count { it in doc } / keywords.size.coerceAtLeast(1)
                c.id to score
            }
            .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
            .map { it.first }
    }

    /** Same category gate as the engine, ordered by the confirmed test score only. */
    fun testOnly(need: SynthNeed): List<String> = market.candidates
        .filter { it.specialization == need.query.specialization && MatchingPolicy.gradeFit(it.assessedGrade, need.query.grade, false) != null }
        .sortedWith(compareByDescending<SynthCandidate> { it.testScore }.thenBy { it.id })
        .map { it.id }

    /** Bank mode: the exact category (specialization + assessed grade) ordered by confirmed-profile strength. */
    fun bankStrength(need: SynthNeed): List<String> =
        MatchingEngine(MatchingWeights(), market.now)
            .browse(pool, CandidateFilter(specialization = need.query.specialization, grade = need.query.grade))
            .results.map { it.candidate.id }

    fun sameSpecialization(need: SynthNeed) = market.candidates.filter { it.specialization == need.query.specialization }.map { it.id }

    fun exactCategory(need: SynthNeed) = market.candidates
        .filter { it.specialization == need.query.specialization && it.assessedGrade == need.query.grade }.map { it.id }
}
