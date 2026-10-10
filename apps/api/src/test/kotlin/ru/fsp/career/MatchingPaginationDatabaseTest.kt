package ru.fsp.career

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.fsp.career.assessment.infrastructure.AssessmentAttempts
import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.matching.domain.CandidateFilter
import ru.fsp.career.matching.infrastructure.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.shared.infrastructure.Database
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.*

class MatchingPaginationDatabaseTest {
    @Test fun bankCanReachCandidatesAfterTheFirstHundredWithoutFalseConfirmations() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            db.tx {
                val tag = "pagination-${UUID.randomUUID()}"
                val employer = createAccount("$tag-employer", "employer")
                val ids = List(106) { index ->
                    val candidate = createAccount("$tag-$index", "candidate")
                    CandidateProfiles.update({ CandidateProfiles.accountId eq candidate }) {
                        it[fullName] = "Candidate $index"; it[processingConsent] = true; it[consentPublication] = true
                        it[specialization] = "backend"; it[techStack] = listOf(tag)
                    }
                    AssessmentAttempts.insert {
                        it[id] = UUID.randomUUID(); it[candidateId] = candidate; it[specialization] = "backend"
                        it[questionIds] = emptyList(); it[submittedAt] = OffsetDateTime.now(); it[score] = 70
                        it[grade] = if (index == 105) null else "middle"
                        if (index == 105) { it[passed] = false; it[targetGrade] = "senior"; it[engineVersion] = "cat-v1" }
                    }
                    candidate
                }
                try {
                    val filter = CandidateFilter(stack = listOf(tag))
                    val first = browseCandidates(employer, filter, 100)
                    val last = browseCandidates(employer, filter, 100, assertNotNull(first.nextOffset))
                    assertEquals(105, first.total)
                    assertEquals(100, first.matches.size)
                    assertEquals(5, last.matches.size)
                    assertNull(last.nextOffset)
                    assertEquals(105, (first.matches + last.matches).map { it.candidateId }.toSet().size)
                    assertNull(profile(ids.last()).grade)
                    assertTrue(browseCandidates(employer, filter, 100, 1000).matches.isEmpty())
                } finally {
                    AssessmentAttempts.deleteWhere { candidateId inList ids }
                    Accounts.deleteWhere { Accounts.key inList (ids + employer) }
                }
            }
        }
    }
}
