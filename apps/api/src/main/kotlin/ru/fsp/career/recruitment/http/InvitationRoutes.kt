package ru.fsp.career.recruitment.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.http.role
import ru.fsp.career.recruitment.domain.InviteRequest
import ru.fsp.career.recruitment.application.InvitationService
import ru.fsp.career.shared.domain.*
import io.ktor.server.plugins.di.*

internal fun Route.invitationRoutes(audience: String) {
    val service: InvitationService by application.dependencies

    post("/invitations") {
        call.respond(
            HttpStatusCode.Created,
            service.send(call.role(audience, "employer"), call.receive<InviteRequest>())
        )
    }

    get("/invitations") { call.respond(service.list(call.role(audience, "any"))) }

    for ((action, target) in listOf("view" to "viewed", "accept" to "accepted", "reject" to "rejected")) {
        post("/invitations/{id}/$action") {
            call.respond(
                service.change(
                    call.role(audience, "candidate"),
                    uuid(call.parameters["id"] ?: bad("Invitation ID required")),
                    target
                )
            )
        }
    }
}
