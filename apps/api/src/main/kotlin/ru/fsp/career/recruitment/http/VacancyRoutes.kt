package ru.fsp.career.recruitment.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.http.role
import ru.fsp.career.recruitment.application.VacancyService
import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.*
import io.ktor.server.plugins.di.*

private fun RoutingContext.pathId() = uuid(call.parameters["id"] ?: bad("ID required"))

internal fun Route.vacancyRoutes(audience: String) {
    val service: VacancyService by application.dependencies

    get("/employers/vacancies") { call.respond(service.own(call.role(audience, "employer"))) }

    post("/employers/vacancies") {
        call.respond(
            HttpStatusCode.Created,
            service.create(
                call.role(audience, "employer"),
                call.receive<VacancyRequest>()
            )
        )
    }

    put("/employers/vacancies/{id}") {
        call.respond(
            service.update(
                call.role(audience, "employer"),
                pathId(),
                call.receive<VacancyRequest>()
            )
        )
    }

    post("/employers/vacancies/{id}/close") {
        call.respond(
            service.changeStatus(
                call.role(audience, "employer"),
                pathId(),
                "closed"
            )
        )
    }

    post("/employers/vacancies/{id}/reopen") {
        call.respond(
            service.changeStatus(
                call.role(audience, "employer"),
                pathId(),
                "open"
            )
        )
    }

    get("/employers/vacancies/{id}/applications") {
        call.respond(
            service.applicants(
                call.role(audience, "employer"),
                pathId()
            )
        )
    }

    put("/employers/applications/{id}/status") {
        call.respond(
            service.changeApplication(
                call.role(audience, "employer"),
                pathId(),
                call.receive<ApplicationStatusRequest>()
            )
        )
    }

    get("/vacancies") {
        call.respond(
            service.browse(
                call.role(audience, "candidate"),
                vacancyFilter { call.request.queryParameters[it] })
        )
    }

    post("/vacancies/{id}/applications") {
        call.respond(
            HttpStatusCode.Created,
            service.apply(call.role(audience, "candidate"), pathId(), call.receive<ApplyRequest>())
        )
    }

    get("/candidates/me/applications") {
        call.respond(service.applications(call.role(audience, "candidate")))
    }

    delete("/candidates/me/applications/{id}") {
        service.withdraw(
            call.role(audience, "candidate"),
            pathId()
        )

        call.respond(HttpStatusCode.NoContent)
    }
}
