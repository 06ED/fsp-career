package ru.fsp.career.identity.infrastructure

import org.jetbrains.exposed.sql.*
import ru.fsp.career.identity.domain.*
import ru.fsp.career.shared.domain.bad
import ru.fsp.career.shared.domain.conflict
import ru.fsp.career.shared.infrastructure.Database
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
internal suspend fun Database.session(cookie: String?): BrowserSession? {
    if (cookie == null || cookie.length != 43) return null
    return tx {
        val row = SessionEntity.findById(digest(cookie))
            ?.takeIf { it.expiresAt.isAfter(now()) } ?: return@tx null
        val identity = row.identityId?.let { id ->
            IdentityEntity.findById(id)?.let {
                BrowserIdentity(id, it.accountId?.let { account ->
                    AccountEntity[account].subject
                } ?: "pending:$id", it.email, it.emailVerified)
            }
        }
        BrowserSession(identity, row.csrfToken)
    }
}
internal suspend fun Database.issueSession(old: String?, identity: UUID?): String = tx {
    if (old != null) SessionEntity.findById(digest(old))?.delete()
    val token = opaque()
    SessionEntity.new(digest(token)) { identityId = identity; csrfToken = opaque(); expiresAt = now().plusDays(7) }
    token
}
internal suspend fun Database.saveState(state: String, cookie: String, provider: String, attempt: OAuthAttempt): Unit = tx {
    OAuthStateEntity.new(digest(state)) {
        sessionHash = digest(cookie); this.provider = provider
        verifier = attempt.verifier; nonce = attempt.nonce; expiresAt = now().plusMinutes(5)
    }
    Unit
}
internal suspend fun Database.consumeState(state: String?, cookie: String?, provider: String): OAuthAttempt? {
    if (state == null || state.length != 43 || cookie == null || cookie.length != 43) return null
    return tx {
        val row = OAuthStateEntity.find { OauthTransactions.stateHash eq digest(state) }.forUpdate().singleOrNull() ?: return@tx null
        val valid = row.provider == provider && row.sessionHash == digest(cookie) && row.expiresAt.isAfter(now()) &&
            SessionEntity.findById(digest(cookie))?.expiresAt?.isAfter(now()) == true
        val attempt = if (valid) OAuthAttempt(row.verifier, row.nonce) else null
        row.delete()
        attempt
    }
}
internal fun Transaction.providerIdentity(provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String): UUID {
    require(subject.isNotBlank() && subject.length <= 256)
    val existing = IdentityEntity.find { (AuthIdentities.provider eq provider) and
        (AuthIdentities.issuer eq issuer) and (AuthIdentities.externalSubject eq subject) }.singleOrNull()
    val legacy = if (provider == "fsp" && issuer == legacyIssuer && !subject.startsWith("demo-seed") && !subject.startsWith("user:"))
        AccountEntity.find { Accounts.subject eq subject }.singleOrNull()?.id?.value else null
    if (existing != null) {
        if (existing.accountId == null && legacy != null) existing.accountId = legacy
        existing.email = email; existing.emailVerified = verified
        return existing.id.value
    }
    val id = UUID.randomUUID()
    // Only the verified issuer + subject can claim a legacy account; never match accounts by email.
    IdentityEntity.new(id) {
        this.provider = provider; this.issuer = issuer
        externalSubject = subject; accountId = legacy; this.email = email; emailVerified = verified
    }
    return id
}
internal suspend fun Database.registerLocal(email: String, hash: String): UUID = try { tx {
    val id = UUID.randomUUID()
    IdentityEntity.new(id) {
        provider = "local"; issuer = "local"
        externalSubject = id.toString(); this.email = email; emailVerified = false
    }.flush()
    CredentialEntity.new(id) { passwordHash = hash }
    id
} } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
    if (e.sqlState != "23505") throw e
    bad("Invalid email or password")
}
internal fun Transaction.linkProvider(identity: UUID, provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String) {
    require(provider in listOf("fsp", "vk"))
    val account = IdentityEntity[identity].accountId ?: conflict("Choose a role before linking a provider")
    // Serialize provider changes per account; the conditional update prevents taking another account's identity.
    AccountEntity.find { Accounts.key eq account }.forUpdate().single()
    val external = providerIdentity(provider, issuer, subject, email, verified, legacyIssuer)
    val linked = AuthIdentities.selectAll().where { (AuthIdentities.accountId eq account) and (AuthIdentities.provider eq provider) }.singleOrNull()
    if (linked != null && linked[AuthIdentities.key] != external) conflict("Another identity from this provider is already linked")
    val updated = AuthIdentities.update({ (AuthIdentities.key eq external) and
        (AuthIdentities.accountId.isNull() or (AuthIdentities.accountId eq account)) }) { it[accountId] = account }
    if (updated != 1) conflict("This identity is already linked to another account")
}
internal fun Transaction.linkedProviders(identity: UUID): Providers {
    val current = IdentityEntity[identity]
    val account = current.accountId ?: return Providers(current.provider == "fsp", current.provider == "vk")
    val providers = AuthIdentities.selectAll().where { AuthIdentities.accountId eq account }.map { it[AuthIdentities.provider] }
    return Providers("fsp" in providers, "vk" in providers)
}
internal suspend fun Database.localPassword(email: String): Pair<UUID, String>? = tx {
    val id = IdentityEntity.find { (AuthIdentities.provider eq "local") and (AuthIdentities.email eq email) }.singleOrNull()?.id?.value
    id?.let { CredentialEntity.findById(it)?.let { row -> id to row.passwordHash } }
}
