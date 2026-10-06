package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable data class Me(val role: String? = null, val email: String = "", val emailVerified: Boolean = false)
@Serializable data class RoleChoice(val role: String)

/**
 * One-time role choice after the first login. Any verified Keycloak user may become a candidate or an
 * employer: the spec leaves employer verification out of the MVP, and the choice cannot be changed later
 * because profiles, assessments and invitations are owned by the role-specific account.
 */
fun Route.accountRoutes(db: Database, audience: String) {
    get("/me") {
        val sub = call.role(audience, "any")
        val claims = call.principal<JWTPrincipal>()!!.payload
        val adminEmployer = "employer" in call.tokenRoles(audience)
        val role = db.tx {
            boundRole(sub)?.second
                // Employers assigned in the Keycloak admin console before in-app choice existed.
                ?: if (adminEmployer) { createAccount(sub, "employer"); "employer" } else null
        }
        call.respond(Me(role, claims.getClaim("email").asString().orEmpty(), claims.getClaim("email_verified").asBoolean() == true))
    }
    post("/me/role") {
        val sub = call.role(audience, "any")
        val role = call.receive<RoleChoice>().role.takeIf { it == "candidate" || it == "employer" }
            ?: bad("role must be candidate or employer")
        call.respond(db.tx {
            val bound = boundRole(sub)?.second
            if (bound != null && bound != role) conflict("Account already bound to $bound")
            if (bound == null) createAccount(sub, role)
            Me(role)
        })
    }
}
