package ru.fsp.career.assessment.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.assessment.application.AssessmentService
import ru.fsp.career.assessment.domain.*
import ru.fsp.career.identity.http.role
import ru.fsp.career.shared.domain.*
import io.ktor.server.plugins.di.*

internal fun Route.assessmentRoutes(audience: String) {
    val service: AssessmentService by application.dependencies
    get("/assessments/catalog") { call.respond(service.catalog()) }
    post("/assessments/blueprint") { call.respond(service.preview(call.receive<BlueprintPreviewRequest>())) }
    get("/assessments/eligibility") { call.respond(service.eligibility(call.role(audience, "candidate"), call.request.queryParameters["specialization"])) }
    get("/assessments/history") { call.respond(service.history(call.role(audience, "candidate"))) }
    get("/assessments/attempts/current") { call.respond(service.current(call.role(audience, "candidate"))) }
    post("/assessments/attempts") {
        when (val outcome = service.start(call.role(audience, "candidate"), call.receive<StartAssessmentRequest>())) {
            is StartOutcome.Started -> call.respond(HttpStatusCode.Created, outcome.attempt)
            is StartOutcome.Blocked -> call.respond(HttpStatusCode.Conflict, outcome.conflict)
        }
    }
    post("/assessments/attempts/{id}/answers") {
        call.respond(service.answer(call.role(audience, "candidate"), uuid(call.parameters["id"] ?: bad("Attempt ID required")), call.receive<AssessmentAnswerRequest>()))
    }
}
