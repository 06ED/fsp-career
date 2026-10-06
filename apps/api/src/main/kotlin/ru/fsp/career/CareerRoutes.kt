package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
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
        resumeRoutes(db, audience)
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
                }
                profile(id)
            })
        }
        assessmentRoutes(db, audience)
        get("/employers/company") {
            val sub = call.role(audience, "employer")
            call.respond(db.tx {
                val id = account(sub, "employer")
                val row = EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq id }.single()
                Company(row[EmployerCompanies.name], row[EmployerCompanies.description], row[EmployerCompanies.website])
            })
        }
        put("/employers/company") {
            val sub = call.role(audience, "employer")
            val company = call.receive<Company>()
            val name = requireText(company.name, "Company name", 200)
            if (company.description.length > 2000 || company.website.length > 500) bad("Company field too long")
            call.respond(db.tx {
                val id = account(sub, "employer")
                EmployerCompanies.update({ EmployerCompanies.accountId eq id }) {
                    it[EmployerCompanies.name] = name
                    it[description] = company.description.trim()
                    it[website] = company.website.trim()
                }
                Company(name, company.description.trim(), company.website.trim())
            })
        }
        get("/employers/needs") {
            val sub = call.role(audience, "employer")
            call.respond(db.tx {
                val id = account(sub, "employer")
                val row = EmployerNeeds.selectAll().where { EmployerNeeds.accountId eq id }.single()
                Need(row[EmployerNeeds.specialization], row[EmployerNeeds.industry],
                    row[EmployerNeeds.minGrade], row[EmployerNeeds.description])
            })
        }
        put("/employers/needs") {
            val sub = call.role(audience, "employer")
            val need = call.receive<Need>()
            grade(need.minGrade)
            val spec = requireText(need.specialization, "Specialization", 100)
            if (need.industry.length > 100 || need.description.length > 2000) bad("Need field too long")
            call.respond(db.tx {
                val id = account(sub, "employer")
                EmployerNeeds.update({ EmployerNeeds.accountId eq id }) {
                    it[specialization] = spec
                    it[industry] = need.industry.trim()
                    it[minGrade] = need.minGrade
                    it[description] = need.description.trim()
                }
                need.copy(specialization = spec)
            })
        }
        get("/matches") {
            val sub = call.role(audience, "employer")
            val specFilter = call.request.queryParameters["specialization"]?.trim()?.takeIf { it.isNotBlank() }
            if (specFilter != null && specFilter.length > 100) bad("Specialization too long")
            val gradeFilter = call.request.queryParameters["grade"]?.let { grade(it) }
            val skillFilter = call.request.queryParameters["techStack"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            if (skillFilter.size > 10 || skillFilter.any { it.length > 100 }) bad("Invalid techStack filter")
            val verifiedOnly = call.request.queryParameters["verifiedAchievements"]?.let {
                it.toBooleanStrictOrNull() ?: bad("verifiedAchievements must be true or false")
            } ?: false
            call.respond(db.tx {
                val id = account(sub, "employer")
                val row = EmployerNeeds.selectAll().where { EmployerNeeds.accountId eq id }.single()
                val need = Need(row[EmployerNeeds.specialization], row[EmployerNeeds.industry],
                    row[EmployerNeeds.minGrade], row[EmployerNeeds.description])
                if (need.specialization.isBlank() && specFilter == null)
                    bad("Set employer needs first or provide specialization")
                val spec = specFilter ?: need.specialization
                val published = CandidateProfiles.selectAll().where {
                    (CandidateProfiles.processingConsent eq true) and
                        (CandidateProfiles.consentPublication eq true) and
                        (CandidateProfiles.fullName neq "")
                }.filter { it[CandidateProfiles.specialization].equals(spec, ignoreCase = true) }
                if (published.isEmpty()) return@tx Matches(emptyList())
                val ids = published.map { it[CandidateProfiles.accountId] }
                val attempts = AssessmentAttempts.selectAll().where {
                    (AssessmentAttempts.candidateId inList ids) and AssessmentAttempts.submittedAt.isNotNull()
                }.groupBy { it[AssessmentAttempts.candidateId] }
                val verified = Achievements.selectAll().where {
                    (Achievements.candidateId inList ids) and Achievements.verifiedAt.isNotNull()
                }.orderBy(Achievements.points to SortOrder.DESC).groupBy { it[Achievements.candidateId] }
                val matches = published.mapNotNull { candidate ->
                    val candidateId = candidate[CandidateProfiles.accountId]
                    val best = attempts[candidateId].orEmpty()
                        .filter { it[AssessmentAttempts.specialization] == candidate[CandidateProfiles.specialization] }
                        .maxWithOrNull(compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }
                            .thenBy { it[AssessmentAttempts.score]!! }) ?: return@mapNotNull null
                    val assessedGrade = best[AssessmentAttempts.grade]!!
                    if (gradeRank(assessedGrade) < gradeRank(gradeFilter ?: need.minGrade)) return@mapNotNull null
                    val score = best[AssessmentAttempts.score]!!
                    val achievements = verified[candidateId].orEmpty().map {
                        Achievement(it[Achievements.title], it[Achievements.issuer], it[Achievements.points])
                    }
                    val bonus = achievements.sumOf { it.points }.coerceAtMost(20)
                    val industryBonus = if (need.industry.isNotBlank() &&
                        candidate[CandidateProfiles.industry].equals(need.industry, ignoreCase = true)) 5 else 0
                    Match(candidateId.toString(), candidate[CandidateProfiles.fullName],
                        candidate[CandidateProfiles.specialization], assessedGrade, score, bonus,
                        score + bonus + industryBonus,
                        "Assessment $score/100; verified achievements +$bonus; industry match +$industryBonus",
                        achievements, candidate[CandidateProfiles.techStack])
                }.filter { match ->
                    (!verifiedOnly || match.achievements.isNotEmpty()) &&
                        skillFilter.all { skill -> match.techStack.any { it.equals(skill, ignoreCase = true) } }
                }.sortedWith(compareByDescending<Match> { it.totalScore }.thenBy { it.candidateId })
                Matches(matches)
            })
        }
        post("/invitations") {
            val sub = call.role(audience, "employer")
            val request = call.receive<InviteRequest>()
            val description = requireText(request.description, "Description", 4000)
            if (request.salaryMinRub <= 0 || request.salaryMaxRub < request.salaryMinRub)
                bad("Salary range in RUB must be positive and min <= max")
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
                val company = EmployerCompanies.selectAll()
                    .where { EmployerCompanies.accountId eq employer }.single()[EmployerCompanies.name]
                if (company.isBlank()) bad("Set company name first")
                val invitationId = UUID.randomUUID()
                val created = now()
                InvitationsTable.insert {
                    it[InvitationsTable.id] = invitationId
                    it[employerId] = employer
                    it[candidateId] = candidate
                    it[InvitationsTable.description] = description
                    it[salaryMinRub] = request.salaryMinRub
                    it[salaryMaxRub] = request.salaryMaxRub
                    it[status] = "sent"
                    it[createdAt] = created
                    it[updatedAt] = created
                }
                invitation(invitationId)
            })
        }
        get("/invitations") {
            val principal = call.principal<JWTPrincipal>() ?: forbidden("Authentication required")
            val realm = principal.payload.getClaim("realm_access").asMap()?.get("roles") as? List<*>
            val client = (principal.payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
            val role = when {
                (realm.orEmpty() + client.orEmpty()).contains("employer") -> "employer"
                (realm.orEmpty() + client.orEmpty()).contains("candidate") -> "candidate"
                else -> forbidden("Role required")
            }
            val sub = call.role(audience, role)
            call.respond(db.tx { Invitations(invitationsFor(role, account(sub, role))) })
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
