package ru.fsp.career.identity.domain

import java.util.UUID

internal interface AccountRepository {
    suspend fun role(subject: String, adminEmployer: Boolean): String?
    suspend fun choose(subject: String, role: String, identity: BrowserIdentity?): String
}

internal interface IdentityRepository {
    suspend fun registerLocal(email: String, hash: String): UUID
    suspend fun localPassword(email: String): Pair<UUID, String>?
    suspend fun external(provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String): UUID
    suspend fun linkExternal(identity: UUID, provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String)
    suspend fun linkedProviders(identity: UUID): Providers
    suspend fun session(cookie: String?): BrowserSession?
    suspend fun issueSession(old: String?, identity: UUID?): String
    suspend fun saveState(state: String, cookie: String, provider: String, attempt: OAuthAttempt)
    suspend fun consumeState(state: String?, cookie: String?, provider: String): OAuthAttempt?
}

internal interface PasswordHasher {
    val dummyHash: String
    suspend fun hash(password: String): String
    suspend fun verify(password: String, encoded: String): Boolean
}
