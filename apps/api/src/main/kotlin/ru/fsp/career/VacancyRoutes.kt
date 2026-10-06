package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// Vacancies and self-initiated applications: the classic secondary path next to the
// employer→candidate invitation mechanic. Every route checks the role and the ownership of
// the record; foreign records answer 404 so their existence is not disclosed.

private fun utcNow(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun Route.vacancyRoutes(db: Database, audience: String) {
    // ---- Employer: publish, edit, close/reopen, review applications ----
    get("/employers/vacancies") {
        val sub = call.role(audience, "employer")
        call.respond(db.tx { EmployerVacancies(employerVacancies(account(sub, "employer"))) })
    }
    post("/employers/vacancies") {
        val sub = call.role(audience, "employer")
        val input = validateVacancy(call.receive<VacancyRequest>())
        call.respond(HttpStatusCode.Created, db.tx {
            val employer = account(sub, "employer")
            requireVacancyCompanyName(employer)
            val id = UUID.randomUUID()
            val created = utcNow()
            Vacancies.insert {
                it[Vacancies.id] = id
                it[employerId] = employer
                it[status] = "open"
                it[createdAt] = created
                it[updatedAt] = created
                write(it, input)
            }
            employerVacancies(employer, id).single()
        })
    }
    put("/employers/vacancies/{id}") {
        val sub = call.role(audience, "employer")
        val vacancyId = pathId()
        val input = validateVacancy(call.receive<VacancyRequest>())
        call.respond(db.tx {
            val employer = account(sub, "employer")
            ownVacancy(employer, vacancyId)
            Vacancies.update({ Vacancies.id eq vacancyId }) {
                write(it, input)
                it[updatedAt] = utcNow()
            }
            employerVacancies(employer, vacancyId).single()
        })
    }
    post("/employers/vacancies/{id}/close") { changeVacancyStatus(db, audience, "closed") }
    post("/employers/vacancies/{id}/reopen") { changeVacancyStatus(db, audience, "open") }
    get("/employers/vacancies/{id}/applications") {
        val sub = call.role(audience, "employer")
        val vacancyId = pathId()
        call.respond(db.tx {
            val employer = account(sub, "employer")
            ownVacancy(employer, vacancyId)
            val rows = VacancyApplications.selectAll().where { VacancyApplications.vacancyId eq vacancyId }
                .orderBy(VacancyApplications.createdAt to SortOrder.ASC).toList()
            Applicants(employerVacancies(employer, vacancyId).single(), applicants(rows))
        })
    }
    put("/employers/applications/{id}/status") {
        val sub = call.role(audience, "employer")
        val applicationId = pathId()
        val target = call.receive<ApplicationStatusRequest>().status.trim().lowercase()
        if (target !in listOf("viewed", "accepted", "rejected")) bad("status must be viewed, accepted or rejected")
        call.respond(db.tx {
            val employer = account(sub, "employer")
            // Join through the vacancy so an employer can only touch applications to own vacancies.
            val current = (VacancyApplications innerJoin Vacancies).selectAll().where {
                (VacancyApplications.id eq applicationId) and (Vacancies.employerId eq employer)
            }.forUpdate().singleOrNull() ?: missing("Application not found")
            if (!applicationTransitionAllowed(current[VacancyApplications.status], target))
                conflict("Invalid application transition")
            VacancyApplications.update({ VacancyApplications.id eq applicationId }) {
                it[status] = target
                it[updatedAt] = utcNow()
            }
            applicants(VacancyApplications.selectAll().where { VacancyApplications.id eq applicationId }.toList()).single()
        })
    }

    // ---- Candidate: browse published vacancies, apply, track and withdraw ----
    get("/vacancies") {
        val sub = call.role(audience, "candidate")
        val filter = vacancyFilter { call.request.queryParameters[it] }
        call.respond(db.tx {
            val candidate = account(sub, "candidate")
            VacancyCards(vacancyCards(candidate, profile(candidate), filter))
        })
    }
    post("/vacancies/{id}/applications") {
        val sub = call.role(audience, "candidate")
        val vacancyId = pathId()
        val note = call.receive<ApplyRequest>().coverNote.trim()
        if (note.length > 2000) bad("Cover note must contain at most 2000 characters")
        call.respond(HttpStatusCode.Created, db.tx {
            val candidate = account(sub, "candidate") // row lock serializes this candidate's requests
            requireApplicationReady(profile(candidate))
            val vacancy = Vacancies.selectAll().where { Vacancies.id eq vacancyId }.singleOrNull()
                ?: missing("Vacancy not found")
            if (vacancy[Vacancies.status] != "open") conflict("Vacancy is closed")
            val duplicate = VacancyApplications.selectAll().where {
                (VacancyApplications.vacancyId eq vacancyId) and (VacancyApplications.candidateId eq candidate)
            }.any()
            if (duplicate) conflict("You have already applied to this vacancy")
            val id = UUID.randomUUID()
            val created = utcNow()
            VacancyApplications.insert {
                it[VacancyApplications.id] = id
                it[VacancyApplications.vacancyId] = vacancyId
                it[candidateId] = candidate
                it[coverNote] = note
                it[status] = "sent"
                it[createdAt] = created
                it[updatedAt] = created
            }
            candidateApplications(candidate, id).single()
        })
    }
    get("/candidates/me/applications") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx { CandidateApplications(candidateApplications(account(sub, "candidate"))) })
    }
    delete("/candidates/me/applications/{id}") {
        val sub = call.role(audience, "candidate")
        val applicationId = pathId()
        db.tx {
            val candidate = account(sub, "candidate")
            val current = VacancyApplications.selectAll().where {
                (VacancyApplications.id eq applicationId) and (VacancyApplications.candidateId eq candidate)
            }.forUpdate().singleOrNull() ?: missing("Application not found")
            if (!applicationWithdrawable(current[VacancyApplications.status]))
                conflict("Only sent or viewed applications can be withdrawn")
            // Deleting the row also withdraws the contact disclosure tied to this application.
            VacancyApplications.deleteWhere { VacancyApplications.id eq applicationId }
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun RoutingContext.pathId(): UUID = uuid(call.parameters["id"] ?: bad("ID required"))

private suspend fun RoutingContext.changeVacancyStatus(db: Database, audience: String, target: String) {
    val sub = call.role(audience, "employer")
    val vacancyId = pathId()
    call.respond(db.tx {
        val employer = account(sub, "employer")
        val current = ownVacancy(employer, vacancyId)
        if (!vacancyTransitionAllowed(current[Vacancies.status], target))
            conflict(if (target == "closed") "Vacancy is already closed" else "Vacancy is already open")
        Vacancies.update({ Vacancies.id eq vacancyId }) {
            it[status] = target
            it[updatedAt] = utcNow()
        }
        employerVacancies(employer, vacancyId).single()
    })
}

private fun Vacancies.write(statement: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>, input: VacancyRequest) {
    statement[title] = input.title
    statement[description] = input.description
    statement[specialization] = input.specialization
    statement[grade] = input.grade
    statement[techStack] = input.techStack
    statement[salaryMinRub] = input.salaryMinRub
    statement[salaryMaxRub] = input.salaryMaxRub
    statement[workFormat] = input.workFormat
    statement[city] = input.city
}

internal fun Transaction.requireVacancyCompanyName(employer: UUID) {
    val name = EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq employer }.single()[EmployerCompanies.name]
    if (name.isBlank()) bad("Set company name first")
}

private fun Transaction.ownVacancy(employer: UUID, vacancyId: UUID): ResultRow =
    Vacancies.selectAll().where { (Vacancies.id eq vacancyId) and (Vacancies.employerId eq employer) }
        .forUpdate().singleOrNull() ?: missing("Vacancy not found")

private fun Transaction.employerVacancies(employer: UUID, onlyId: UUID? = null): List<EmployerVacancy> {
    var condition: Op<Boolean> = Vacancies.employerId eq employer
    if (onlyId != null) condition = condition and (Vacancies.id eq onlyId)
    val rows = Vacancies.selectAll().where(condition).orderBy(Vacancies.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val count = VacancyApplications.id.count()
    val counts = VacancyApplications
        .select(VacancyApplications.vacancyId, VacancyApplications.status, count)
        .where { VacancyApplications.vacancyId inList rows.map { it[Vacancies.id] } }
        .groupBy(VacancyApplications.vacancyId, VacancyApplications.status)
        .groupBy({ it[VacancyApplications.vacancyId] }, { it[VacancyApplications.status] to it[count].toInt() })
    return rows.map { row ->
        val byStatus = counts[row[Vacancies.id]].orEmpty().toMap()
        EmployerVacancy(
            id = row[Vacancies.id].toString(),
            title = row[Vacancies.title],
            description = row[Vacancies.description],
            specialization = row[Vacancies.specialization],
            grade = row[Vacancies.grade],
            techStack = row[Vacancies.techStack],
            salaryMinRub = row[Vacancies.salaryMinRub],
            salaryMaxRub = row[Vacancies.salaryMaxRub],
            workFormat = row[Vacancies.workFormat],
            city = row[Vacancies.city],
            status = row[Vacancies.status],
            createdAt = row[Vacancies.createdAt].toInstant().toString(),
            updatedAt = row[Vacancies.updatedAt].toInstant().toString(),
            applications = ApplicationCounts(
                total = byStatus.values.sum(),
                sent = byStatus["sent"] ?: 0,
                viewed = byStatus["viewed"] ?: 0,
                accepted = byStatus["accepted"] ?: 0,
                rejected = byStatus["rejected"] ?: 0,
            ),
        )
    }
}

/**
 * Open vacancies for a candidate. Indexed predicates run in SQL; skills/free text in memory.
 * Recommended order: confirmed-category match first, then same specialization, then more
 * overlapping skills; ties keep newest-first.
 */
private fun Transaction.vacancyCards(candidate: UUID, me: CandidateProfile, filter: VacancyFilter): List<VacancyCard> {
    var condition: Op<Boolean> = Vacancies.status eq "open"
    filter.specialization?.let { condition = condition and (Vacancies.specialization eq it) }
    filter.grade?.let { condition = condition and (Vacancies.grade eq it) }
    filter.workFormat?.let { condition = condition and (Vacancies.workFormat eq it) }
    // "Salary from X": the vacancy can pay at least X somewhere in its range.
    filter.salaryFrom?.let { condition = condition and (Vacancies.salaryMaxRub greaterEq it) }
    val rows = Vacancies.selectAll().where(condition)
        .orderBy(Vacancies.createdAt to SortOrder.DESC).limit(500).toList()
    if (rows.isEmpty()) return emptyList()
    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList rows.map { it[Vacancies.employerId] }.distinct() }
        .associateBy { it[EmployerCompanies.accountId] }
    val mine = VacancyApplications.selectAll().where {
        (VacancyApplications.candidateId eq candidate) and
            (VacancyApplications.vacancyId inList rows.map { it[Vacancies.id] })
    }.associateBy { it[VacancyApplications.vacancyId] }
    return rows.mapNotNull { row ->
        val company = companies.getValue(row[Vacancies.employerId])
        val stack = row[Vacancies.techStack]
        val searchable = listOf(row[Vacancies.title], row[Vacancies.description], company[EmployerCompanies.name], row[Vacancies.city])
        if (!filter.acceptsText(stack, searchable)) return@mapNotNull null
        val fit = vacancyFit(me.specialization, me.grade, me.score, me.techStack,
            row[Vacancies.specialization], row[Vacancies.grade], stack, forCandidate = true)
        val application = mine[row[Vacancies.id]]
        VacancyCard(
            id = row[Vacancies.id].toString(),
            title = row[Vacancies.title],
            description = row[Vacancies.description],
            specialization = row[Vacancies.specialization],
            grade = row[Vacancies.grade],
            techStack = stack,
            salaryMinRub = row[Vacancies.salaryMinRub],
            salaryMaxRub = row[Vacancies.salaryMaxRub],
            workFormat = row[Vacancies.workFormat],
            city = row[Vacancies.city],
            companyName = company[EmployerCompanies.name],
            companyDescription = company[EmployerCompanies.description],
            companyWebsite = company[EmployerCompanies.website],
            createdAt = row[Vacancies.createdAt].toInstant().toString(),
            categoryMatch = fit.categoryMatch,
            stackOverlap = fit.overlap,
            fit = fit.text,
            myApplicationId = application?.get(VacancyApplications.id)?.toString(),
            myApplicationStatus = application?.get(VacancyApplications.status),
        )
    }.sortedWith(
        compareByDescending<VacancyCard> { it.categoryMatch }
            .thenByDescending { it.specialization == me.specialization }
            .thenByDescending { it.stackOverlap.size }
    )
}

private fun Transaction.candidateApplications(candidate: UUID, onlyId: UUID? = null): List<CandidateApplication> {
    var condition: Op<Boolean> = VacancyApplications.candidateId eq candidate
    if (onlyId != null) condition = condition and (VacancyApplications.id eq onlyId)
    val rows = (VacancyApplications innerJoin Vacancies).selectAll().where(condition)
        .orderBy(VacancyApplications.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList rows.map { it[Vacancies.employerId] }.distinct() }
        .associate { it[EmployerCompanies.accountId] to it[EmployerCompanies.name] }
    return rows.map { row ->
        CandidateApplication(
            id = row[VacancyApplications.id].toString(),
            vacancyId = row[Vacancies.id].toString(),
            vacancyTitle = row[Vacancies.title],
            vacancyStatus = row[Vacancies.status],
            companyName = companies[row[Vacancies.employerId]].orEmpty(),
            specialization = row[Vacancies.specialization],
            grade = row[Vacancies.grade],
            salaryMinRub = row[Vacancies.salaryMinRub],
            salaryMaxRub = row[Vacancies.salaryMaxRub],
            workFormat = row[Vacancies.workFormat],
            city = row[Vacancies.city],
            coverNote = row[VacancyApplications.coverNote],
            status = row[VacancyApplications.status],
            createdAt = row[VacancyApplications.createdAt].toInstant().toString(),
            updatedAt = row[VacancyApplications.updatedAt].toInstant().toString(),
        )
    }
}

/**
 * Best confirmed assessment per candidate in their *current* profile specialization — the same
 * rule as profile(): highest grade, then highest score. Batched to avoid N+1 queries.
 */
internal fun Transaction.applicantCategories(profiles: Collection<ResultRow>): Map<UUID, Pair<String, Int>> {
    if (profiles.isEmpty()) return emptyMap()
    val specializations = profiles.associate { it[CandidateProfiles.accountId] to it[CandidateProfiles.specialization] }
    return AssessmentAttempts.selectAll().where {
        (AssessmentAttempts.candidateId inList specializations.keys) and AssessmentAttempts.submittedAt.isNotNull()
    }.filter { it[AssessmentAttempts.specialization] == specializations[it[AssessmentAttempts.candidateId]] }
        .groupBy { it[AssessmentAttempts.candidateId] }
        .mapValues { (_, attempts) ->
            attempts.maxWith(compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }
                .thenBy { it[AssessmentAttempts.score]!! })
                .let { it[AssessmentAttempts.grade]!! to it[AssessmentAttempts.score]!! }
        }
}

/**
 * Applicants for the employer, best fit first: confirmed-category match, then assessed grade and
 * score, then earliest application (rows arrive ordered by created_at). Contacts are returned because each row exists only because the
 * candidate applied to this employer's vacancy (spec 2.2 rule 2); withdrawing deletes the row.
 */
private fun Transaction.applicants(rows: List<ResultRow>): List<Applicant> {
    if (rows.isEmpty()) return emptyList()
    val vacancies = Vacancies.selectAll()
        .where { Vacancies.id inList rows.map { it[VacancyApplications.vacancyId] }.distinct() }
        .associateBy { it[Vacancies.id] }
    val candidateIds = rows.map { it[VacancyApplications.candidateId] }.distinct()
    val profiles = CandidateProfiles.selectAll().where { CandidateProfiles.accountId inList candidateIds }
        .associateBy { it[CandidateProfiles.accountId] }
    val categories = applicantCategories(profiles.values)
    val signals = taskSignals(candidateIds)
    return rows.map { row ->
        val vacancy = vacancies.getValue(row[VacancyApplications.vacancyId])
        val candidateId = row[VacancyApplications.candidateId]
        val profile = profiles.getValue(candidateId)
        val category = categories[candidateId]
        val signal = signals[candidateId]
        val fit = vacancyFit(profile[CandidateProfiles.specialization], category?.first, category?.second,
            profile[CandidateProfiles.techStack], vacancy[Vacancies.specialization], vacancy[Vacancies.grade],
            vacancy[Vacancies.techStack], forCandidate = false)
        val taskNote = signal?.let { s ->
            " Регулярные задания: ${s.submissions}" +
                (s.averageRating?.let { ", средняя оценка работодателей ${"%.1f".format(java.util.Locale.ROOT, it).replace('.', ',')}" } ?: "") + "."
        }.orEmpty()
        Applicant(
            id = row[VacancyApplications.id].toString(),
            vacancyId = vacancy[Vacancies.id].toString(),
            candidateId = candidateId.toString(),
            fullName = profile[CandidateProfiles.fullName],
            specialization = profile[CandidateProfiles.specialization],
            assessedGrade = category?.first,
            score = category?.second,
            techStack = profile[CandidateProfiles.techStack],
            experience = profile[CandidateProfiles.experience],
            coverNote = row[VacancyApplications.coverNote],
            status = row[VacancyApplications.status],
            createdAt = row[VacancyApplications.createdAt].toInstant().toString(),
            updatedAt = row[VacancyApplications.updatedAt].toInstant().toString(),
            contactEmail = profile[CandidateProfiles.email],
            contactPhone = profile[CandidateProfiles.phone],
            categoryMatch = fit.categoryMatch,
            stackOverlap = fit.overlap,
            fit = fit.text + taskNote,
            taskSubmissions = signal?.submissions ?: 0,
            taskAverageRating = signal?.averageRating,
            lastTaskActivityAt = signal?.lastActivityAt?.toString(),
        )
    }.sortedWith(
        compareByDescending<Applicant> { it.categoryMatch }
            .thenByDescending { it.assessedGrade?.let(::gradeRank) ?: 0 }
            .thenByDescending { it.score ?: -1 }
    ) // stable sort: equal applicants keep the query's earliest-first order
}

/** Called on consent revocation (152-FZ): removes the candidate's applications, task answers and offers. */
internal fun Transaction.eraseVacancyAndTaskData(candidate: UUID) {
    ShortTaskOffers.deleteWhere { ShortTaskOffers.candidateId eq candidate }
    ShortTaskSubmissions.deleteWhere { ShortTaskSubmissions.candidateId eq candidate }
    VacancyApplications.deleteWhere { VacancyApplications.candidateId eq candidate }
}
