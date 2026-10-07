package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.assessment.assessmentRoutes
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
            val trustedEmail = call.verifiedEmail()
            if (p.email.isNotBlank() && p.email.trim() != trustedEmail) bad("Email must match verified identity email")
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
        assessmentRoutes(db, audience)
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
