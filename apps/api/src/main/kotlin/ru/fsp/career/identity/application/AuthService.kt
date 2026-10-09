package ru.fsp.career.identity.application

import ru.fsp.career.identity.domain.*
import java.util.UUID

internal class AuthService(private val identities: IdentityRepository, private val passwords: PasswordHasher) {
    suspend fun register(input: Credentials): UUID {
        val email = registrationEmail(input)
        val hash = passwords.hash(input.password)
        return identities.registerLocal(email, hash)
    }
    suspend fun login(input: Credentials): UUID? {
        val credentials = identities.localPassword(loginEmail(input))
        val valid = passwords.verify(input.password, credentials?.second ?: passwords.dummyHash)
        return credentials?.first?.takeIf { valid }
    }
    suspend fun external(provider: String, issuer: String, subject: String, email: String, verified: Boolean, legacyIssuer: String,
        linkTo: UUID? = null): UUID {
        if (linkTo == null) return identities.external(provider, issuer, subject, email, verified, legacyIssuer)
        identities.linkExternal(linkTo, provider, issuer, subject, email, verified, legacyIssuer)
        return linkTo
    }
}
