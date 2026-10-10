package ru.fsp.career.assessment

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.fsp.career.identity.infrastructure.Accounts
import ru.fsp.career.shared.domain.DomainError
import ru.fsp.career.shared.infrastructure.Database
import ru.fsp.career.assessment.domain.*
import ru.fsp.career.assessment.infrastructure.*
import ru.fsp.career.profiles.infrastructure.*
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the transactional assessment flow against a real PostgreSQL (skipped without TEST_DATABASE_URL). */
class AssessmentFlowDatabaseTest {
    private val survey = AssessmentSurvey("fintech", "backend", 4, listOf("individual"), "remote")

    private fun database(): Database {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        return Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career")
    }

    /**
     * Fixture account created with plain SQL (DB defaults for every profile column), so the test does not
     * depend on how the app binds roles to accounts.
     */
    private fun Transaction.newCandidate(): UUID {
        val id = UUID.randomUUID()
        exec("INSERT INTO accounts(id, subject, role) VALUES ('$id', 'assessment-flow-$id', 'candidate')")
        exec("INSERT INTO candidate_profiles(account_id) VALUES ('$id')")
        return id
    }

    /** Answers every item of the open attempt correctly (or wrongly) using the server-side key. */
    private suspend fun Database.finish(candidate: UUID, attemptId: UUID, correctly: Boolean): AssessmentResultView {
        while (true) {
            val (position, key, choice) = tx {
                val item = AssessmentItems.selectAll().where { (AssessmentItems.attemptId eq attemptId) and AssessmentItems.answeredAt.isNull() }.single()
                Triple(item[AssessmentItems.position], item[AssessmentItems.answerKey], item[AssessmentItems.view].format == "choice")
            }
            val answer = when {
                choice && correctly -> AnswerKey.optionIds[key.toInt()]
                choice -> AnswerKey.optionIds[(key.toInt() + 1) % 4]
                correctly -> key
                else -> formatNumber(key.toDouble() + 1000)
            }
            val outcome = tx { answerItem(candidate, attemptId, AssessmentAnswerRequest(position, answer), nowUtc()) }
            if (outcome.done) return outcome.result!!
            assertNull(outcome.result)
            assertEquals(position + 1, outcome.attempt!!.position)
        }
    }

    @Test fun surveyAdaptiveTestRetakeRulesAndErasure() {
        database().use { db ->
            runBlocking {
                val candidate = db.tx { newCandidate() }
                try {
                    assertFailsWith<DomainError> { planStart(StartAssessmentRequest(survey, "senior", vacancyId = "fintech-backend")) }
                    assertFailsWith<DomainError> { planStart(StartAssessmentRequest(survey, "senior", vacancyText = "Kotlin SQL backend")) }
                    val plan = planStart(StartAssessmentRequest(survey, "senior"))
                    assertFailsWith<DomainError> { db.tx { startAttempt(candidate, plan, nowUtc()) } }
                    db.tx { CandidateProfiles.update({ CandidateProfiles.accountId eq candidate }) { it[processingConsent] = true } }

                    val started = assertIs<StartOutcome.Started>(db.tx { startAttempt(candidate, plan, nowUtc()) }).attempt
                    assertEquals(0, started.position)
                    assertNotNull(started.item)
                    assertEquals(12, started.blueprint.sumOf { it.items })
                    assertNull(started.vacancyTitle)
                    db.tx {
                        val p = profile(candidate)
                        assertEquals("backend", p.specialization); assertEquals("fintech", p.industry); assertEquals("senior", p.desiredGrade)
                    }
                    assertIs<StartOutcome.Blocked>(db.tx { startAttempt(candidate, plan, nowUtc()) }, "one open attempt at a time")
                    val attemptId = UUID.fromString(started.attemptId)
                    assertFailsWith<DomainError> { db.tx { answerItem(candidate, attemptId, AssessmentAnswerRequest(5, "a"), nowUtc()) } }

                    val failed = db.finish(candidate, attemptId, correctly = false)
                    assertEquals(false, failed.passed)
                    assertNull(failed.grade, "a failed first attempt cannot confirm any grade")
                    db.tx {
                        assertNull(profile(candidate).grade)
                        assertNull(eligibility(candidate, "backend", nowUtc()).confirmedGrade)
                    }
                    assertTrue(failed.score < 20)
                    assertEquals(listOf("middle"), failed.nextOptions.filter { it.allowed }.map { it.grade })

                    val blocked = assertIs<StartOutcome.Blocked>(db.tx { startAttempt(candidate, plan, nowUtc()) })
                    assertEquals("cooldown", blocked.conflict.reason)
                    assertNotNull(blocked.conflict.retakeAvailableAt)

                    val lower = assertIs<StartOutcome.Started>(db.tx {
                        startAttempt(candidate, planStart(StartAssessmentRequest(survey, "middle")), nowUtc())
                    }).attempt
                    val passed = db.finish(candidate, UUID.fromString(lower.attemptId), correctly = true)
                    assertEquals(true, passed.passed)
                    assertEquals(true, passed.confident)
                    assertEquals("middle", passed.grade)
                    assertNull(passed.previousGrade)
                    // senior was failed in the window, so the confident pass does not reopen it immediately
                    assertTrue(passed.nextOptions.none { it.allowed })

                    db.tx {
                        assertEquals("middle", profile(candidate).grade)
                        val history = history(candidate)
                        assertEquals(listOf("middle", "senior"), history.map { it.targetGrade })
                        assertEquals(12, history.first().answered)
                        assertEquals(survey, history.first().survey)
                        // Confirmation uses cumulative topic evidence, including the earlier failed attempt.
                        val answers = (AssessmentItems innerJoin AssessmentAttempts).selectAll()
                            .where { AssessmentAttempts.candidateId eq candidate }.map {
                                ItemBank.family(it[AssessmentItems.familyId])!!.topic to it[AssessmentItems.correct]!!
                            }
                        val expectedSkills = ConfirmedSkills.skills(ConfirmedSkills.topics(answers))
                        assertEquals(expectedSkills, confirmedSkills(listOf(candidate))[candidate].orEmpty())
                        val eligibility = eligibility(candidate, "backend", nowUtc())
                        assertEquals("middle", eligibility.confirmedGrade)
                        assertTrue(eligibility.options.none { it.allowed })
                    }

                    // An attempt in another specialization that runs out of time is closed as a failure.
                    val dataPlan = planStart(StartAssessmentRequest(survey.copy(specialization = "data"), "middle"))
                    val open = assertIs<StartOutcome.Started>(db.tx { startAttempt(candidate, dataPlan, nowUtc()) }).attempt
                    db.tx { AssessmentAttempts.update({ AssessmentAttempts.id eq UUID.fromString(open.attemptId) }) { it[expiresAt] = nowUtc().minusMinutes(1) } }
                    val closed = db.tx { closeExpired(candidate, nowUtc()) }
                    assertNotNull(closed)
                    assertEquals(true, closed.expired)
                    assertEquals(false, closed.passed)
                    assertNull(closed.grade)
                    assertTrue(closed.score < 15, "abandoned attempt scores as all-wrong: ${closed.score}")
                    db.tx { assertNull(currentAttempt(candidate)) }
                } finally {
                    db.tx {
                        AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq candidate }
                        Accounts.deleteWhere { Accounts.id eq candidate }
                    }
                }
            }
        }
    }
}
