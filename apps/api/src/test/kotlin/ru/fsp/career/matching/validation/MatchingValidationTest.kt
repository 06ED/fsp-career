package ru.fsp.career.matching.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchingValidationTest {
    @Test fun metricsFollowTheirDefinitions() {
        assertEquals(0.6, RankingMetrics.precisionAt(5, listOf(3, 0, 2, 2, 1)))
        assertEquals(1.0, RankingMetrics.ndcgAt(10, listOf(3, 2, 1, 0), listOf(0, 1, 2, 3)), 1e-9)
        assertTrue(RankingMetrics.ndcgAt(10, listOf(0, 1, 2, 3), listOf(0, 1, 2, 3)) < 1.0)
        assertEquals(1.0 / 3, RankingMetrics.reciprocalRank(listOf(0, 1, 2)), 1e-9)
        assertEquals(0.0, RankingMetrics.reciprocalRank(listOf(0, 1)))
        val interval = RankingMetrics.bootstrap(listOf(0.2, 0.4, 0.6, 0.8))
        assertTrue(interval.low <= interval.mean && interval.mean <= interval.high)
    }

    @Test fun generatorIsDeterministic() {
        val a = SyntheticMarket(seed = 42, candidatesPerSpecialization = 10).candidates
        val b = SyntheticMarket(seed = 42, candidatesPerSpecialization = 10).candidates
        assertEquals(a, b)
        assertEquals(50, a.size)
        assertTrue(a.any { it.fsp.isEmpty() } && a.any { it.fsp.isNotEmpty() })
        assertTrue(a.any { it.stuffer })
    }

    @Test fun engineBeatsBaselinesOnASmallHeldOutMarket() {
        val eval = Evaluation(SyntheticMarket(seed = 7, candidatesPerSpecialization = 40))
        val engine = eval.run("engine", "", "", eval.engine()).values { it.ndcg10 }.average()
        val keyword = eval.run("keyword", "", "") { eval.keyword(it) }.values { it.ndcg10 }.average()
        val random = eval.runRandom("random", "", "", repetitions = 10) { eval.sameSpecialization(it) }.values { it.ndcg10 }.average()
        assertTrue(engine > keyword + 0.1, "engine $engine vs keyword $keyword")
        assertTrue(engine > random + 0.2, "engine $engine vs random $random")
    }
}
