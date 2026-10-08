package ru.fsp.career

import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.assessment.infrastructure.*
import ru.fsp.career.achievements.infrastructure.*
import ru.fsp.career.matching.infrastructure.*
import ru.fsp.career.recruitment.infrastructure.*
import ru.fsp.career.shared.infrastructure.Database

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.fsp.career.achievements.domain.FspOwnership
import ru.fsp.career.matching.domain.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** V6 schema + mock FSP registry + privacy + invitation contact rules against a real PostgreSQL (skipped without one). */
class MatchingIntegrationTest {
    @Test fun fspLinkPrivacyAndInvitationContacts() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            runBlocking {
                db.tx {
                    assertEquals(40, FspRegistryParticipants.selectAll().count())
                    assertEquals(80, FspRegistryResults.selectAll().count())
                }
                val registry = MockFspRegistry(db)
                val record = assertNotNull(registry.lookup("FSP-100001"))
                assertEquals(4, record.results.size)
                assertTrue(FspOwnership.verify(record, "Ivan.Petrov@demo.fsp.local"))
                assertNull(registry.lookup("FSP-999999"))
                assertTrue(assertNotNull(registry.lookup("FSP-100003")).results.isEmpty())

                val candidate = db.tx { createAccount("matching-it-${UUID.randomUUID()}", "candidate") }
                val employer = db.tx { createAccount("matching-it-${UUID.randomUUID()}", "employer") }
                try {
                    db.tx {
                        CandidateProfiles.update({ CandidateProfiles.accountId eq candidate }) {
                            it[fullName] = "Иван Петров (тест)"
                            it[email] = "ivan.petrov@demo.fsp.local"
                            it[phone] = "+70000000000"
                            it[processingConsent] = true
                            it[consentPublication] = true
                            it[specialization] = "backend"
                            it[techStack] = listOf("Kotlin", "PostgreSQL")
                            it[experience] = "Писал платёжные микросервисы на Kotlin и PostgreSQL."
                        }
                        AssessmentAttempts.insert {
                            it[id] = UUID.randomUUID()
                            it[candidateId] = candidate
                            it[specialization] = "backend"
                            it[questionIds] = listOf(1L, 2L, 3L, 4L, 5L)
                            it[submittedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                            it[score] = 70
                            it[grade] = "middle"
                        }
                        // Unlinked: the candidate is still discoverable, with an explicit «no FSP history».
                        val before = publishedCandidates().single { it.accountId == candidate }
                        assertTrue(before.achievements.isEmpty() && !before.signals.fspLinked)

                        importFsp(candidate, record, registry)
                        val linkStatus = fspStatus(candidate, registry)
                        assertTrue(linkStatus.linked)
                        assertEquals("FSP-100001", linkStatus.fspId)
                        assertEquals(4, linkStatus.achievements.size)
                        assertEquals(9 + 11 + 10 + 4, linkStatus.totalPoints)
                        // Re-import is idempotent (replaces, never duplicates).
                        importFsp(candidate, record, registry)
                        assertEquals(4, fspStatus(candidate, registry).achievements.size)

                        val member = publishedCandidates().single { it.accountId == candidate }
                        assertEquals(4, member.signals.achievements.size)
                        val ranked = MatchingEngine().rankForNeed(
                            NeedQuery("backend", "middle", "", listOf("Kotlin"), "Платёжные сервисы на Kotlin"),
                            PreparedPool(listOf(member.signals)),
                        ).results.single()
                        assertTrue(ranked.factors.first { it.code == "fsp" }.points > 0)

                        // Privacy: hidden achievements and name neither show nor score.
                        CandidateProfiles.update({ CandidateProfiles.accountId eq candidate }) {
                            it[privacy] = CandidatePrivacy(fullName = false, fspAchievements = false)
                        }
                        val hidden = publishedCandidates().single { it.accountId == candidate }
                        assertTrue(hidden.achievements.isEmpty() && hidden.signals.achievementsHidden)
                        assertTrue(hidden.signals.displayName.startsWith("Кандидат №"))

                        // Invitation: company contact line is the default contact method; candidate contacts stay closed.
                        EmployerCompanies.update({ EmployerCompanies.accountId eq employer }) {
                            it[name] = "Тестовая компания"
                            it[contactTelegram] = "@test_hr"
                        }
                        val invitationId = UUID.randomUUID()
                        InvitationsTable.insert {
                            it[id] = invitationId
                            it[employerId] = employer
                            it[candidateId] = candidate
                            it[description] = "Оффер"
                            it[salaryMinRub] = 100000
                            it[salaryMaxRub] = 150000
                            it[status] = "sent"
                            it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                        }
                        val sent = invitationsFor("employer", employer).single()
                        assertEquals("Telegram: @test_hr", sent.contactMethod)
                        assertNull(sent.contactEmail)
                        assertTrue(sent.candidateName.startsWith("Кандидат №"))
                        assertEquals("sent", latestInvitationStatus(employer)[candidate])
                        assertFalse(candidate in disclosedCandidates(employer, listOf(candidate)))

                        InvitationsTable.update({ InvitationsTable.id eq invitationId }) { it[status] = "accepted" }
                        val accepted = invitationsFor("employer", employer).single()
                        assertEquals("ivan.petrov@demo.fsp.local", accepted.contactEmail)
                        assertEquals("Иван Петров (тест)", accepted.candidateName)
                        assertEquals("Telegram: @test_hr", invitationsFor("candidate", candidate).single().contactMethod)
                    }
                } finally {
                    db.tx {
                        InvitationsTable.deleteWhere { InvitationsTable.employerId eq employer }
                        AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq candidate }
                        Accounts.deleteWhere { Accounts.id eq candidate }
                        Accounts.deleteWhere { Accounts.id eq employer }
                    }
                }
            }
        }
    }
}
