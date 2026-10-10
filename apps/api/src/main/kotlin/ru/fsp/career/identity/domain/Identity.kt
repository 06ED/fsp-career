package ru.fsp.career.identity.domain

import kotlinx.serialization.Serializable
import java.util.UUID
import java.security.MessageDigest

@Serializable data class Me(val role: String? = null, val email: String = "", val emailVerified: Boolean = false)
@Serializable data class RoleChoice(val role: String)
@Serializable data class Credentials(val email: String, val password: String)
@Serializable data class EmailVerification(val token: String)
@Serializable data class Providers(val fsp: Boolean, val vk: Boolean)
@Serializable data class SessionResponse(val authenticated: Boolean, val email: String?, val csrfToken: String, val providers: Providers,
    val linkedProviders: Providers = Providers(false, false), val emailVerificationRequired: Boolean = false)

internal data class BrowserIdentity(val id: UUID, val subject: String, val email: String, val verified: Boolean, val provider: String = "")
internal data class BrowserSession(val identity: BrowserIdentity?, val csrf: String)
internal data class OAuthAttempt(val verifier: String, val nonce: String)

internal fun csrfValid(actualOrigin: String?, allowedOrigin: String, token: String?, session: BrowserSession?): Boolean =
    actualOrigin == allowedOrigin && session != null && MessageDigest.isEqual(token.orEmpty().toByteArray(), session.csrf.toByteArray())
