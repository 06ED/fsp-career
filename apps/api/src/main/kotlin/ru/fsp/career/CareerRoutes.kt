package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

private fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun Route.careerRoutes(db: Database, audience: String) {
    route("/api") {
        accountRoutes(db, audience)
        resumeRoutes(db, audience)
        vacancyRoutes(db, audience)
        taskRoutes(db, audience)
        matchingRoutes(db, audience)
        get("/candidates/me") {
            val sub = call.role(audience, "candidate")
            call.respond(db.tx { profile(account(sub, "candidate")) })
        }
        put("/candidates/me") {
            val sub = call.role(audience, "candidate")
            val p = call.receive<CandidateProfile>()
            if (!p.processingConsent) bad("Grant processingConsent before storing a profile; DELETE /api/candidates/me/consent to revoke")
            grade(p.desiredGrade)
            validateCareerFields(p.experience, p.techStack, p.softSkills, p.targetRoles, p.cvHistory)
            val claims = call.principal<JWTPrincipal>()!!.payload
            val trustedEmail = if (claims.getClaim("email_verified").asBoolean() == true) claims.getClaim("email").asString().orEmpty() else ""
            if (p.email.isNotBlank() && p.email.trim() != trustedEmail) bad("Email must match verified Keycloak email")
            if (trustedEmail.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(trustedEmail)) bad("Invalid verified email")
            if (p.fullName.length > 200 || p.email.length > 320 || p.phone.length > 50 || p.specialization.length > 100 || p.industry.length > 100) bad("Profile field too long")
            call.respond(db.tx {
                val id = account(sub, "candidate")
                CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
                    it[fullName] = p.fullName.trim()
                    it[email] = trustedEmail
                    it[phone] = p.phone.trim()
                    it[processingConsent] = true
                    it[consentPublication] = p.consentPublication
                    it[specialization] = p.specialization.trim()
                    it[industry] = p.industry.trim()
                    it[desiredGrade] = p.desiredGrade
                    it[techStack] = p.techStack
                    it[experience] = p.experience.trim()
                    it[softSkills] = p.softSkills
                    it[targetRoles] = p.targetRoles
                    it[cvHistory] = p.cvHistory
                    it[updatedAt] = now()
                }
                profile(id)
            })
        }
        delete("/candidates/me/consent") {
            val sub = call.role(audience, "candidate")
            call.respond(db.tx {
                val id = account(sub, "candidate")
                eraseVacancyAndTaskData(id)
                InvitationsTable.deleteWhere { InvitationsTable.candidateId eq id }
                Achievements.deleteWhere { Achievements.candidateId eq id }
                AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq id }
                ResumeDrafts.deleteWhere { ResumeDrafts.candidateId eq id }
                CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
                    it[fullName] = ""
                    it[email] = ""
                    it[phone] = ""
                    it[processingConsent] = false
                    it[consentPublication] = false
                    it[specialization] = ""
                    it[industry] = ""
                    it[desiredGrade] = "junior"
                    it[techStack] = emptyList()
                    it[experience] = ""
                    it[softSkills] = emptyList()
                    it[targetRoles] = emptyList()
                    it[cvHistory] = emptyList()
                    it[updatedAt] = now()
                    it[fspId] = null
                    it[fspLinkedAt] = null
                    it[privacy] = CandidatePrivacy()
                }
                profile(id)
            })
        }
        get("/assessments/history") {
            val sub = call.role(audience, "candidate")
            call.respond(db.tx {
                val id = account(sub, "candidate")
                val results = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.candidateId eq id) and AssessmentAttempts.submittedAt.isNotNull()
                }.orderBy(AssessmentAttempts.submittedAt to SortOrder.DESC).map { row ->
                    Result(row[AssessmentAttempts.score]!!, row[AssessmentAttempts.grade]!!,
                        row[AssessmentAttempts.specialization], row[AssessmentAttempts.submittedAt]!!.toInstant().toString())
                }
                AssessmentHistory(results)
            })
        }
        get("/assessments/questions") {
            val sub = call.role(audience, "candidate")
            call.respond(db.tx {
                val id = account(sub, "candidate")
                val candidate = profile(id)
                if (!candidate.processingConsent) forbidden("Grant processing consent before assessment")
                val specialization = candidate.specialization.takeIf { it.isNotBlank() } ?: bad("Set specialization before assessment")
                val last = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.candidateId eq id) and AssessmentAttempts.submittedAt.isNotNull()
                }.orderBy(AssessmentAttempts.submittedAt to SortOrder.DESC).limit(1)
                    .firstOrNull()?.get(AssessmentAttempts.submittedAt)?.toInstant()
                if (last != null && last.plus(90, ChronoUnit.DAYS).isAfter(Instant.now()))
                    conflict("Retake available at ${last.plus(90, ChronoUnit.DAYS)}")
                val expiry = now().minusMinutes(30)
                AssessmentAttempts.deleteWhere {
                    (AssessmentAttempts.candidateId eq id) and AssessmentAttempts.submittedAt.isNull() and
                        (AssessmentAttempts.startedAt less expiry)
                }
                val open = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.candidateId eq id) and AssessmentAttempts.submittedAt.isNull()
                }.singleOrNull()
                val (attemptId, assigned, startedAt) = if (open != null) {
                    Triple(open[AssessmentAttempts.id], open[AssessmentAttempts.questionIds],
                        open[AssessmentAttempts.startedAt].toInstant())
                } else {
                    val specialized = if (specialization.equals("general", ignoreCase = true)) emptyList() else
                        Questions.selectAll().where {
                            (Questions.active eq true) and (Questions.specialization eq specialization.lowercase())
                        }.map { it[Questions.id] }.shuffled().take(3)
                    val chosen = specialized.takeIf { it.size == 3 }.orEmpty()
                    val general = Questions.selectAll().where {
                        (Questions.active eq true) and (Questions.specialization eq "general")
                    }.map { it[Questions.id] }.shuffled().take(5 - chosen.size)
                    val selected = (chosen + general).shuffled()
                    if (selected.size < 5) conflict("Question bank requires at least five questions")
                    val newId = UUID.randomUUID()
                    val started = now()
                    AssessmentAttempts.insert {
                        it[AssessmentAttempts.id] = newId
                        it[candidateId] = id
                        it[AssessmentAttempts.specialization] = specialization
                        it[questionIds] = selected
                        it[AssessmentAttempts.startedAt] = started
                    }
                    Triple(newId, selected, started.toInstant())
                }
                val questions = Questions.selectAll().where { Questions.id inList assigned }.map { row ->
                    Question(row[Questions.id], row[Questions.prompt], row[Questions.options])
                }
                QuestionSet(attemptId.toString(), questions, startedAt.plus(30, ChronoUnit.MINUTES).toString())
            })
        }
        post("/assessments") {
            val sub = call.role(audience, "candidate")
            val input = call.receive<Submission>()
            call.respond(HttpStatusCode.Created, db.tx {
                val id = account(sub, "candidate")
                val attemptId = uuid(input.attemptId)
                val attempt = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.id eq attemptId) and (AssessmentAttempts.candidateId eq id)
                }.forUpdate().singleOrNull() ?: missing("Assessment not found")
                if (attempt[AssessmentAttempts.submittedAt] != null) conflict("Assessment already submitted")
                if (attempt[AssessmentAttempts.startedAt].toInstant().plus(30, ChronoUnit.MINUTES).isBefore(Instant.now()))
                    conflict("Assessment expired")
                val ids = attempt[AssessmentAttempts.questionIds]
                if (input.answers.size != ids.size || input.answers.map { it.questionId }.toSet() != ids.toSet())
                    bad("Answer every assigned question exactly once")
                val answers = Questions.selectAll().where { Questions.id inList ids }
                    .associateBy { it[Questions.id] }
                input.answers.forEach { answer ->
                    if (answers[answer.questionId]?.get(Questions.options)?.none { it.id == answer.optionId } != false)
                        bad("Invalid answer option")
                    AssessmentAnswers.insert {
                        it[AssessmentAnswers.attemptId] = attemptId
                        it[questionId] = answer.questionId
                        it[optionId] = answer.optionId
                    }
                }
                val score = input.answers.count { answers[it.questionId]?.get(Questions.correctOption) == it.optionId } * 100 / ids.size
                val achieved = when { score >= 80 -> "senior"; score >= 60 -> "middle"; else -> "junior" }
                val previous = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.candidateId eq id) and
                        (AssessmentAttempts.specialization eq attempt[AssessmentAttempts.specialization]) and
                        AssessmentAttempts.submittedAt.isNotNull()
                }.mapNotNull { it[AssessmentAttempts.grade] }.maxByOrNull(::gradeRank)
                val finalGrade = listOfNotNull(previous, achieved).maxBy(::gradeRank)
                val submitted = now()
                AssessmentAttempts.update({ AssessmentAttempts.id eq attemptId }) {
                    it[submittedAt] = submitted
                    it[AssessmentAttempts.score] = score
                    it[grade] = finalGrade
                }
                Result(score, finalGrade, attempt[AssessmentAttempts.specialization], submitted.toInstant().toString())
            })
        }
        get("/employers/company") {
            val sub = call.role(audience, "employer")
            call.respond(db.tx {
                val id = account(sub, "employer")
                companyFrom(EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq id }.single())
            })
        }
        put("/employers/company") {
            val sub = call.role(audience, "employer")
            val company = validateCompany(call.receive<Company>())
            call.respond(db.tx {
                val id = account(sub, "employer")
                EmployerCompanies.update({ EmployerCompanies.accountId eq id }) {
                    it[name] = company.name
                    it[description] = company.description
                    it[website] = company.website
                    it[industry] = company.industry
                    it[contactEmail] = company.contactEmail
                    it[contactPhone] = company.contactPhone
                    it[contactTelegram] = company.contactTelegram
                    it[contactNote] = company.contactNote
                }
                company
            })
        }
        get("/employers/needs") {
            val sub = call.role(audience, "employer")
            call.respond(db.tx {
                val id = account(sub, "employer")
                needFrom(EmployerNeeds.selectAll().where { EmployerNeeds.accountId eq id }.single())
            })
        }
        put("/employers/needs") {
            val sub = call.role(audience, "employer")
            val need = call.receive<Need>()
            grade(need.minGrade)
            val spec = requireText(need.specialization, "Specialization", 100)
            if (need.industry.length > 100 || need.description.length > 2000) bad("Need field too long")
            val stack = validateNeedStack(need.techStack)
            call.respond(db.tx {
                val id = account(sub, "employer")
                EmployerNeeds.update({ EmployerNeeds.accountId eq id }) {
                    it[specialization] = spec
                    it[industry] = need.industry.trim()
                    it[minGrade] = need.minGrade
                    it[description] = need.description.trim()
                    it[techStack] = stack
                }
                need.copy(specialization = spec, industry = need.industry.trim(), description = need.description.trim(), techStack = stack)
            })
        }
        post("/invitations") {
            val sub = call.role(audience, "employer")
            val request = call.receive<InviteRequest>()
            val description = requireText(request.description, "Description", 4000)
            if (request.salaryMinRub <= 0 || request.salaryMaxRub < request.salaryMinRub)
                bad("Salary range in RUB must be positive and min <= max")
            if (request.contactMethod.length > 300) bad("contactMethod must contain at most 300 characters")
            call.respond(HttpStatusCode.Created, db.tx {
                val employer = account(sub, "employer")
                val candidate = uuid(request.candidateId)
                val available = CandidateProfiles.selectAll().where {
                    (CandidateProfiles.accountId eq candidate) and
                        (CandidateProfiles.processingConsent eq true) and
                        (CandidateProfiles.consentPublication eq true) and
                        (CandidateProfiles.fullName neq "")
                }.any()
                if (!available) missing("Published candidate not found")
                val company = companyFrom(EmployerCompanies.selectAll()
                    .where { EmployerCompanies.accountId eq employer }.single())
                if (company.name.isBlank()) bad("Set company name first")
                // Spec 2.2 rule 2: the invitation always carries the company name and a way to contact it.
                val contact = request.contactMethod.trim().ifBlank { companyContactLine(company) }
                if (contact.isBlank()) bad("Set company contact data or provide contactMethod")
                val invitationId = UUID.randomUUID()
                val created = now()
                InvitationsTable.insert {
                    it[InvitationsTable.id] = invitationId
                    it[employerId] = employer
                    it[candidateId] = candidate
                    it[InvitationsTable.description] = description
                    it[salaryMinRub] = request.salaryMinRub
                    it[salaryMaxRub] = request.salaryMaxRub
                    it[contactMethod] = contact
                    it[status] = "sent"
                    it[createdAt] = created
                    it[updatedAt] = created
                }
                invitation(invitationId)
            })
        }
        get("/invitations") {
            val sub = call.role(audience, "any")
            call.respond(db.tx {
                val (id, role) = boundRole(sub) ?: forbidden("Choose a role first: POST /api/me/role")
                Invitations(invitationsFor(role, id))
            })
        }
        post("/invitations/{id}/view") { changeInvitation(db, audience, "viewed") }
        post("/invitations/{id}/accept") { changeInvitation(db, audience, "accepted") }
        post("/invitations/{id}/reject") { changeInvitation(db, audience, "rejected") }
    }
}

private suspend fun RoutingContext.changeInvitation(db: Database, audience: String, status: String) {
    val sub = call.role(audience, "candidate")
    val invitationId = uuid(call.parameters["id"] ?: bad("Invitation ID required"))
    call.respond(db.tx {
        val id = account(sub, "candidate")
        val current = InvitationsTable.selectAll().where {
            (InvitationsTable.id eq invitationId) and (InvitationsTable.candidateId eq id)
        }.forUpdate().singleOrNull() ?: missing("Invitation not found")
        if (current[InvitationsTable.status] !in if (status == "viewed") listOf("sent") else listOf("sent", "viewed"))
            conflict("Invalid invitation transition")
        InvitationsTable.update({ InvitationsTable.id eq invitationId }) {
            it[InvitationsTable.status] = status
            it[updatedAt] = now()
        }
        invitation(invitationId)
    })
}
