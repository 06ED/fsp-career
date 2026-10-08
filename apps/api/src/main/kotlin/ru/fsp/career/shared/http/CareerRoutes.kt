package ru.fsp.career.shared.http

import io.ktor.server.routing.*
import ru.fsp.career.identity.http.accountRoutes
import ru.fsp.career.profiles.http.*
import ru.fsp.career.assessment.http.assessmentRoutes
import ru.fsp.career.matching.http.matchingRoutes
import ru.fsp.career.achievements.http.achievementRoutes
import ru.fsp.career.recruitment.http.*

internal fun Route.careerRoutes(audience: String) {
    route("/api") {
        accountRoutes(audience)
        profileRoutes(audience)
        resumeRoutes(audience)
        assessmentRoutes(audience)
        matchingRoutes(audience)
        achievementRoutes(audience)
        vacancyRoutes(audience)
        taskRoutes(audience)
        invitationRoutes(audience)
    }
}
