package ru.fsp.career.recruitment.http

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.http.role
import ru.fsp.career.recruitment.application.TaskService
import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.*
import io.ktor.server.plugins.di.*

private fun RoutingContext.taskId() = uuid(call.parameters["id"] ?: bad("ID required"))
internal fun Route.taskRoutes(audience: String) {
    val service: TaskService by application.dependencies

    get("/employers/tasks") { call.respond(service.own(call.role(audience, "employer"))) }

    post("/employers/tasks") {
        call.respond(
            HttpStatusCode.Created,
            service.create(call.role(audience, "employer"), call.receive<ShortTaskRequest>())
        )
    }

    post("/employers/tasks/{id}/deactivate") {
        call.respond(
            service.changeActive(
                call.role(audience, "employer"),
                taskId(),
                false
            )
        )
    }

    post("/employers/tasks/{id}/activate") {
        call.respond(
            service.changeActive(
                call.role(audience, "employer"),
                taskId(),
                true
            )
        )
    }

    get("/employers/tasks/{id}/submissions") {
        call.respond(
            service.submissions(
                call.role(audience, "employer"),
                taskId()
            )
        )
    }

    put("/employers/task-submissions/{id}/rating") {
        call.respond(
            service.rate(
                call.role(audience, "employer"),
                taskId(),
                call.receive<TaskRatingRequest>()
            )
        )
    }

    get("/candidates/me/tasks/current") {
        call.respond(service.current(call.role(audience, "candidate")))
    }

    post("/candidates/me/task-submissions") {
        call.respond(
            HttpStatusCode.Created,
            service.submit(
                call.role(audience, "candidate"),
                call.receive<TaskSubmissionRequest>()
            )
        )
    }

    get("/candidates/me/task-submissions") {
        call.respond(service.history(call.role(audience, "candidate")))
    }
}
