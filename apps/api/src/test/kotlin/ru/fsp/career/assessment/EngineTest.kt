package ru.fsp.career.assessment

import ru.fsp.career.assessment.domain.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IrtTest {
    @Test fun probabilityAndInformationBehave() {
        val item = ItemParams(1.5, 0.3)
        assertEquals(0.5, Irt.probability(item, 0.3), 1e-12)
        assertTrue(Irt.probability(item, 1.0) > Irt.probability(item, 0.0))
        val guess = ItemParams(1.5, 0.3, 0.25)
        assertTrue(Irt.probability(guess, -10.0) > 0.249)
        val peak = Irt.grid.maxBy { Irt.information(item, it) }
        assertTrue(abs(peak - 0.3) < 0.051, "2PL information peaks at b")
        assertTrue(Irt.information(item, 0.3) > Irt.information(guess, 0.3), "guessing lowers information")
    }

    @Test fun normalCdfMatchesKnownValues() {
        assertEquals(0.5, Irt.normalCdf(0.0), 1e-7)
        assertEquals(0.975, Irt.normalCdf(1.959964), 1e-6)
        assertEquals(0.0228, Irt.normalCdf(-2.0), 1e-4)
    }

    @Test fun eapRecoversAbilityAndShrinksUncertainty() {
        val rng = Random(42)
        val trueTheta = 1.0
        val items = List(200) { ItemParams(1.4, rng.nextDouble(-2.0, 2.0)) }
        val grid = LikelihoodGrid()
        var previousSe = Double.MAX_VALUE
        items.forEachIndexed { i, item ->
            grid.add(item, rng.nextDouble() < Irt.probability(item, trueTheta))
            if (i % 50 == 49) {
                val estimate = grid.posterior()
                assertTrue(estimate.se < previousSe)
                previousSe = estimate.se
            }
        }
        val estimate = grid.posterior()
        assertTrue(abs(estimate.theta - trueTheta) < 0.35, "EAP ${estimate.theta}")
        assertTrue(estimate.se < 0.25)
    }

    @Test fun extremePatternsStayFinite() {
        val allRight = LikelihoodGrid.of(List(12) { ItemParams(1.5, 0.0) to true }).posterior()
        val allWrong = LikelihoodGrid.of(List(12) { ItemParams(1.5, 0.0) to false }).posterior()
        assertTrue(allRight.theta > 1.0 && allRight.theta < 4.0)
        assertTrue(allWrong.theta < -1.0 && allWrong.theta > -4.0)
    }

    @Test fun declarationDoesNotChangeTheFinalScore() {
        val responses = List(12) { i -> ItemParams(1.3, -1.0 + i * 0.2) to (i % 3 != 0) }
        val grid = LikelihoodGrid.of(responses)
        assertEquals(Cat.finalEstimate(grid), Cat.finalEstimate(LikelihoodGrid.of(responses)))
        assertTrue(Cat.routingEstimate(Grade.SENIOR, grid).theta > Cat.routingEstimate(Grade.JUNIOR, grid).theta)
    }

    @Test fun decisionRuleAndGradeFloor() {
        val strong = Grading.decide(Grade.MIDDLE, Estimate(1.5, 0.3))
        assertTrue(strong.passed && strong.confident)
        assertTrue(Grading.decide(Grade.MIDDLE, Estimate(-0.45, 0.4), k = 0.0).passed, "k = 0: θ̂ on the target side of the cut passes")
        assertFalse(Grading.decide(Grade.MIDDLE, Estimate(-0.45, 0.4), k = 0.25).passed, "θ̂ − k·SE below the cut must not pass")
        assertFalse(Grading.decide(Grade.MIDDLE, Estimate(-0.55, 0.1)).passed)
        val upgrade = Grading.decide(Grade.MIDDLE, Estimate(0.85, 0.4), k = 0.0, kUpgrade = 0.0)
        assertTrue(upgrade.passed && upgrade.confident, "θ̂ above the next cut unlocks the upgrade attempt")
        assertFalse(Grading.decide(Grade.SENIOR, Estimate(3.0, 0.2)).confident, "no grade above senior")
        assertNull(Grading.confirmedAfter(null, Grade.SENIOR, passed = false))
        assertEquals(Grade.MIDDLE, Grading.confirmedAfter(Grade.MIDDLE, Grade.SENIOR, passed = false), "never lowered")
        assertEquals(Grade.SENIOR, Grading.confirmedAfter(Grade.MIDDLE, Grade.SENIOR, passed = true))
        assertEquals(Grade.SENIOR, Grading.confirmedAfter(Grade.SENIOR, Grade.MIDDLE, passed = true))
        assertEquals(50, Grading.score(0.0))
        assertEquals(Grade.MIDDLE, Grade.reference(0.0))
        assertEquals(Grade.SENIOR, Grade.reference(0.8))
        assertEquals(Grade.JUNIOR, Grade.reference(-0.51))
    }
}

class RetakePolicyTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")
    private fun daysAgo(days: Long) = now.minus(days, ChronoUnit.DAYS)
    private fun allowed(history: List<PastAttempt>) = RetakePolicy.options(history, now).filter { it.allowed }.map { it.grade }

    @Test fun firstAttemptAllowsAnyGrade() {
        assertEquals(Grade.entries, allowed(emptyList()))
    }

    @Test fun failureAllowsImmediateLowerOnceThenCooldown() {
        val failedSenior = PastAttempt(Grade.SENIOR, passed = false, confident = false, confirmed = Grade.JUNIOR, submittedAt = now.minusSeconds(3600))
        val options = RetakePolicy.options(listOf(failedSenior), now)
        assertEquals(listOf(Grade.MIDDLE), options.filter { it.allowed }.map { it.grade })
        assertEquals(RetakeReason.LOWER_AFTER_FAIL, options.first { it.grade == Grade.MIDDLE }.reason)
        val blocked = options.first { it.grade == Grade.SENIOR }
        assertEquals(RetakeReason.COOLDOWN, blocked.reason)
        assertEquals(failedSenior.submittedAt.plus(90, ChronoUnit.DAYS), blocked.availableAt)
        // The lower attempt passed (not confidently): nothing else until the 90 days run out.
        val passedMiddle = PastAttempt(Grade.MIDDLE, passed = true, confident = false, confirmed = Grade.MIDDLE, submittedAt = now)
        assertEquals(emptyList(), allowed(listOf(failedSenior, passedMiddle)))
    }

    @Test fun confidentPassAllowsImmediateUpgradeUnlessThatLevelFailedRecently() {
        val confidentMiddle = PastAttempt(Grade.MIDDLE, passed = true, confident = true, confirmed = Grade.MIDDLE, submittedAt = daysAgo(1))
        assertEquals(listOf(Grade.SENIOR), allowed(listOf(confidentMiddle)))
        val failedSenior = PastAttempt(Grade.SENIOR, passed = false, confident = false, confirmed = Grade.JUNIOR, submittedAt = daysAgo(2))
        assertEquals(emptyList(), allowed(listOf(failedSenior, confidentMiddle)), "no senior → middle → senior loop")
    }

    @Test fun cooldownExpiresAfterNinetyDaysAndConfirmedGradeIsAFloor() {
        val middle = PastAttempt(Grade.MIDDLE, passed = true, confident = false, confirmed = Grade.MIDDLE, submittedAt = daysAgo(90))
        val options = RetakePolicy.options(listOf(middle), now)
        assertEquals(listOf(Grade.MIDDLE, Grade.SENIOR), options.filter { it.allowed }.map { it.grade })
        assertEquals(RetakeReason.BELOW_CONFIRMED, options.first { it.grade == Grade.JUNIOR }.reason)
        assertNull(options.first { it.grade == Grade.JUNIOR }.availableAt)
        val almost = middle.copy(submittedAt = daysAgo(89))
        assertEquals(emptyList(), allowed(listOf(almost)))
    }

    @Test fun juniorFailureHasNoLowerLevelAndLegacyAttemptsOnlyCoolDown() {
        val failedJunior = PastAttempt(Grade.JUNIOR, passed = false, confident = false, confirmed = Grade.JUNIOR, submittedAt = daysAgo(3))
        assertEquals(emptyList(), allowed(listOf(failedJunior)))
        val legacy = PastAttempt(null, passed = null, confident = null, confirmed = Grade.SENIOR, submittedAt = daysAgo(10))
        assertEquals(emptyList(), allowed(listOf(legacy)))
    }
}

class CatTest {
    private val pool = ItemBank.pool("backend")

    @Test fun respectsQuotasNeverRepeatsAndFillsTheTest() {
        val blueprint = Blueprints.blueprintFor("backend", null)
        val rng = Random(1)
        val administered = mutableListOf<ItemFamily>()
        val grid = LikelihoodGrid()
        repeat(Cat.TEST_LENGTH) {
            val theta = Cat.routingEstimate(Grade.MIDDLE, grid).theta
            val next = Cat.nextFamily(pool, blueprint.quotas, administered, theta, { 0L }, rng)!!
            administered += next
            grid.add(next.params, rng.nextBoolean())
        }
        assertEquals(Cat.TEST_LENGTH, administered.map { it.id }.toSet().size)
        val perTopic = administered.groupingBy { it.topic }.eachCount()
        for ((topic, quota) in blueprint.quotas) assertEquals(quota, perTopic[topic] ?: 0, topic)
    }

    @Test fun picksInformativeItemsAndRotatesExposure() {
        val quotas = pool.groupingBy { it.topic }.eachCount()
        val high = Cat.nextFamily(pool, quotas, emptyList(), 1.8, { 0L }, Random(3))!!
        val low = Cat.nextFamily(pool, quotas, emptyList(), -1.5, { 0L }, Random(3))!!
        assertTrue(high.b > low.b, "harder item for a stronger candidate")
        val first = Cat.nextFamily(pool, quotas, emptyList(), 0.0, { 0L }, Random(5))!!
        val second = Cat.nextFamily(pool, quotas, emptyList(), 0.0, { if (it == first.id) 1000L else 0L }, Random(5))!!
        val similar = pool.filter { Irt.information(it.params, 0.0) >= Cat.SIMILAR_INFORMATION * pool.maxOf { f -> Irt.information(f.params, 0.0) } }
        if (similar.size > 1) assertTrue(first.id != second.id, "an overexposed family yields to an equally informative one")
    }
}

class BlueprintTest {
    @Test fun demoVacanciesAreRecognised() {
        val expected = mapOf(
            "fintech-backend" to listOf("kotlin", "postgresql", "transactions", "messaging", "reliability"),
            "marketplace-frontend" to listOf("react", "typescript", "css", "web-performance", "accessibility"),
            "edtech-analyst" to listOf("sql", "pandas", "experiments", "product-analytics", "ml"),
            "subscription-pm" to listOf("unit-economics", "prioritization", "experiments", "research"),
            "bank-designer" to listOf("figma", "usability", "accessibility", "layout", "typography"),
        )
        for (vacancy in DemoVacancies.all) {
            assertEquals(vacancy.specialization, Blueprints.detectSpecialization(vacancy.text), vacancy.id)
            val blueprint = Blueprints.blueprintFor(vacancy.specialization, vacancy.text)
            val skills = blueprint.skills.map { it.skill.id }
            for (skill in expected.getValue(vacancy.id)) assertTrue(skill in skills, "${vacancy.id}: $skill not detected in $skills")
            assertEquals(Cat.TEST_LENGTH, blueprint.quotas.values.sum(), vacancy.id)
            val caps = ItemBank.pool(vacancy.specialization).groupingBy { it.topic }.eachCount()
            assertTrue(blueprint.quotas.all { (topic, n) -> n <= caps.getValue(topic) }, vacancy.id)
        }
    }

    @Test fun vacancyShiftsWeightTowardsMentionedTopics() {
        val generic = Blueprints.blueprintFor("backend", null)
        val text = "Нужен разработчик: транзакции, уровни изоляции, индексы PostgreSQL, блокировки в транзакциях, explain"
        val focused = Blueprints.blueprintFor("backend", text)
        assertTrue((focused.topicWeights["databases"] ?: 0.0) > (generic.topicWeights["databases"] ?: 0.0))
        assertTrue((focused.quotas["databases"] ?: 0) >= (generic.quotas["databases"] ?: 0))
        assertEquals(12, focused.quotas.values.sum())
    }

    @Test fun stemmingAndTokenisation() {
        assertEquals("транзакц", Blueprints.stem("транзакциями").take(8))
        assertEquals("индекс", Blueprints.stem("индексы"))
        assertTrue("abtest" in Blueprints.tokens("Проводить A/B-тесты и считать p-value"))
        assertTrue(Blueprints.detect("Опыт с машинным обучением и SQL").map { it.skill.id }.containsAll(listOf("ml", "sql")))
        assertTrue(Blueprints.detect("Пишем на TypeScript").any { it.skill.id == "typescript" })
        assertFalse(Blueprints.detect("Статистика продаж кассы").any { it.skill.id == "typescript" }, "short terms need whole-token match")
    }

    @Test fun samplesComeFromTheBlueprintsTopTopics() {
        val vacancy = DemoVacancies.find("fintech-backend")!!
        val blueprint = Blueprints.blueprintFor("backend", vacancy.text)
        val samples = Blueprints.samples(blueprint, Grade.MIDDLE, 4, Random(9))
        assertEquals(4, samples.size)
        assertTrue(samples.all { (family, _) -> family.topic in blueprint.quotas.keys.take(4) })
    }
}

class ConfirmedSkillsTest {
    @Test fun oneLuckyAnswerIsNotEnough() {
        val answers = listOf("sql" to true, "sql" to true, "sql" to false, "kotlin" to true, "react" to true, "react" to false, "react" to false)
        assertEquals(setOf("sql"), ConfirmedSkills.topics(answers))
        assertEquals(setOf("sql"), ConfirmedSkills.skills(setOf("sql")))
        assertTrue(Topics.labels.keys.all { it in ConfirmedSkills.canonical }, "every topic maps to a skill name")
    }
}
