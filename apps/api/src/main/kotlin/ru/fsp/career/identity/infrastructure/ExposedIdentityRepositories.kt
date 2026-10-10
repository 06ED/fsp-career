package ru.fsp.career.identity.infrastructure

import ru.fsp.career.identity.domain.*
import ru.fsp.career.shared.infrastructure.Database
import java.util.UUID
import ru.fsp.career.shared.domain.conflict

internal class ExposedAccountRepository(private val db: Database) : AccountRepository {
    override suspend fun role(subject: String, adminEmployer: Boolean) = db.tx { roleFor(subject, adminEmployer) }
    override suspend fun choose(subject: String, role: String, identity: BrowserIdentity?) =
        db.tx { chooseRole(subject, role, identity) }
}

internal class ExposedIdentityRepository(private val db: Database) : IdentityRepository {
    override suspend fun registerLocal(email: String, hash: String) = db.registerLocal(email, hash)
    override suspend fun localPassword(email: String) = db.localPassword(email)
    override suspend fun requestVerification(identity: UUID) = db.requestVerification(identity)
    override suspend fun verifyEmail(token: String) = db.verifyEmail(token)
    override suspend fun external(provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String) =
        db.tx { providerIdentity(provider, issuer, subject, email, verified, legacyIssuer) }
    override suspend fun linkExternal(identity: UUID, provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String) {
        try { db.tx { linkProvider(identity, provider, issuer, subject, email, verified, legacyIssuer) } }
        catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
            if (e.sqlState != "23505") throw e
            conflict("This identity is already linked")
        }
    }
    override suspend fun linkedProviders(identity: UUID) = db.tx { linkedProviders(identity) }
    override suspend fun session(cookie: String?) = db.session(cookie)
    override suspend fun issueSession(old: String?, identity: UUID?) = db.issueSession(old, identity)
    override suspend fun saveState(state: String, cookie: String, provider: String, attempt: OAuthAttempt) {
        db.saveState(state, cookie, provider, attempt)
    }
    override suspend fun consumeState(state: String?, cookie: String?, provider: String) = db.consumeState(state, cookie, provider)
}
