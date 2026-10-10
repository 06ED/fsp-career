package ru.fsp.career.identity.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import ru.fsp.career.identity.domain.BrowserIdentity
import ru.fsp.career.shared.http.ApiError

internal data class BrowserPrincipal(val identity: BrowserIdentity)

/** Account ownership and the stored application role are enforced by the identity store. */
@Suppress("UNUSED_PARAMETER")
fun ApplicationCall.role(audience: String, requiredRole: String): String {
    val identity = principal<BrowserPrincipal>()?.identity
    if (identity?.provider == "local" && !identity.verified)
        throw ApiError(HttpStatusCode.Forbidden, "Verify your email before using the account")
    return identity?.subject ?: principal<JWTPrincipal>()?.payload?.subject
        ?: throw ApiError(HttpStatusCode.Unauthorized, "Authentication required")
}

fun ApplicationCall.authEmail(): String = principal<BrowserPrincipal>()?.identity?.email
    ?: principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString().orEmpty()
fun ApplicationCall.verifiedEmail(): String = principal<BrowserPrincipal>()?.identity?.let { if (it.verified) it.email else "" }
    ?: principal<JWTPrincipal>()?.payload?.let { if (it.getClaim("email_verified").asBoolean() == true) it.getClaim("email").asString().orEmpty() else "" }.orEmpty()
fun ApplicationCall.tokenRoles(audience: String): Set<String> {
    val payload = principal<JWTPrincipal>()?.payload ?: return emptySet()
    val realm = payload.getClaim("realm_access").asMap()?.get("roles") as? List<*>
    val client = (payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
    return (realm.orEmpty() + client.orEmpty()).filterIsInstance<String>().toSet()
}
