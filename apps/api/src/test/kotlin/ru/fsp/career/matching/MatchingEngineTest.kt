package ru.fsp.career.matching

import ru.fsp.career.matching.domain.*

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchingEngineTest {
    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val engine = MatchingEngine(MatchingWeights(), now)
    private val need = NeedQuery(
        specialization = "backend",
        grade = "middle",
        industry = "финтех",
        stack = listOf("Kotlin", "PostgreSQL", "Kafka"),
        description = "Платёжный сервис: микросервисы на Kotlin, транзакции в PostgreSQL, события в Kafka, антифрод.",
    )
    private val paymentsText = "Писал микросервисы на Kotlin для платёжного шлюза, хранил транзакции в PostgreSQL, события через Kafka."

    private fun candidate(
        id: String,
        specialization: String = "backend",
        grade: String? = "middle",
        score: Int? = 70,
        stack: List<String> = listOf("Kotlin", "PostgreSQL", "Kafka"),
        text: String = paymentsText,
        achievements: List<AchievementSignal> = emptyList(),
        industry: String = "финтех",
        fspLinked: Boolean = achievements.isNotEmpty(),
        daysAgo: Long = 10,
        stackHidden: Boolean = false,
        achievementsHidden: Boolean = false,
    ) = CandidateSignals(
        id = id, displayName = id, specialization = specialization, industry = industry, assessedGrade = grade,
        testScore = score, assessedAt = now.minus(daysAgo, ChronoUnit.DAYS), selfReportedSkills = stack,
        profileText = text, achievements = achievements, fspLinked = fspLinked,
        lastActiveAt = now.minus(daysAgo, ChronoUnit.DAYS), stackHidden = stackHidden, achievementsHidden = achievementsHidden,
    )

    private val federalPrize = AchievementSignal(
        title = "Призёр", issuer = "ФСП", points = 9, source = "fsp", eventName = "Чемпионат России (демо)",
        eventDate = LocalDate.parse("2026-03-01"), level = "federal", place = 2, result = "prize",
    )

    private fun rank(vararg pool: CandidateSignals, filter: CandidateFilter = CandidateFilter(), query: NeedQuery = need) =
        engine.rankForNeed(query, PreparedPool(pool.toList()), filter).results

    @Test fun categoryGateUsesAssessedSpecializationAndGrade() {
        val results = rank(
            candidate("ok"),
            candidate("frontend", specialization = "frontend"),
            candidate("junior", grade = "junior"),
            candidate("unassessed", grade = null, score = null),
        )
        assertEquals(listOf("ok"), results.map { it.candidate.id })
        val withLower = rank(candidate("ok"), candidate("junior", grade = "junior"), filter = CandidateFilter(allowLowerGrade = true))
        assertEquals(listOf("ok", "junior"), withLower.map { it.candidate.id })
        assertTrue(withLower.last().flags.any { "ниже требуемого" in it })
        assertEquals(0.60, withLower.last().gradeFit)
    }

    @Test fun exactGradeBeatsOverqualifiedWithSameSignals() {
        val results = rank(candidate("senior", grade = "senior"), candidate("middle"))
        assertEquals(listOf("middle", "senior"), results.map { it.candidate.id })
        assertEquals(0.85, results.last().gradeFit)
        assertTrue(results.last().flags.any { "выше требуемого" in it })
        assertTrue(results.last().factors.first { it.code == "grade" }.text.contains("×0,85"))
    }

    @Test fun confirmedFspAchievementsRaiseCandidateAndAreExplained() {
        val results = rank(candidate("plain"), candidate("fsp", achievements = listOf(federalPrize)))
        assertEquals(listOf("fsp", "plain"), results.map { it.candidate.id })
        val fsp = results.first().factors.first { it.code == "fsp" }
        assertTrue(fsp.points > 0 && "призёр (2 место)" in fsp.text, fsp.text)
        val none = results.last().factors.first { it.code == "fsp" }
        assertEquals(0.0, none.points)
        assertTrue("История ФСП не привязана" in none.text && "не штраф" in none.text)
        assertTrue("нет истории ФСП" in results.last().summary)
        assertTrue(results.last().flags.contains("Нет истории ФСП"))
    }

    @Test fun noFspHistoryIsNotPenalisedBeyondTheFactor() {
        // Same candidate with and without FSP: the difference equals the FSP factor points, nothing else.
        val with = rank(candidate("a", achievements = listOf(federalPrize))).single()
        val without = rank(candidate("a")).single()
        val fspPoints = with.factors.first { it.code == "fsp" }.points
        assertEquals(with.relevance - fspPoints, without.relevance, 1e-9)
    }

    @Test fun linkedWithoutResultsAndHiddenAchievementsHaveOwnTexts() {
        val linked = rank(candidate("linked", fspLinked = true)).single()
        assertTrue("ФСП ID привязан, но результатов в реестре нет" in linked.factors.first { it.code == "fsp" }.text)
        val hidden = rank(candidate("hidden", achievementsHidden = true)).single()
        assertTrue("скрыты" in hidden.factors.first { it.code == "fsp" }.text)
        assertTrue(hidden.flags.contains("Достижения скрыты кандидатом"))
    }

    @Test fun describedStackOutweighsBareKeywordTags() {
        val honest = candidate("honest")
        val stuffer = candidate(
            "stuffer",
            stack = listOf("Kotlin", "PostgreSQL", "Kafka", "Go", "Rust", "Scala", "Kubernetes", "Elixir"),
            text = "Делал внутренние инструменты.",
        )
        val results = rank(honest, stuffer)
        val honestStack = results.first { it.candidate.id == "honest" }.factors.first { it.code == "stack" }
        val stufferStack = results.first { it.candidate.id == "stuffer" }.factors.first { it.code == "stack" }
        assertTrue(honestStack.value > 2 * stufferStack.value, "${honestStack.value} vs ${stufferStack.value}")
        assertTrue("Стек: 3 из 3" in honestStack.text && "Подтверждено описанием опыта" in honestStack.text)
        assertTrue("Только заявлено" in stufferStack.text, stufferStack.text)
        assertTrue(results.first { it.candidate.id == "stuffer" }.flags.any { "не подтверждена описанием" in it })
        assertEquals("honest", results.first().candidate.id)
    }

    @Test fun textRelevanceExplainsSharedTerms() {
        val results = rank(candidate("payments"), candidate("design", text = "Рисовал иллюстрации и логотипы для журналов."))
        assertEquals("payments", results.first().candidate.id)
        val text = results.first().factors.first { it.code == "text" }
        assertTrue(text.value > results.last().factors.first { it.code == "text" }.value)
        assertTrue("Kotlin" in text.text || "PostgreSQL" in text.text || "Kafka" in text.text, text.text)
    }

    @Test fun inapplicableFactorsAreDroppedAndWeightsRescaledTo100() {
        val bare = NeedQuery("backend", "middle", description = "Нужен разработчик сервисов")
        val result = rank(candidate("a"), query = bare).single()
        val codes = result.factors.map { it.code }
        assertFalse("stack" in codes || "industry" in codes)
        assertEquals(100.0, result.factors.filter { it.code != "grade" }.sumOf { it.weight }, 1e-9)
        val full = rank(candidate("a")).single()
        assertEquals(100.0, full.factors.filter { it.code != "grade" }.sumOf { it.weight }, 1e-9)
        assertEquals(30.0, full.factors.first { it.code == "test" }.weight, 1e-9)
    }

    @Test fun hiddenStackNeitherScoresNorPassesStackFilter() {
        val hidden = candidate("hidden", stack = emptyList(), stackHidden = true, text = "Делал внутренние инструменты.")
        val result = rank(hidden).single()
        val stack = result.factors.first { it.code == "stack" }
        assertEquals(0.0, stack.value)
        assertTrue("скрыт" in stack.text)
        assertTrue(rank(hidden, filter = CandidateFilter(stack = listOf("Kotlin"))).isEmpty())
    }

    @Test fun hardFiltersNarrowTheSelection() {
        val pool = arrayOf(
            candidate("fsp", achievements = listOf(federalPrize)),
            candidate("retail", industry = "ритейл"),
            candidate("python", stack = listOf("Python"), text = "Писал скрипты."),
        )
        assertEquals(listOf("fsp"), rank(*pool, filter = CandidateFilter(fspOnly = true)).map { it.candidate.id })
        assertEquals(setOf("fsp", "retail"), rank(*pool, filter = CandidateFilter(stack = listOf("postgres", "KOTLIN"))).map { it.candidate.id }.toSet())
        assertEquals(listOf("retail"), rank(*pool, filter = CandidateFilter(industry = "Ритейл")).map { it.candidate.id })
    }

    @Test fun tiesAreBrokenDeterministicallyById() {
        assertEquals(listOf("a", "b", "c"), rank(candidate("c"), candidate("a"), candidate("b")).map { it.candidate.id })
    }

    @Test fun bankModeOrdersCategoryByConfirmedStrength() {
        val pool = PreparedPool(listOf(
            candidate("strong", score = 90),
            candidate("fsp", score = 80, achievements = listOf(federalPrize, federalPrize.copy(title = "Ещё"))),
            candidate("weak", score = 60, stack = listOf("Kotlin", "PostgreSQL", "Kafka", "Go", "Docker")),
            candidate("senior", grade = "senior", score = 99),
            candidate("front", specialization = "frontend", score = 95),
        ))
        val ranking = engine.browse(pool, CandidateFilter(specialization = "backend", grade = "middle"))
        assertEquals(listOf("fsp", "strong", "weak"), ranking.results.map { it.candidate.id })
        assertEquals(listOf("test", "fsp", "freshness"), ranking.results.first().factors.map { it.code })
        val categories = ranking.categories.associateBy { it.specialization + ":" + it.grade }
        assertEquals(3, categories.getValue("backend:middle").candidates)
        assertEquals(1, categories.getValue("backend:middle").withFsp)
        assertEquals(1, categories.getValue("frontend:middle").candidates)
        // Free-text query filters the bank by narrative + listed tags, order stays by strength.
        val query = engine.browse(pool, CandidateFilter(query = "Go"))
        assertEquals(listOf("weak"), query.results.map { it.candidate.id })
        // A stop-word-only query is ignored rather than filtering everyone out.
        assertEquals(5, engine.browse(pool, CandidateFilter(query = "и в на")).results.size)
    }

    @Test fun needCategoriesRecommendExactAndHigherGrades() {
        val pool = PreparedPool(listOf(
            candidate("m1"), candidate("m2"), candidate("s1", grade = "senior"), candidate("j1", grade = "junior"),
            candidate("data", specialization = "data", text = "Строил витрины данных и дашборды."),
        ))
        val categories = engine.rankForNeed(need, pool).categories
        val byFit = categories.associateBy { it.fit }
        assertEquals(2, byFit.getValue("exact").candidates)
        assertTrue(byFit.getValue("exact").recommended && byFit.getValue("higher").recommended)
        assertFalse(byFit.getValue("lower").recommended)
        assertEquals("exact", categories.first().fit)
    }

    @Test fun explainReturnsBreakdownForCard() {
        val pool = PreparedPool(listOf(candidate("a"), candidate("b", grade = "junior")))
        assertNotNull(engine.explain(need, pool, "a"))
        // A lower grade is still explained on the card (with the ×0.60 multiplier), other specs are not.
        assertEquals(0.60, engine.explain(need, pool, "b")?.gradeFit)
        assertNull(engine.explain(need.copy(specialization = "frontend"), pool, "a"))
    }

    @Test fun recencyAndFreshnessPolicy() {
        assertEquals(1.0, MatchingPolicy.recency(LocalDate.parse("2025-10-01"), now))
        assertEquals(0.6, MatchingPolicy.recency(LocalDate.parse("2022-10-01"), now), 0.01)
        assertEquals(0.4, MatchingPolicy.recency(LocalDate.parse("2014-01-01"), now))
        assertEquals(1.0, MatchingPolicy.freshness(now.minus(10, ChronoUnit.DAYS), now).first)
        assertEquals(0.3, MatchingPolicy.freshness(now.minus(500, ChronoUnit.DAYS), now).first)
        assertEquals(0.3, MatchingPolicy.freshness(null, now).first)
    }
}
