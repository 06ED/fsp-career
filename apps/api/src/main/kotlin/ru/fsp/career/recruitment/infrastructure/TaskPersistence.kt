package ru.fsp.career.recruitment.infrastructure

import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.shared.domain.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// Regular short tasks (spec 2: "система периодически предлагает кандидату задачу от работодателя —
// решить её или предложить подход"). Employers publish short tasks per specialization; every week
// the system offers each candidate exactly one of them (see pickWeeklyTask); employers rate answers.
// The answers are a fresh activity signal exposed through taskSignals()/lastTaskActivity().

private fun utcNow(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

/** Aggregated short-task activity of a candidate; intended as an additional ranking/freshness signal. */
internal data class TaskSignal(
    val submissions: Int,
    val rated: Int,
    val averageRating: Double?,
    val lastActivityAt: Instant,
)

/** Latest short-task submission per candidate (only candidates with at least one answer appear). */
internal fun Transaction.lastTaskActivity(candidateIds: Collection<UUID>): Map<UUID, Instant> {
    if (candidateIds.isEmpty()) return emptyMap()
    val latest = ShortTaskSubmissions.submittedAt.max()
    return ShortTaskSubmissions.select(ShortTaskSubmissions.candidateId, latest)
        .where { ShortTaskSubmissions.candidateId inList candidateIds.distinct() }
        .groupBy(ShortTaskSubmissions.candidateId)
        .associate { it[ShortTaskSubmissions.candidateId] to it[latest]!!.toInstant() }
}

/** Count, employer ratings and last activity of short-task answers per candidate. */
internal fun Transaction.taskSignals(candidateIds: Collection<UUID>): Map<UUID, TaskSignal> {
    if (candidateIds.isEmpty()) return emptyMap()
    return ShortTaskSubmissions.selectAll()
        .where { ShortTaskSubmissions.candidateId inList candidateIds.distinct() }
        .groupBy { it[ShortTaskSubmissions.candidateId] }
        .mapValues { (_, rows) ->
            val ratings = rows.mapNotNull { it[ShortTaskSubmissions.rating] }
            TaskSignal(
                submissions = rows.size,
                rated = ratings.size,
                averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
                lastActivityAt = rows.maxOf { it[ShortTaskSubmissions.submittedAt].toInstant() },
            )
        }
}

internal fun Transaction.createTask(employer: UUID, input: ShortTaskRequest): EmployerShortTask {
    requireVacancyCompanyName(employer)
    val id = UUID.randomUUID()
    TaskEntity.new(id) {
        employerId = employer; title = input.title; body = input.body
        specialization = input.specialization; grade = input.grade; active = true; createdAt = utcNow()
    }.flush()
    return employerTasks(employer, id).single()
}

internal fun Transaction.changeTaskActive(employer: UUID, taskId: UUID, active: Boolean): EmployerShortTask {
    val row =
        TaskEntity.find { (ShortTasks.key eq taskId) and (ShortTasks.employerId eq employer) }.forUpdate()
            .singleOrNull() ?: missing("Task not found")
    if (row.active == active) conflict(if (active) "Task is already active" else "Task is already inactive")
    row.apply { this.active = active; flush() }
    return employerTasks(employer, taskId).single()
}

internal fun Transaction.taskSubmissions(employer: UUID, taskId: UUID): EmployerTaskSubmissions {
    val task = employerTasks(employer, taskId).singleOrNull() ?: missing("Task not found")
    val rows = ShortTaskSubmissions.selectAll().where { ShortTaskSubmissions.taskId eq taskId }
        .orderBy(ShortTaskSubmissions.submittedAt to SortOrder.DESC).toList()
    return EmployerTaskSubmissions(task, employerSubmissions(rows))
}

internal fun Transaction.rateSubmission(employer: UUID, id: UUID, input: TaskRatingRequest): EmployerTaskSubmission {
    val row = (ShortTaskSubmissions innerJoin ShortTasks).selectAll().where {
        (ShortTaskSubmissions.key eq id) and (ShortTasks.employerId eq employer)
    }.forUpdate().singleOrNull() ?: missing("Submission not found")
    val submission = SubmissionEntity.wrapRow(row)
    submission.apply { rating = input.rating; ratingComment = input.comment; ratedAt = utcNow(); flush() }
    return employerSubmissions(ShortTaskSubmissions.selectAll().where { ShortTaskSubmissions.key eq id }
        .toList()).single()
}

internal fun Transaction.submitTask(
    candidate: UUID,
    input: TaskSubmissionRequest,
    taskId: UUID
): CandidateTaskSubmission {
    val current = currentShortTask(candidate, requireTaskReady(profile(candidate)))
    if (current.state == "answered")
        conflict("This week's task is already answered; the next one is offered at ${current.nextRotationAt}")
    if (current.state != "offered" || current.task?.id != taskId.toString())
        conflict("This task is not your current weekly task")

    val id = UUID.randomUUID()
    SubmissionEntity.new(id) {
        this.taskId = taskId; candidateId = candidate
        kind = input.kind; answer = input.answer; submittedAt = utcNow(); ratingComment = ""
    }.flush()

    return candidateSubmissions(candidate, id).single()
}

/**
 * Returns (and, on the first request of the week, assigns) the candidate's weekly task.
 *
 * Period = ISO week starting Monday 00:00 UTC. Eligible pool = active tasks of the candidate's
 * specialization whose grade is empty or equals the candidate's grade (assessed grade, or the
 * desired grade before the first assessment), minus tasks the candidate already answered.
 * The pick is deterministic (pickWeeklyTask) and persisted in short_task_offers so it stays stable
 * for the whole week. It is re-picked only if the task was deactivated or the candidate changed
 * specialization. After answering, the slot is "answered" until the next Monday.
 */
internal fun Transaction.currentShortTask(
    candidate: UUID,
    me: CandidateProfile,
    today: LocalDate = LocalDate.now(ZoneOffset.UTC),
): CurrentShortTask {
    val period = taskPeriodStart(today)
    val nextRotation = period.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString()
    val lastActivity = lastTaskActivity(listOf(candidate))[candidate]?.toString()
    fun slot(state: String, task: OfferedTask? = null, submission: CandidateTaskSubmission? = null) =
        CurrentShortTask(period.toString(), nextRotation, state, task, submission, lastActivity)

    val offer = TaskOfferEntity.find {
        (ShortTaskOffers.candidateId eq candidate) and (ShortTaskOffers.periodStart eq period)
    }.forUpdate().singleOrNull()
    if (offer != null) {
        val offeredId = offer.taskId
        val answered = ShortTaskSubmissions.selectAll().where {
            (ShortTaskSubmissions.taskId eq offeredId) and (ShortTaskSubmissions.candidateId eq candidate)
        }.singleOrNull()
        if (answered != null)
            return slot(
                "answered",
                offeredTask(offeredId),
                candidateSubmissions(candidate, answered[ShortTaskSubmissions.key]).single()
            )
        val task = ShortTasks.selectAll().where { ShortTasks.id eq offeredId }.single()
        if (task[ShortTasks.active] && task[ShortTasks.specialization] == me.specialization)
            return slot("offered", offeredTask(offeredId))
    }

    val alreadyAnswered = ShortTaskSubmissions.select(ShortTaskSubmissions.taskId)
        .where { ShortTaskSubmissions.candidateId eq candidate }
        .map { it[ShortTaskSubmissions.taskId] }.toSet()
    val effectiveGrade = me.grade ?: me.desiredGrade
    val eligible = ShortTasks.selectAll().where {
        (ShortTasks.active eq true) and (ShortTasks.specialization eq me.specialization)
    }.filter { row ->
        val grade = row[ShortTasks.grade]
        (grade == null || grade == effectiveGrade) && row[ShortTasks.key] !in alreadyAnswered
    }.map { it[ShortTasks.key] }
    val pick = pickWeeklyTask(candidate, period, eligible)
    if (pick == null) {
        offer?.delete()
        return slot("none")
    }
    if (offer == null) {
        TaskOfferEntity.new(TaskOfferEntity.key(candidate, period)) {
            taskId = pick
            offeredAt = utcNow()
        }.flush()
    } else {
        offer.apply { taskId = pick; offeredAt = utcNow(); flush() }
    }
    return slot("offered", offeredTask(pick))
}

private fun Transaction.offeredTask(taskId: UUID): OfferedTask {
    val row = ShortTasks.selectAll().where { ShortTasks.id eq taskId }.single()
    val company = EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq row[ShortTasks.employerId] }
        .single()[EmployerCompanies.name]
    return OfferedTask(
        row[ShortTasks.id].toString(), row[ShortTasks.title], row[ShortTasks.body],
        row[ShortTasks.specialization], row[ShortTasks.grade], company
    )
}

internal fun Transaction.employerTasks(employer: UUID, onlyId: UUID? = null): List<EmployerShortTask> {
    var condition: Op<Boolean> = ShortTasks.employerId eq employer
    if (onlyId != null) condition = condition and (ShortTasks.id eq onlyId)
    val rows = ShortTasks.selectAll().where(condition).orderBy(ShortTasks.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val submissions = ShortTaskSubmissions.select(ShortTaskSubmissions.taskId, ShortTaskSubmissions.rating)
        .where { ShortTaskSubmissions.taskId inList rows.map { it[ShortTasks.key] } }
        .groupBy({ it[ShortTaskSubmissions.taskId] }, { it[ShortTaskSubmissions.rating] })
    return rows.map { row ->
        val ratings = submissions[row[ShortTasks.key]].orEmpty()
        EmployerShortTask(
            id = row[ShortTasks.id].toString(),
            title = row[ShortTasks.title],
            body = row[ShortTasks.body],
            specialization = row[ShortTasks.specialization],
            grade = row[ShortTasks.grade],
            active = row[ShortTasks.active],
            createdAt = row[ShortTasks.createdAt].toInstant().toString(),
            submissions = ratings.size,
            unrated = ratings.count { it == null },
        )
    }
}

internal fun Transaction.candidateSubmissions(candidate: UUID, onlyId: UUID? = null): List<CandidateTaskSubmission> {
    var condition: Op<Boolean> = ShortTaskSubmissions.candidateId eq candidate
    if (onlyId != null) condition = condition and (ShortTaskSubmissions.id eq onlyId)
    val rows = (ShortTaskSubmissions innerJoin ShortTasks).selectAll().where(condition)
        .orderBy(ShortTaskSubmissions.submittedAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList rows.map { it[ShortTasks.employerId] }.distinct() }
        .associate { it[EmployerCompanies.accountId] to it[EmployerCompanies.name] }
    return rows.map { row ->
        CandidateTaskSubmission(
            id = row[ShortTaskSubmissions.id].toString(),
            taskId = row[ShortTasks.id].toString(),
            taskTitle = row[ShortTasks.title],
            companyName = companies[row[ShortTasks.employerId]].orEmpty(),
            kind = row[ShortTaskSubmissions.kind],
            answer = row[ShortTaskSubmissions.answer],
            submittedAt = row[ShortTaskSubmissions.submittedAt].toInstant().toString(),
            rating = row[ShortTaskSubmissions.rating],
            ratingComment = row[ShortTaskSubmissions.ratingComment],
            ratedAt = row[ShortTaskSubmissions.ratedAt]?.toInstant()?.toString(),
        )
    }
}

/** Employer view of answers: name + confirmed category, never e-mail/phone (no application/acceptance here). */
private fun Transaction.employerSubmissions(rows: List<ResultRow>): List<EmployerTaskSubmission> {
    if (rows.isEmpty()) return emptyList()
    val profiles = CandidateProfiles.selectAll()
        .where { CandidateProfiles.accountId inList rows.map { it[ShortTaskSubmissions.candidateId] }.distinct() }
        .associateBy { it[CandidateProfiles.accountId] }
    val categories = applicantCategories(profiles.values)
    return rows.map { row ->
        val candidateId = row[ShortTaskSubmissions.candidateId]
        val profile = profiles.getValue(candidateId)
        EmployerTaskSubmission(
            id = row[ShortTaskSubmissions.id].toString(),
            taskId = row[ShortTaskSubmissions.taskId].toString(),
            candidateId = candidateId.toString(),
            candidateName = if (profile[CandidateProfiles.privacy].fullName)
                employerText(profile[CandidateProfiles.fullName], profile[CandidateProfiles.email], profile[CandidateProfiles.phone])
                else anonymousName(candidateId),
            specialization = employerText(profile[CandidateProfiles.specialization], profile[CandidateProfiles.email], profile[CandidateProfiles.phone]),
            assessedGrade = categories[candidateId]?.first,
            score = categories[candidateId]?.second,
            kind = row[ShortTaskSubmissions.kind],
            answer = employerText(row[ShortTaskSubmissions.answer], profile[CandidateProfiles.email], profile[CandidateProfiles.phone],
                profile[CandidateProfiles.fullName].takeUnless { profile[CandidateProfiles.privacy].fullName }.orEmpty()),
            submittedAt = row[ShortTaskSubmissions.submittedAt].toInstant().toString(),
            rating = row[ShortTaskSubmissions.rating],
            ratingComment = row[ShortTaskSubmissions.ratingComment],
            ratedAt = row[ShortTaskSubmissions.ratedAt]?.toInstant()?.toString(),
        )
    }
}
