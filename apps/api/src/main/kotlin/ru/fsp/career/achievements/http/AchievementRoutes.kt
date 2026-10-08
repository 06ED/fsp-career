package ru.fsp.career.achievements.http

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.achievements.domain.*
import ru.fsp.career.achievements.application.AchievementService
import ru.fsp.career.identity.http.*
import io.ktor.server.plugins.di.*

internal fun Route.achievementRoutes(audience: String) {
    val service: AchievementService by application.dependencies
    get("/candidates/me/fsp") { call.respond(service.status(call.role(audience, "candidate"))) }
    post("/candidates/me/fsp") { call.respond(service.link(call.role(audience, "candidate"), call.receive<FspLinkRequest>(), call.verifiedEmail())) }
    delete("/candidates/me/fsp") { call.respond(service.unlink(call.role(audience, "candidate"))) }
}
