package ru.fsp.career.identity.infrastructure

import org.jetbrains.exposed.dao.id.IdTable
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

// Flyway owns DDL; these mappings preserve the existing schema.
// Raw key aliases let batched SQL projections use UUID/String values alongside DAO EntityIDs.
internal object Accounts : IdTable<UUID>("accounts") {
    val key = uuid("id")
    override val id = key.entityId()
    val subject = text("subject")
    val role = text("role")
    override val primaryKey = PrimaryKey(id)
}
internal object AuthIdentities : IdTable<UUID>("auth_identities") {
    val key = uuid("id")
    override val id = key.entityId()
    val provider = text("provider")
    val issuer = text("issuer")
    val externalSubject = text("external_subject")
    val accountId = reference("account_id", Accounts.key).nullable()
    val email = text("email")
    val emailVerified = bool("email_verified")
    override val primaryKey = PrimaryKey(id)
}
internal object LocalCredentials : IdTable<UUID>("local_credentials") {
    val identityId = reference("identity_id", AuthIdentities.key)
    override val id = identityId.entityId().also { it.foreignKey = identityId.foreignKey }
    val passwordHash = text("password_hash")
    override val primaryKey = PrimaryKey(identityId)
}
internal object BrowserSessions : IdTable<String>("browser_sessions") {
    val idHash = text("id_hash")
    override val id = idHash.entityId()
    val identityId = reference("identity_id", AuthIdentities.key).nullable()
    val csrfToken = text("csrf_token")
    val expiresAt = timestampWithTimeZone("expires_at")
    override val primaryKey = PrimaryKey(idHash)
}
internal object OauthTransactions : IdTable<String>("oauth_transactions") {
    val stateHash = text("state_hash")
    override val id = stateHash.entityId()
    val sessionHash = text("session_hash")
    val provider = text("provider")
    val verifier = text("verifier")
    val nonce = text("nonce")
    val expiresAt = timestampWithTimeZone("expires_at")
    override val primaryKey = PrimaryKey(stateHash)
}
