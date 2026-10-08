package ru.fsp.career.recruitment.infrastructure

import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.assessment.infrastructure.AssessmentAttempts
import ru.fsp.career.shared.domain.*
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

internal fun Transaction.createVacancy(employer: UUID, input: VacancyRequest): EmployerVacancy {
    requireVacancyCompanyName(employer)
    val id = UUID.randomUUID()
    val created = utcNow()
    VacancyEntity.new(id) {
        employerId = employer; status = "open"; createdAt = created; updatedAt = created
        write(input)
    }.flush()
    return employerVacancies(employer, id).single()
}

internal fun Transaction.updateVacancy(employer: UUID, id: UUID, input: VacancyRequest): EmployerVacancy {
    ownVacancy(employer, id).apply { write(input); updatedAt = utcNow(); flush() }
    return employerVacancies(employer, id).single()
}

internal fun Transaction.changeVacancyStatus(employer: UUID, id: UUID, target: String): EmployerVacancy {
    val current = ownVacancy(employer, id)
    if (!vacancyTransitionAllowed(
            current.status,
            target
        )
    ) conflict(if (target == "closed") "Vacancy is already closed" else "Vacancy is already open")
    current.apply { status = target; updatedAt = utcNow(); flush() }
    return employerVacancies(employer, id).single()
}

internal fun Transaction.vacancyApplicants(employer: UUID, id: UUID): Applicants {
    ownVacancy(employer, id)
    val rows = VacancyApplications.selectAll().where { VacancyApplications.vacancyId eq id }
        .orderBy(VacancyApplications.createdAt to SortOrder.ASC).toList()
    return Applicants(employerVacancies(employer, id).single(), applicants(rows))
}

internal fun Transaction.changeApplicationStatus(employer: UUID, id: UUID, target: String): Applicant {
    val row = (VacancyApplications innerJoin Vacancies).selectAll().where {
        (VacancyApplications.key eq id) and (Vacancies.employerId eq employer)
    }.forUpdate().singleOrNull() ?: missing("Application not found")
    val current = ApplicationEntity.wrapRow(row)
    if (!applicationTransitionAllowed(
            current.status,
            target
        )
    ) conflict("Invalid application transition")
    current.apply { status = target; updatedAt = utcNow(); flush() }
    return applicants(VacancyApplications.selectAll().where { VacancyApplications.key eq id }.toList()).single()
}

internal fun Transaction.applyToVacancy(candidate: UUID, vacancyId: UUID, note: String): CandidateApplication {
    requireApplicationReady(profile(candidate))
    val vacancy =
        VacancyEntity.findById(vacancyId) ?: missing("Vacancy not found")
    if (vacancy.status != "open") conflict("Vacancy is closed")
    if (VacancyApplications.selectAll()
            .where { (VacancyApplications.vacancyId eq vacancyId) and (VacancyApplications.candidateId eq candidate) }
            .any()
    ) conflict("You have already applied to this vacancy")
    val id = UUID.randomUUID()
    val created = utcNow()
    ApplicationEntity.new(id) {
        this.vacancyId = vacancyId; candidateId = candidate
        coverNote = note; status = "sent"; createdAt = created; updatedAt = created
    }.flush()
    return candidateApplications(candidate, id).single()
}

internal fun Transaction.withdrawApplication(candidate: UUID, id: UUID) {
    val current = ApplicationEntity.find { (VacancyApplications.key eq id) and (VacancyApplications.candidateId eq candidate) }.forUpdate()
        .singleOrNull() ?: missing("Application not found")
    if (!applicationWithdrawable(current.status)) conflict("Only sent or viewed applications can be withdrawn")
    current.delete()
}

private fun VacancyEntity.write(input: VacancyRequest) {
    title = input.title
    description = input.description
    specialization = input.specialization
    grade = input.grade
    techStack = input.techStack
    salaryMinRub = input.salaryMinRub
    salaryMaxRub = input.salaryMaxRub
    workFormat = input.workFormat
    city = input.city
}

internal fun Transaction.requireVacancyCompanyName(employer: UUID) {
    val name =
        EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq employer }.single()[EmployerCompanies.name]
    if (name.isBlank()) bad("Set company name first")
}

private fun Transaction.ownVacancy(employer: UUID, vacancyId: UUID): VacancyEntity =
    VacancyEntity.find { (Vacancies.key eq vacancyId) and (Vacancies.employerId eq employer) }
        .forUpdate().singleOrNull() ?: missing("Vacancy not found")

internal fun Transaction.employerVacancies(employer: UUID, onlyId: UUID? = null): List<EmployerVacancy> {
    var condition: Op<Boolean> = Vacancies.employerId eq employer
    if (onlyId != null) condition = condition and (Vacancies.key eq onlyId)
    val rows = Vacancies.selectAll().where(condition).orderBy(Vacancies.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val count = VacancyApplications.key.count()
    val counts = VacancyApplications
        .select(VacancyApplications.vacancyId, VacancyApplications.status, count)
        .where { VacancyApplications.vacancyId inList rows.map { it[Vacancies.key] } }
        .groupBy(VacancyApplications.vacancyId, VacancyApplications.status)
        .groupBy({ it[VacancyApplications.vacancyId] }, { it[VacancyApplications.status] to it[count].toInt() })
    return rows.map { row ->
        val byStatus = counts[row[Vacancies.key]].orEmpty().toMap()
        EmployerVacancy(
            id = row[Vacancies.key].toString(),
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
internal fun Transaction.vacancyCards(candidate: UUID, me: CandidateProfile, filter: VacancyFilter): List<VacancyCard> {
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
                (VacancyApplications.vacancyId inList rows.map { it[Vacancies.key] })
    }.associateBy { it[VacancyApplications.vacancyId] }
    return rows.mapNotNull { row ->
        val company = companies.getValue(row[Vacancies.employerId])
        val stack = row[Vacancies.techStack]
        val searchable = listOf(
            row[Vacancies.title],
            row[Vacancies.description],
            company[EmployerCompanies.name],
            row[Vacancies.city]
        )
        if (!filter.acceptsText(stack, searchable)) return@mapNotNull null
        val fit = vacancyFit(
            me.specialization, me.grade, me.score, me.techStack,
            row[Vacancies.specialization], row[Vacancies.grade], stack, forCandidate = true
        )
        val application = mine[row[Vacancies.key]]
        VacancyCard(
            id = row[Vacancies.key].toString(),
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
            myApplicationId = application?.get(VacancyApplications.key)?.toString(),
            myApplicationStatus = application?.get(VacancyApplications.status),
        )
    }.sortedWith(
        compareByDescending<VacancyCard> { it.categoryMatch }
            .thenByDescending { it.specialization == me.specialization }
            .thenByDescending { it.stackOverlap.size }
    )
}

internal fun Transaction.candidateApplications(candidate: UUID, onlyId: UUID? = null): List<CandidateApplication> {
    var condition: Op<Boolean> = VacancyApplications.candidateId eq candidate
    if (onlyId != null) condition = condition and (VacancyApplications.key eq onlyId)
    val rows = (VacancyApplications innerJoin Vacancies).selectAll().where(condition)
        .orderBy(VacancyApplications.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList rows.map { it[Vacancies.employerId] }.distinct() }
        .associate { it[EmployerCompanies.accountId] to it[EmployerCompanies.name] }
    return rows.map { row ->
        CandidateApplication(
            id = row[VacancyApplications.key].toString(),
            vacancyId = row[Vacancies.key].toString(),
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
        .where { Vacancies.key inList rows.map { it[VacancyApplications.vacancyId] }.distinct() }
        .associateBy { it[Vacancies.key] }
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
        val fit = vacancyFit(
            profile[CandidateProfiles.specialization], category?.first, category?.second,
            profile[CandidateProfiles.techStack], vacancy[Vacancies.specialization], vacancy[Vacancies.grade],
            vacancy[Vacancies.techStack], forCandidate = false
        )
        val taskNote = signal?.let { s ->
            " Регулярные задания: ${s.submissions}" +
                    (s.averageRating?.let {
                        ", средняя оценка работодателей ${
                            "%.1f".format(java.util.Locale.ROOT, it).replace('.', ',')
                        }"
                    } ?: "") + "."
        }.orEmpty()
        Applicant(
            id = row[VacancyApplications.key].toString(),
            vacancyId = vacancy[Vacancies.key].toString(),
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
