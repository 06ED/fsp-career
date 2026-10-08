package ru.fsp.career.identity.http

import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.application.AccountService
import ru.fsp.career.identity.domain.RoleChoice
import io.ktor.server.plugins.di.*

internal fun Route.accountRoutes(audience: String) {
    val service: AccountService by application.dependencies
    get("/me") {
        call.respond(service.me(call.role(audience, "any"), call.authEmail(), call.verifiedEmail().isNotBlank(), "employer" in call.tokenRoles(audience)))
    }
    post("/me/role") {
        call.respond(service.choose(call.role(audience, "any"), call.receive<RoleChoice>(), call.principal<BrowserPrincipal>()?.identity))
    }
}
