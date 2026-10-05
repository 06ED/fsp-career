package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

internal fun Connection.account(subject: String, role: String): UUID {
    val existing = rows("SELECT id, role FROM accounts WHERE subject=? FOR UPDATE", subject) { it.getObject("id", UUID::class.java) to it.getString("role") }.firstOrNull()
    if (existing != null) {
        if (existing.second != role) forbidden("Account bound to another role")
        return existing.first
    }
    val id = rows("INSERT INTO accounts(subject,role) VALUES (?,?) RETURNING id", subject, role) { it.getObject(1, UUID::class.java) }.single()
    if (role == "candidate") exec("INSERT INTO candidate_profiles(account_id) VALUES (?)", id)
    else {
        exec("INSERT INTO employer_companies(account_id) VALUES (?)", id)
        exec("INSERT INTO employer_needs(account_id) VALUES (?)", id)
    }
    return id
}
private fun ResultSet.candidate() = CandidateProfile(getString("full_name"),getString("email"),getString("phone"),getBoolean("consent_publication"),getString("specialization"),getString("industry"),getString("desired_grade"),getString("grade"),getInt("score").takeUnless { wasNull() },Json.decodeFromString(getString("tech_stack")),getString("experience"),Json.decodeFromString(getString("soft_skills")),Json.decodeFromString(getString("target_roles")),Json.decodeFromString(getString("cv_history")),getBoolean("processing_consent"))
private const val PROFILE_SQL = """SELECT p.*, a.grade, a.score FROM candidate_profiles p LEFT JOIN LATERAL (SELECT grade,score FROM assessment_attempts WHERE candidate_id=p.account_id AND specialization=p.specialization AND submitted_at IS NOT NULL ORDER BY CASE grade WHEN 'senior' THEN 3 WHEN 'middle' THEN 2 ELSE 1 END DESC,score DESC LIMIT 1) a ON TRUE WHERE p.account_id=?"""
internal fun Connection.profile(id: UUID) = rows(PROFILE_SQL,id) { it.candidate() }.single()

fun Route.careerRoutes(db: Database, audience: String) {
    route("/api") {
        resumeRoutes(db,audience)
        get("/candidates/me") {
            val sub = call.role(audience,"candidate")
            call.respond(db.tx { it.profile(it.account(sub,"candidate")) })
        }
        put("/candidates/me") {
            val sub = call.role(audience,"candidate")
            val p = call.receive<CandidateProfile>()
            if (!p.processingConsent) bad("Grant processingConsent before storing a profile; DELETE /api/candidates/me/consent to revoke")
            grade(p.desiredGrade)
            validateCareerFields(p.experience,p.techStack,p.softSkills,p.targetRoles,p.cvHistory)
            val claims = call.principal<JWTPrincipal>()!!.payload
            val trustedEmail = if (claims.getClaim("email_verified").asBoolean() == true) claims.getClaim("email").asString().orEmpty() else ""
            if (p.email.isNotBlank() && p.email.trim() != trustedEmail) bad("Email must match verified Keycloak email")
            if (trustedEmail.isNotBlank() && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(trustedEmail)) bad("Invalid verified email")
            if (p.fullName.length > 200 || p.email.length > 320 || p.phone.length > 50 || p.specialization.length > 100 || p.industry.length > 100) bad("Profile field too long")
            call.respond(db.tx { c ->
                val id = c.account(sub,"candidate")
                c.exec("UPDATE candidate_profiles SET full_name=?,email=?,phone=?,processing_consent=true,consent_publication=?,specialization=?,industry=?,desired_grade=?,tech_stack=?::jsonb,experience=?,soft_skills=?::jsonb,target_roles=?::jsonb,cv_history=?::jsonb,updated_at=now() WHERE account_id=?",p.fullName.trim(),trustedEmail,p.phone.trim(),p.consentPublication,p.specialization.trim(),p.industry.trim(),p.desiredGrade,Json.encodeToString(p.techStack),p.experience.trim(),Json.encodeToString(p.softSkills),Json.encodeToString(p.targetRoles),Json.encodeToString(p.cvHistory),id)
                c.profile(id)
            })
        }
        delete("/candidates/me/consent") {
            val sub = call.role(audience,"candidate")
            call.respond(db.tx { c ->
                val id = c.account(sub,"candidate")
                c.exec("DELETE FROM invitations WHERE candidate_id=?",id)
                c.exec("DELETE FROM achievements WHERE candidate_id=?",id)
                c.exec("DELETE FROM assessment_attempts WHERE candidate_id=?",id)
                c.exec("DELETE FROM resume_drafts WHERE candidate_id=?",id)
                c.exec("UPDATE candidate_profiles SET full_name='',email='',phone='',processing_consent=false,consent_publication=false,specialization='',industry='',desired_grade='junior',tech_stack='[]'::jsonb,experience='',soft_skills='[]'::jsonb,target_roles='[]'::jsonb,cv_history='[]'::jsonb,updated_at=now() WHERE account_id=?",id)
                c.profile(id)
            })
        }
        get("/assessments/history") {
            val sub = call.role(audience,"candidate")
            call.respond(db.tx { c ->
                val id = c.account(sub,"candidate")
                AssessmentHistory(c.rows("SELECT score,grade,specialization,submitted_at FROM assessment_attempts WHERE candidate_id=? AND submitted_at IS NOT NULL ORDER BY submitted_at DESC",id) {
                    Result(it.getInt(1),it.getString(2),it.getString(3),it.getTimestamp(4).toInstant().toString())
                })
            })
        }
        get("/assessments/questions") {
            val sub = call.role(audience,"candidate")
            call.respond(db.tx { c ->
                val id = c.account(sub,"candidate")
                val profile = c.profile(id)
                if (!profile.processingConsent) forbidden("Grant processing consent before assessment")
                val specialization = profile.specialization.takeIf { it.isNotBlank() } ?: bad("Set specialization before assessment")
                val last = c.rows("SELECT submitted_at FROM assessment_attempts WHERE candidate_id=? AND submitted_at IS NOT NULL ORDER BY submitted_at DESC LIMIT 1",id) { it.getTimestamp(1).toInstant() }.firstOrNull()
                if (last != null && last.plus(90,ChronoUnit.DAYS).isAfter(Instant.now())) conflict("Retake available at ${last.plus(90,ChronoUnit.DAYS)}")
                c.exec("DELETE FROM assessment_attempts WHERE candidate_id=? AND submitted_at IS NULL AND started_at < now()-interval '30 minutes'",id)
                val open = c.rows("SELECT id,question_ids,started_at FROM assessment_attempts WHERE candidate_id=? AND submitted_at IS NULL",id) { Triple(it.getObject(1,UUID::class.java), (it.getArray(2).array as Array<*>).map { n -> (n as Number).toLong() },it.getTimestamp(3).toInstant()) }.firstOrNull()
                val attempt = if (open != null) open else {
                    // "general" must be drawn once; two independent general draws could repeat IDs.
                    val specialized = if (specialization.equals("general",true)) emptyList() else
                        c.rows("SELECT id FROM questions WHERE active AND specialization=? ORDER BY random() LIMIT 3",specialization.lowercase()) { it.getLong(1) }
                    val chosen = if (specialized.size == 3) specialized else emptyList()
                    val selected = (chosen + c.rows("SELECT id FROM questions WHERE active AND specialization='general' ORDER BY random() LIMIT ?",5-chosen.size) { it.getLong(1) }).shuffled()
                    if (selected.size < 5) conflict("Question bank requires at least five questions")
                    val newId = c.rows("INSERT INTO assessment_attempts(candidate_id,specialization,question_ids) VALUES (?,?,?) RETURNING id",id,specialization,selected.toLongArray()) { it.getObject(1,UUID::class.java) }.single()
                    Triple(newId,selected,Instant.now())
                }
                val questions = c.rows("SELECT id,prompt,options::text FROM questions WHERE id=ANY(?)",c.createArrayOf("bigint",attempt.second.toTypedArray())) {
                    Question(it.getLong(1),it.getString(2),Json.decodeFromString<List<Option>>(it.getString(3)))
                }
                QuestionSet(attempt.first.toString(),questions,attempt.third.plus(30,ChronoUnit.MINUTES).toString())
            })
        }
        post("/assessments") {
            val sub = call.role(audience,"candidate"); val input = call.receive<Submission>()
            call.respond(HttpStatusCode.Created,db.tx { c ->
                val id = c.account(sub,"candidate")
                val attemptId = uuid(input.attemptId)
                val attempt = c.rows("SELECT specialization,question_ids,started_at,submitted_at FROM assessment_attempts WHERE id=? AND candidate_id=? FOR UPDATE",attemptId,id) { listOf(it.getString(1), (it.getArray(2).array as Array<*>).map { n -> (n as Number).toLong() },it.getTimestamp(3).toInstant(),it.getTimestamp(4)?.toInstant()) }.firstOrNull() ?: missing("Assessment not found")
                if (attempt[3] != null) conflict("Assessment already submitted")
                if ((attempt[2] as Instant).plus(30,ChronoUnit.MINUTES).isBefore(Instant.now())) conflict("Assessment expired")
                @Suppress("UNCHECKED_CAST") val ids = attempt[1] as List<Long>
                if (input.answers.size != ids.size || input.answers.map { it.questionId }.toSet() != ids.toSet()) bad("Answer every assigned question exactly once")
                val answers = c.rows("SELECT id,correct_option,options::text FROM questions WHERE id=ANY(?)",c.createArrayOf("bigint",ids.toTypedArray())) { Triple(it.getLong(1),it.getString(2),Json.decodeFromString<List<Option>>(it.getString(3))) }.associateBy { it.first }
                input.answers.forEach { a ->
                    if (answers[a.questionId]?.third?.none { it.id == a.optionId } != false) bad("Invalid answer option")
                    c.exec("INSERT INTO assessment_answers(attempt_id,question_id,option_id) VALUES (?,?,?)",attemptId,a.questionId,a.optionId)
                }
                val score = input.answers.count { answers[it.questionId]?.second == it.optionId } * 100 / ids.size
                val achieved = when { score >= 80 -> "senior"; score >= 60 -> "middle"; else -> "junior" }
                val previous = c.rows("SELECT grade FROM assessment_attempts WHERE candidate_id=? AND specialization=? AND submitted_at IS NOT NULL ORDER BY CASE grade WHEN 'senior' THEN 3 WHEN 'middle' THEN 2 ELSE 1 END DESC LIMIT 1",id,attempt[0]) { it.getString(1) }.firstOrNull()
                val finalGrade = listOfNotNull(previous,achieved).maxBy { listOf("junior","middle","senior").indexOf(it) }
                val submitted = c.rows("UPDATE assessment_attempts SET submitted_at=now(),score=?,grade=? WHERE id=? RETURNING submitted_at",score,finalGrade,attemptId) { it.getTimestamp(1).toInstant().toString() }.single()
                Result(score,finalGrade,attempt[0] as String,submitted)
            })
        }
        get("/employers/company") {
            val sub = call.role(audience,"employer")
            call.respond(db.tx { c -> val id=c.account(sub,"employer"); c.rows("SELECT name,description,website FROM employer_companies WHERE account_id=?",id) { Company(it.getString(1),it.getString(2),it.getString(3)) }.single() })
        }
        put("/employers/company") {
            val sub = call.role(audience,"employer"); val company=call.receive<Company>()
            val name=requireText(company.name,"Company name",200)
            if (company.description.length>2000 || company.website.length>500) bad("Company field too long")
            call.respond(db.tx { c -> val id=c.account(sub,"employer"); c.exec("UPDATE employer_companies SET name=?,description=?,website=? WHERE account_id=?",name,company.description.trim(),company.website.trim(),id); Company(name,company.description.trim(),company.website.trim()) })
        }
        get("/employers/needs") {
            val sub = call.role(audience,"employer")
            call.respond(db.tx { c -> val id=c.account(sub,"employer"); c.rows("SELECT specialization,industry,min_grade,description FROM employer_needs WHERE account_id=?",id) { Need(it.getString(1),it.getString(2),it.getString(3),it.getString(4)) }.single() })
        }
        put("/employers/needs") {
            val sub = call.role(audience,"employer"); val need=call.receive<Need>()
            grade(need.minGrade); val spec=requireText(need.specialization,"Specialization",100)
            if (need.industry.length>100 || need.description.length>2000) bad("Need field too long")
            call.respond(db.tx { c -> val id=c.account(sub,"employer"); c.exec("UPDATE employer_needs SET specialization=?,industry=?,min_grade=?,description=? WHERE account_id=?",spec,need.industry.trim(),need.minGrade,need.description.trim(),id); need.copy(specialization=spec) })
        }
        get("/matches") {
            val sub = call.role(audience,"employer")
            val specFilter = call.request.queryParameters["specialization"]?.trim()?.takeIf { it.isNotBlank() }
            if (specFilter != null && specFilter.length > 100) bad("Specialization too long")
            val gradeFilter = call.request.queryParameters["grade"]?.let { grade(it) }
            val skillFilter = call.request.queryParameters["techStack"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            if (skillFilter.size > 10 || skillFilter.any { it.length > 100 }) bad("Invalid techStack filter")
            val verifiedOnly = call.request.queryParameters["verifiedAchievements"]?.let { it.toBooleanStrictOrNull() ?: bad("verifiedAchievements must be true or false") } ?: false
            call.respond(db.tx { c ->
                val id=c.account(sub,"employer")
                val need=c.rows("SELECT specialization,industry,min_grade,description FROM employer_needs WHERE account_id=?",id) { Need(it.getString(1),it.getString(2),it.getString(3),it.getString(4)) }.single()
                if (need.specialization.isBlank() && specFilter == null) bad("Set employer needs first or provide specialization")
                val matches = c.rows("""SELECT p.account_id,p.full_name,p.specialization,a.grade,a.score,p.industry,p.tech_stack::text FROM candidate_profiles p JOIN LATERAL (SELECT grade,score FROM assessment_attempts WHERE candidate_id=p.account_id AND specialization=p.specialization AND submitted_at IS NOT NULL ORDER BY CASE grade WHEN 'senior' THEN 3 WHEN 'middle' THEN 2 ELSE 1 END DESC,score DESC LIMIT 1) a ON true WHERE p.processing_consent=true AND p.consent_publication=true AND lower(p.specialization)=lower(?) AND p.full_name<>'' AND CASE a.grade WHEN 'senior' THEN 3 WHEN 'middle' THEN 2 ELSE 1 END >= CASE ? WHEN 'senior' THEN 3 WHEN 'middle' THEN 2 ELSE 1 END""",specFilter ?: need.specialization,gradeFilter ?: need.minGrade) { r ->
                    val candidateId=r.getObject(1,UUID::class.java)
                    val achievements=c.rows("SELECT title,issuer,points FROM achievements WHERE candidate_id=? AND verified_at IS NOT NULL ORDER BY points DESC",candidateId) { Achievement(it.getString(1),it.getString(2),it.getInt(3)) }
                    val score=r.getInt(5); val bonus=achievements.sumOf { it.points }.coerceAtMost(20)
                    val industryBonus=if (need.industry.isNotBlank() && r.getString(6).equals(need.industry,true)) 5 else 0
                    Match(candidateId.toString(),r.getString(2),r.getString(3),r.getString(4),score,bonus,score+bonus+industryBonus,"Assessment $score/100; verified achievements +$bonus; industry match +$industryBonus",achievements,Json.decodeFromString(r.getString(7)))
                }.filter { m -> (!verifiedOnly || m.achievements.isNotEmpty()) && skillFilter.all { needed -> m.techStack.any { it.equals(needed,true) } } }.sortedWith(compareByDescending<Match> { it.totalScore }.thenBy { it.candidateId })
                Matches(matches)
            })
        }
        post("/invitations") {
            val sub=call.role(audience,"employer"); val request=call.receive<InviteRequest>()
            val description=requireText(request.description,"Description",4000)
            if (request.salaryMinRub<=0 || request.salaryMaxRub<request.salaryMinRub) bad("Salary range in RUB must be positive and min <= max")
            call.respond(HttpStatusCode.Created,db.tx { c ->
                val employer=c.account(sub,"employer"); val candidate=uuid(request.candidateId)
                val available=c.rows("SELECT 1 FROM candidate_profiles WHERE account_id=? AND processing_consent=true AND consent_publication=true AND full_name<>''",candidate) { true }.firstOrNull() == true
                if (!available) missing("Published candidate not found")
                val company=c.rows("SELECT name FROM employer_companies WHERE account_id=?",employer) { it.getString(1) }.single()
                if (company.isBlank()) bad("Set company name first")
                val invitationId=c.rows("INSERT INTO invitations(employer_id,candidate_id,description,salary_min_rub,salary_max_rub) VALUES (?,?,?,?,?) RETURNING id",employer,candidate,description,request.salaryMinRub,request.salaryMaxRub) { it.getObject(1,UUID::class.java) }.single()
                c.invitations("id=?",invitationId).single()
            })
        }
        get("/invitations") {
            val principal = call.principal<io.ktor.server.auth.jwt.JWTPrincipal>() ?: forbidden("Authentication required")
            val realm=principal.payload.getClaim("realm_access").asMap()?.get("roles") as? List<*>
            val client=(principal.payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
            val role=when { (realm.orEmpty()+client.orEmpty()).contains("employer") -> "employer"; (realm.orEmpty()+client.orEmpty()).contains("candidate") -> "candidate"; else -> forbidden("Role required") }
            val sub=call.role(audience,role)
            call.respond(db.tx { c -> val id=c.account(sub,role); Invitations(c.invitations(if(role=="candidate") "candidate_id=?" else "employer_id=?",id)) })
        }
        post("/invitations/{id}/view") { changeInvitation(db,audience,"viewed") }
        post("/invitations/{id}/accept") { changeInvitation(db,audience,"accepted") }
        post("/invitations/{id}/reject") { changeInvitation(db,audience,"rejected") }
    }
}
private fun Connection.invitations(where: String, id: UUID): List<Invitation> = rows("""SELECT i.*, CASE WHEN i.status='accepted' THEN p.email ELSE NULL END AS contact_email, CASE WHEN i.status='accepted' THEN p.phone ELSE NULL END AS contact_phone,co.name AS company_name,p.full_name AS candidate_name FROM invitations i JOIN candidate_profiles p ON p.account_id=i.candidate_id JOIN employer_companies co ON co.account_id=i.employer_id WHERE i.$where ORDER BY i.created_at DESC""",id) { r -> Invitation(r.getString("id"),r.getString("candidate_id"),r.getString("employer_id"),r.getString("description"),r.getInt("salary_min_rub"),r.getInt("salary_max_rub"),r.getString("status"),r.getTimestamp("created_at").toInstant().toString(),r.getString("contact_email"),r.getString("contact_phone"),r.getString("company_name"),r.getString("candidate_name")) }
private suspend fun RoutingContext.changeInvitation(db: Database,audience: String,status: String) {
    val sub=call.role(audience,"candidate"); val invitationId=uuid(call.parameters["id"] ?: bad("Invitation ID required"))
    call.respond(db.tx { c ->
        val id=c.account(sub,"candidate")
        val current=c.rows("SELECT status FROM invitations WHERE id=? AND candidate_id=? FOR UPDATE",invitationId,id) { it.getString(1) }.firstOrNull() ?: missing("Invitation not found")
        if (current !in if(status=="viewed") listOf("sent") else listOf("sent","viewed")) conflict("Invalid invitation transition")
        c.exec("UPDATE invitations SET status=?,updated_at=now() WHERE id=?",status,invitationId)
        c.invitations("id=?",invitationId).single()
    })
}
