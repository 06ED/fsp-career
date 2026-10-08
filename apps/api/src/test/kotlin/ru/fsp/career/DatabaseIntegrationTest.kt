package ru.fsp.career

import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.assessment.infrastructure.*
import ru.fsp.career.shared.infrastructure.Database

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DatabaseIntegrationTest {
    @Test fun migrationsAndDatabaseConstraints() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            kotlinx.coroutines.runBlocking {
                db.tx { assertEquals(25, Questions.selectAll().count()) }
                assertFails {
                    db.tx {
                        Accounts.insert {
                            it[id] = UUID.randomUUID()
                            it[subject] = "invalid-role-test-${UUID.randomUUID()}"
                            it[role] = "admin"
                        }
                    }
                }
                val id = db.tx { createAccount("exposed-test-${UUID.randomUUID()}", "candidate") }
                try {
                    db.tx {
                        assertEquals(false, profile(id).processingConsent)
                        CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
                            it[techStack] = listOf("Kotlin", "SQL")
                        }
                        assertEquals(listOf("Kotlin", "SQL"), profile(id).techStack)
                    }
                } finally {
                    db.tx { Accounts.deleteWhere { Accounts.id eq id } }
                }
            }
        }
    }
}
