package ru.fsp.career.assessment

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.jetbrains.exposed.sql.selectAll
import ru.fsp.career.CandidateProfiles
import ru.fsp.career.Database
import ru.fsp.career.account
import ru.fsp.career.bad
import ru.fsp.career.role
import ru.fsp.career.uuid
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Candidate testing: survey → declared grade → adaptive test for that grade → confirmed category.
 * Mounted inside `route("/api")` and `authenticate("keycloak")`. Catalog and blueprint preview contain no
 * personal data and are open to any authenticated user (employers can see how a vacancy becomes a test).
 */
fun Route.assessmentRoutes(db: Database, audience: String) {
    val previewRandom: Random = SecureRandom().asKotlinRandom()

    get("/assessments/catalog") { call.respond(catalogView()) }

    post("/assessments/blueprint") {
        val request = call.receive<BlueprintPreviewRequest>()
        call.respond(previewBlueprint(request, previewRandom))
    }

    get("/assessments/eligibility") {
        val sub = call.role(audience, "candidate")
        val requested = call.request.queryParameters["specialization"]?.let(::requireSpecialization)
        call.respond(db.tx {
            val id = account(sub, "candidate")
            val now = nowUtc()
            closeExpired(id, now)
            val fromProfile = CandidateProfiles.selectAll().where { CandidateProfiles.accountId eq id }
                .single()[CandidateProfiles.specialization].takeIf { it.isNotBlank() }
            val specialization = requested ?: fromProfile?.let(::requireSpecialization)
                ?: bad("Pass specialization or complete the survey first")
            eligibility(id, specialization, now)
        })
    }

    get("/assessments/history") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx {
            val id = account(sub, "candidate")
            closeExpired(id, nowUtc())
            AssessmentHistoryView(history(id))
        })
    }

    get("/assessments/attempts/current") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx {
            val id = account(sub, "candidate")
            val closed = closeExpired(id, nowUtc())
            CurrentAssessmentView(currentAttempt(id), closed)
        })
    }

    post("/assessments/attempts") {
        val sub = call.role(audience, "candidate")
        val plan = planStart(call.receive<StartAssessmentRequest>())
        when (val outcome = db.tx { startAttempt(account(sub, "candidate"), plan, nowUtc()) }) {
            is StartOutcome.Started -> call.respond(HttpStatusCode.Created, outcome.attempt)
            is StartOutcome.Blocked -> call.respond(HttpStatusCode.Conflict, outcome.conflict)
        }
    }

    post("/assessments/attempts/{id}/answers") {
        val sub = call.role(audience, "candidate")
        val attemptId = uuid(call.parameters["id"] ?: bad("Attempt ID required"))
        val request = call.receive<AssessmentAnswerRequest>()
        call.respond(db.tx { answerItem(account(sub, "candidate"), attemptId, request, nowUtc()) })
    }
}
