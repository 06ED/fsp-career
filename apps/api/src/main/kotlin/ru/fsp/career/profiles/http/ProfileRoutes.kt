package ru.fsp.career.profiles.http

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.http.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.application.ProfileService
import io.ktor.server.plugins.di.*

internal fun Route.profileRoutes(audience: String) {
    val service: ProfileService by application.dependencies

    get("/candidates/me/privacy") {
        call.respond(service.privacy(call.role(audience, "candidate")))
    }

    put("/candidates/me/privacy") {
        call.respond(
            service.updatePrivacy(
                call.role(audience, "candidate"),
                call.receive<CandidatePrivacy>()
            )
        )
    }
    get("/candidates/me") {
        call.respond(service.candidate(call.role(audience, "candidate")))
    }

    put("/candidates/me") {
        call.respond(
            service.updateCandidate(
                call.role(audience, "candidate"),
                call.receive<CandidateProfile>(),
                call.verifiedEmail()
            )
        )
    }

    delete("/candidates/me/consent") {
        call.respond(service.revokeConsent(call.role(audience, "candidate")))
    }

    get("/employers/company") {
        call.respond(service.company(call.role(audience, "employer")))
    }

    put("/employers/company") {
        call.respond(
            service.updateCompany(
                call.role(audience, "employer"),
                call.receive<Company>()
            )
        )
    }

    get("/employers/needs") {
        call.respond(service.need(call.role(audience, "employer")))
    }

    put("/employers/needs") {
        call.respond(service.updateNeed(call.role(audience, "employer"), call.receive<Need>()))
    }
}
