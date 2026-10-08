package ru.fsp.career

import ru.fsp.career.identity.domain.*
import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.shared.infrastructure.Database

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.*

class BrowserAuthTest {
    @Test fun passwordAndTokens() = runBlocking {
        val hash = Passwords.hash("a long safe password")
        assertTrue(hash.startsWith("pbkdf2-sha256:600000:"))
        assertTrue(Passwords.verify("a long safe password", hash))
        assertFalse(Passwords.verify("another safe password", hash))
        assertNotEquals(opaque(), opaque())
    }

    @Test fun csrfRequiresExactOriginAndToken() {
        val session = BrowserSession(null, opaque())
        assertTrue(csrfValid("http://localhost:5173", "http://localhost:5173", session.csrf, session))
        assertFalse(csrfValid(null, "http://localhost:5173", session.csrf, session))
        assertFalse(csrfValid("http://localhost:3000", "http://localhost:5173", session.csrf, session))
        assertFalse(csrfValid("http://localhost:5173", "http://localhost:5173", opaque(), session))
        assertFalse(csrfValid("http://localhost:5173", "http://localhost:5173", session.csrf, null))
    }

    @Test fun sessionExpiryRevocationAndStateReplay() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            val cookie = db.issueSession(null, null)
            try {
                assertNotNull(db.session(cookie))
                val state = opaque()
                db.tx { OauthTransactions.insert {
                    it[stateHash] = digest(state); it[sessionHash] = digest(cookie)
                    it[provider] = "fsp"; it[verifier] = opaque(); it[nonce] = opaque()
                    it[expiresAt] = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
                } }
                assertNull(db.consumeState(state, opaque(), "fsp"))
                assertNull(db.consumeState(state, cookie, "fsp"))
                val state2 = opaque()
                db.tx { OauthTransactions.insert {
                    it[stateHash] = digest(state2); it[sessionHash] = digest(cookie)
                    it[provider] = "fsp"; it[verifier] = opaque(); it[nonce] = opaque()
                    it[expiresAt] = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
                } }
                assertNotNull(db.consumeState(state2, cookie, "fsp"))
                assertNull(db.consumeState(state2, cookie, "fsp"))
                val replacement = db.issueSession(cookie, null)
                assertNull(db.session(cookie))
                assertNotNull(db.session(replacement))
                db.tx { BrowserSessions.deleteWhere { BrowserSessions.idHash eq digest(replacement) } }
                assertNull(db.session(replacement))
            } finally {
                db.tx { BrowserSessions.deleteWhere { BrowserSessions.idHash eq digest(cookie) } }
            }
        }
    }

    @Test fun providerIdentityOnlyLinksTrustedIssuerAndSubject() = runBlocking {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            val subject = "legacy-${UUID.randomUUID()}"
            val issuer = "https://issuer.example/realms/fsp"
            val email = "same-${UUID.randomUUID()}@example.com"
            val account = db.tx { createAccount(subject, "employer") }
            try {
                val vk = db.tx { providerIdentity("vk", "https://id.vk.ru", "vk-$subject", email, false, issuer) }
                val foreign = db.tx { providerIdentity("fsp", "https://other.example", subject, email, true, issuer) }
                val trusted = db.tx { providerIdentity("fsp", issuer, subject, email, true, issuer) }
                assertEquals(trusted, db.tx { providerIdentity("fsp", issuer, subject, "", false, issuer) })
                db.tx {
                    assertNull(AuthIdentities.selectAll().where { AuthIdentities.id eq vk }.single()[AuthIdentities.accountId])
                    assertNull(AuthIdentities.selectAll().where { AuthIdentities.id eq foreign }.single()[AuthIdentities.accountId])
                    val row = AuthIdentities.selectAll().where { AuthIdentities.id eq trusted }.single()
                    assertEquals(account, row[AuthIdentities.accountId])
                    assertEquals("", row[AuthIdentities.email])
                    assertFalse(row[AuthIdentities.emailVerified])
                    assertEquals("employer", boundRole(subject)?.second)
                }
            } finally {
                db.tx {
                    AuthIdentities.deleteWhere { AuthIdentities.externalSubject eq "vk-$subject" }
                    AuthIdentities.deleteWhere { AuthIdentities.externalSubject eq subject }
                    Accounts.deleteWhere { Accounts.id eq account }
                }
            }
        }
    }
}
