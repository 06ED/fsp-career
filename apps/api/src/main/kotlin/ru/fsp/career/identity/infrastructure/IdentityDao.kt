package ru.fsp.career.identity.infrastructure

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

internal class AccountEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccountEntity>(Accounts)
    var subject by Accounts.subject
    var role by Accounts.role
}

internal class IdentityEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<IdentityEntity>(AuthIdentities)
    var provider by AuthIdentities.provider
    var issuer by AuthIdentities.issuer
    var externalSubject by AuthIdentities.externalSubject
    var accountId by AuthIdentities.accountId
    var email by AuthIdentities.email
    var emailVerified by AuthIdentities.emailVerified
}

internal class CredentialEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CredentialEntity>(LocalCredentials)
    var passwordHash by LocalCredentials.passwordHash
}

internal class SessionEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, SessionEntity>(BrowserSessions)
    var identityId by BrowserSessions.identityId
    var csrfToken by BrowserSessions.csrfToken
    var expiresAt by BrowserSessions.expiresAt
}

internal class OAuthStateEntity(id: EntityID<String>) : Entity<String>(id) {
    companion object : EntityClass<String, OAuthStateEntity>(OauthTransactions)
    var sessionHash by OauthTransactions.sessionHash
    var provider by OauthTransactions.provider
    var verifier by OauthTransactions.verifier
    var nonce by OauthTransactions.nonce
    var expiresAt by OauthTransactions.expiresAt
}
