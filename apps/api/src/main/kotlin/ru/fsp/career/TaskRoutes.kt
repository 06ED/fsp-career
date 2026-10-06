package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

fun Route.taskRoutes(db: Database, audience: String) {
    // ---- Employer: publish tasks, (de)activate, read and rate answers ----
    get("/employers/tasks") {
        val sub = call.role(audience, "employer")
        call.respond(db.tx { EmployerShortTasks(employerTasks(account(sub, "employer"))) })
    }
    post("/employers/tasks") {
        val sub = call.role(audience, "employer")
        val input = validateShortTask(call.receive<ShortTaskRequest>())
        call.respond(HttpStatusCode.Created, db.tx {
            val employer = account(sub, "employer")
            requireVacancyCompanyName(employer)
            val id = UUID.randomUUID()
            ShortTasks.insert {
                it[ShortTasks.id] = id
                it[employerId] = employer
                it[title] = input.title
                it[body] = input.body
                it[specialization] = input.specialization
                it[grade] = input.grade
                it[active] = true
                it[createdAt] = utcNow()
            }
            employerTasks(employer, id).single()
        })
    }
    post("/employers/tasks/{id}/deactivate") { changeTaskActive(db, audience, false) }
    post("/employers/tasks/{id}/activate") { changeTaskActive(db, audience, true) }
    get("/employers/tasks/{id}/submissions") {
        val sub = call.role(audience, "employer")
        val taskId = taskPathId()
        call.respond(db.tx {
            val employer = account(sub, "employer")
            val task = employerTasks(employer, taskId).singleOrNull() ?: missing("Task not found")
            val rows = ShortTaskSubmissions.selectAll().where { ShortTaskSubmissions.taskId eq taskId }
                .orderBy(ShortTaskSubmissions.submittedAt to SortOrder.DESC).toList()
            EmployerTaskSubmissions(task, employerSubmissions(rows))
        })
    }
    put("/employers/task-submissions/{id}/rating") {
        val sub = call.role(audience, "employer")
        val submissionId = taskPathId()
        val input = validateTaskRating(call.receive<TaskRatingRequest>())
        call.respond(db.tx {
            val employer = account(sub, "employer")
            // Ownership goes through the task: only the task's author may rate its answers.
            (ShortTaskSubmissions innerJoin ShortTasks).selectAll().where {
                (ShortTaskSubmissions.id eq submissionId) and (ShortTasks.employerId eq employer)
            }.forUpdate().singleOrNull() ?: missing("Submission not found")
            ShortTaskSubmissions.update({ ShortTaskSubmissions.id eq submissionId }) {
                it[rating] = input.rating
                it[ratingComment] = input.comment
                it[ratedAt] = utcNow()
            }
            employerSubmissions(ShortTaskSubmissions.selectAll()
                .where { ShortTaskSubmissions.id eq submissionId }.toList()).single()
        })
    }

    // ---- Candidate: this week's task, answer it, history with ratings ----
    get("/candidates/me/tasks/current") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx {
            val candidate = account(sub, "candidate")
            currentShortTask(candidate, requireTaskReady(profile(candidate)))
        })
    }
    post("/candidates/me/task-submissions") {
        val sub = call.role(audience, "candidate")
        val input = validateTaskSubmission(call.receive<TaskSubmissionRequest>())
        val taskId = uuid(input.taskId)
        call.respond(HttpStatusCode.Created, db.tx {
            val candidate = account(sub, "candidate") // row lock: offer + submit are serialized per candidate
            val current = currentShortTask(candidate, requireTaskReady(profile(candidate)))
            if (current.state == "answered")
                conflict("This week's task is already answered; the next one is offered at ${current.nextRotationAt}")
            if (current.state != "offered" || current.task?.id != taskId.toString())
                conflict("This task is not your current weekly task")
            val id = UUID.randomUUID()
            ShortTaskSubmissions.insert {
                it[ShortTaskSubmissions.id] = id
                it[ShortTaskSubmissions.taskId] = taskId
                it[candidateId] = candidate
                it[kind] = input.kind
                it[answer] = input.answer
                it[submittedAt] = utcNow()
                it[ratingComment] = ""
            }
            candidateSubmissions(candidate, id).single()
        })
    }
    get("/candidates/me/task-submissions") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx { CandidateTaskSubmissions(candidateSubmissions(account(sub, "candidate"))) })
    }
}

private fun RoutingContext.taskPathId(): UUID = uuid(call.parameters["id"] ?: bad("ID required"))

private fun requireTaskReady(profile: CandidateProfile): CandidateProfile {
    if (!profile.processingConsent) forbidden("Grant processing consent before short tasks")
    if (profile.specialization.isBlank()) bad("Set specialization before short tasks")
    return profile
}

private suspend fun RoutingContext.changeTaskActive(db: Database, audience: String, active: Boolean) {
    val sub = call.role(audience, "employer")
    val taskId = taskPathId()
    call.respond(db.tx {
        val employer = account(sub, "employer")
        val row = ShortTasks.selectAll().where { (ShortTasks.id eq taskId) and (ShortTasks.employerId eq employer) }
            .forUpdate().singleOrNull() ?: missing("Task not found")
        if (row[ShortTasks.active] == active) conflict(if (active) "Task is already active" else "Task is already inactive")
        // Deactivation does not touch answers; offers of the week are re-picked on the next request.
        ShortTasks.update({ ShortTasks.id eq taskId }) { it[ShortTasks.active] = active }
        employerTasks(employer, taskId).single()
    })
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

    val offer = ShortTaskOffers.selectAll().where {
        (ShortTaskOffers.candidateId eq candidate) and (ShortTaskOffers.periodStart eq period)
    }.forUpdate().singleOrNull()
    if (offer != null) {
        val offeredId = offer[ShortTaskOffers.taskId]
        val answered = ShortTaskSubmissions.selectAll().where {
            (ShortTaskSubmissions.taskId eq offeredId) and (ShortTaskSubmissions.candidateId eq candidate)
        }.singleOrNull()
        if (answered != null)
            return slot("answered", offeredTask(offeredId), candidateSubmissions(candidate, answered[ShortTaskSubmissions.id]).single())
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
        (grade == null || grade == effectiveGrade) && row[ShortTasks.id] !in alreadyAnswered
    }.map { it[ShortTasks.id] }
    val pick = pickWeeklyTask(candidate, period, eligible)
    if (pick == null) {
        if (offer != null) ShortTaskOffers.deleteWhere {
            (ShortTaskOffers.candidateId eq candidate) and (ShortTaskOffers.periodStart eq period)
        }
        return slot("none")
    }
    if (offer == null) {
        ShortTaskOffers.insert {
            it[candidateId] = candidate
            it[periodStart] = period
            it[taskId] = pick
            it[offeredAt] = utcNow()
        }
    } else {
        ShortTaskOffers.update({
            (ShortTaskOffers.candidateId eq candidate) and (ShortTaskOffers.periodStart eq period)
        }) {
            it[taskId] = pick
            it[offeredAt] = utcNow()
        }
    }
    return slot("offered", offeredTask(pick))
}

private fun Transaction.offeredTask(taskId: UUID): OfferedTask {
    val row = ShortTasks.selectAll().where { ShortTasks.id eq taskId }.single()
    val company = EmployerCompanies.selectAll().where { EmployerCompanies.accountId eq row[ShortTasks.employerId] }
        .single()[EmployerCompanies.name]
    return OfferedTask(row[ShortTasks.id].toString(), row[ShortTasks.title], row[ShortTasks.body],
        row[ShortTasks.specialization], row[ShortTasks.grade], company)
}

private fun Transaction.employerTasks(employer: UUID, onlyId: UUID? = null): List<EmployerShortTask> {
    var condition: Op<Boolean> = ShortTasks.employerId eq employer
    if (onlyId != null) condition = condition and (ShortTasks.id eq onlyId)
    val rows = ShortTasks.selectAll().where(condition).orderBy(ShortTasks.createdAt to SortOrder.DESC).toList()
    if (rows.isEmpty()) return emptyList()
    val submissions = ShortTaskSubmissions.select(ShortTaskSubmissions.taskId, ShortTaskSubmissions.rating)
        .where { ShortTaskSubmissions.taskId inList rows.map { it[ShortTasks.id] } }
        .groupBy({ it[ShortTaskSubmissions.taskId] }, { it[ShortTaskSubmissions.rating] })
    return rows.map { row ->
        val ratings = submissions[row[ShortTasks.id]].orEmpty()
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

private fun Transaction.candidateSubmissions(candidate: UUID, onlyId: UUID? = null): List<CandidateTaskSubmission> {
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
            candidateName = profile[CandidateProfiles.fullName],
            specialization = profile[CandidateProfiles.specialization],
            assessedGrade = categories[candidateId]?.first,
            score = categories[candidateId]?.second,
            kind = row[ShortTaskSubmissions.kind],
            answer = row[ShortTaskSubmissions.answer],
            submittedAt = row[ShortTaskSubmissions.submittedAt].toInstant().toString(),
            rating = row[ShortTaskSubmissions.rating],
            ratingComment = row[ShortTaskSubmissions.ratingComment],
            ratedAt = row[ShortTaskSubmissions.ratedAt]?.toInstant()?.toString(),
        )
    }
}
