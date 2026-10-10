package ru.fsp.career

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.shared.domain.DomainError
import ru.fsp.career.shared.infrastructure.Database
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.*

class EmailVerificationDatabaseTest {
    @Test fun tokensAreHashedExpiringSingleUseAndReplacedOnResend() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            val email = "verify-${UUID.randomUUID()}@example.com"
            val id = db.registerLocal(email, "test hash")
            try {
                val first = assertNotNull(db.requestVerification(id)).second
                assertNull(db.verifyEmail("invalid"))
                db.tx {
                    val row = LocalCredentials.selectAll().where { LocalCredentials.identityId eq id }.single()
                    assertEquals(digest(first), row[LocalCredentials.verificationHash])
                    assertFalse(IdentityEntity[id].emailVerified)
                }
                assertFailsWith<DomainError> { db.requestVerification(id) }
                db.tx { LocalCredentials.update({ LocalCredentials.identityId eq id }) {
                    it[verificationSentAt] = OffsetDateTime.now().minusMinutes(2)
                } }
                val second = assertNotNull(db.requestVerification(id)).second
                assertNotEquals(first, second)
                assertNull(db.verifyEmail(first))
                db.tx { LocalCredentials.update({ LocalCredentials.identityId eq id }) {
                    it[verificationExpiresAt] = OffsetDateTime.now().minusSeconds(1)
                } }
                assertNull(db.verifyEmail(second))
                db.tx { LocalCredentials.update({ LocalCredentials.identityId eq id }) {
                    it[verificationExpiresAt] = OffsetDateTime.now().plusMinutes(1)
                } }
                assertEquals(id, db.verifyEmail(second))
                assertNull(db.verifyEmail(second))
                assertNull(db.requestVerification(id))
                db.tx { assertTrue(IdentityEntity[id].emailVerified) }
            } finally {
                db.tx { AuthIdentities.deleteWhere { AuthIdentities.key eq id } }
            }
        }
    }
}
