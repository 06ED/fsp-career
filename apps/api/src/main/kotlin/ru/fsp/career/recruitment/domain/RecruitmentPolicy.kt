package ru.fsp.career.recruitment.domain

import ru.fsp.career.profiles.domain.CandidateProfile
import ru.fsp.career.shared.domain.*

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

// Pure, DB-free rules for vacancies, applications and short tasks (unit-tested in VacancyRulesTest).

/** Same dictionary as candidate profiles and the assessment bank (web: specializations.ts). */
internal val VACANCY_SPECIALIZATIONS = listOf("backend", "frontend", "data", "product", "design")
internal val VACANCY_WORK_FORMATS = listOf("office", "remote", "hybrid")
internal val VACANCY_APPLICATION_STATUSES = listOf("sent", "viewed", "accepted", "rejected")
internal val TASK_SUBMISSION_KINDS = listOf("solution", "approach")

/** Upper sanity bound for salaries so typos like an extra zero do not publish absurd offers. */
internal const val VACANCY_MAX_SALARY_RUB = 10_000_000

private val gradeLabels = mapOf("junior" to "Junior", "middle" to "Middle", "senior" to "Senior")

// specializationLabel() is shared with matching.
private fun gradeLabel(value: String): String = gradeLabels[value] ?: value

internal fun vacancySpecialization(value: String): String =
    value.trim().lowercase().takeIf { it in VACANCY_SPECIALIZATIONS }
        ?: bad("specialization must be one of ${VACANCY_SPECIALIZATIONS.joinToString()}")

internal fun vacancyWorkFormat(value: String): String =
    value.trim().lowercase().takeIf { it in VACANCY_WORK_FORMATS }
        ?: bad("workFormat must be office, remote or hybrid")

/** Trims, de-duplicates (case-insensitive) and bounds a skill list. */
internal fun vacancyStack(items: List<String>, maxItems: Int = 20): List<String> {
    val cleaned = items.map { it.trim() }
    if (cleaned.size > maxItems || cleaned.any { it.isEmpty() || it.length > 60 }) bad("techStack must contain up to $maxItems items of 1..60 characters")
    return cleaned.distinctBy { it.lowercase() }
}

/** Validates and normalizes employer input. The RUB range is mandatory: min > 0, max >= min. */
internal fun validateVacancy(request: VacancyRequest): VacancyRequest {
    val title = requireText(request.title, "Title", 200)
    val description = requireText(request.description, "Description", 6000)
    val city = request.city.trim()

    if (city.length > 100) bad("City must contain at most 100 characters")

    val format = vacancyWorkFormat(request.workFormat)
    if (format != "remote" && city.isEmpty()) bad("City is required for office and hybrid vacancies")

    if (request.salaryMinRub <= 0 || request.salaryMaxRub < request.salaryMinRub)
        bad("Salary range in RUB must be positive and min <= max")
    if (request.salaryMaxRub > VACANCY_MAX_SALARY_RUB) bad("Salary must not exceed $VACANCY_MAX_SALARY_RUB RUB per month")

    return VacancyRequest(
        title = title,
        description = description,
        specialization = vacancySpecialization(request.specialization),
        grade = grade(request.grade.trim().lowercase()),
        techStack = vacancyStack(request.techStack),
        salaryMinRub = request.salaryMinRub,
        salaryMaxRub = request.salaryMaxRub,
        workFormat = format,
        city = city,
    )
}

/**
 * Employer-side application transitions. accepted/rejected are terminal so the candidate's
 * view stays truthful; "viewed" is only an intermediate acknowledgement.
 */
internal fun applicationTransitionAllowed(from: String, to: String): Boolean = when (to) {
    "viewed" -> from == "sent"
    "accepted", "rejected" -> from == "sent" || from == "viewed"
    else -> false
}

/** Candidates may withdraw only pending applications; decided ones stay as history (no re-apply spam). */
internal fun applicationWithdrawable(status: String): Boolean = status == "sent" || status == "viewed"

internal fun applicationStatus(value: String): String =
    value.trim().lowercase().takeIf { it in listOf("viewed", "accepted", "rejected") }
        ?: bad("status must be viewed, accepted or rejected")

internal fun coverNote(value: String): String = value.trim().also {
    if (it.length > 2000) bad("Cover note must contain at most 2000 characters")
}

internal fun vacancyTransitionAllowed(from: String, to: String): Boolean =
    (from == "open" && to == "closed") || (from == "closed" && to == "open")

/**
 * Preconditions for a self-initiated application. A completed assessment is required: the
 * employer must see a category confirmed by testing, not a self-described résumé (spec 2).
 * At least one contact is required because applying is what discloses contacts to the employer.
 */
internal fun requireApplicationReady(profile: CandidateProfile) {
    if (!profile.processingConsent) forbidden("Grant processing consent before applying")
    if (profile.fullName.isBlank() || profile.specialization.isBlank())
        bad("Fill in full name and specialization before applying")
    if (profile.email.isBlank() && profile.phone.isBlank())
        bad("Add a phone number or verify email so the employer can contact you")
    if (profile.grade == null) conflict("Complete the skills assessment before applying")
}

internal data class VacancyFilter(
    val specialization: String? = null,
    val grade: String? = null,
    val stack: List<String> = emptyList(),
    val salaryFrom: Int? = null,
    val workFormat: String? = null,
    val query: String? = null,
)

/** Parses candidate search parameters; every invalid value is a 400, never silently ignored. */
internal fun vacancyFilter(param: (String) -> String?): VacancyFilter {
    fun value(name: String) = param(name)?.trim()?.takeIf { it.isNotEmpty() }
    val stack = value("techStack")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (stack.size > 10 || stack.any { it.length > 60 }) bad("Invalid techStack filter")
    val salary = value("salaryFrom")?.let { raw ->
        raw.toIntOrNull()?.takeIf { it >= 0 } ?: bad("salaryFrom must be a non-negative integer")
    }
    val query = value("q")
    if (query != null && query.length > 100) bad("Query too long")
    return VacancyFilter(
        specialization = value("specialization")?.let(::vacancySpecialization),
        grade = value("grade")?.lowercase()?.let(::grade),
        stack = stack,
        salaryFrom = salary,
        workFormat = value("workFormat")?.let(::vacancyWorkFormat),
        query = query,
    )
}

/**
 * Filters applied in memory after the indexed SQL predicates (status/specialization/grade/
 * format/salary): all requested skills must be present (case-insensitive, same rule as
 * /api/matches), and the free-text query must occur in title, description, company, city or stack.
 */
internal fun VacancyFilter.acceptsText(stack: List<String>, searchable: List<String>): Boolean {
    val skillsOk = this.stack.all { wanted -> stack.any { it.equals(wanted, ignoreCase = true) } }
    val q = query?.lowercase() ?: return skillsOk
    return skillsOk && (searchable + stack).any { it.lowercase().contains(q) }
}

internal fun vacancyStackOverlap(required: List<String>, owned: List<String>): List<String> =
    required.filter { skill -> owned.any { it.equals(skill, ignoreCase = true) } }

internal data class VacancyFit(val categoryMatch: Boolean, val overlap: List<String>, val text: String)

/**
 * Explains how a candidate's *confirmed* category (specialization + assessed grade) relates to a
 * vacancy. categoryMatch = same specialization and assessed grade >= required grade. Self-reported
 * skills only produce the overlap note; they never make a category "match".
 * [forCandidate] switches the wording between "ваша категория" and "категория кандидата".
 */
internal fun vacancyFit(
    candidateSpecialization: String,
    assessedGrade: String?,
    score: Int?,
    candidateStack: List<String>,
    vacancySpecialization: String,
    vacancyGrade: String,
    vacancyStack: List<String>,
    forCandidate: Boolean,
): VacancyFit {
    val who = if (forCandidate) "Ваша категория" else "Категория кандидата"
    val required = gradeLabel(vacancyGrade)
    val sameSpecialization = candidateSpecialization == vacancySpecialization
    val gradeOk = assessedGrade != null && gradeRank(assessedGrade) >= gradeRank(vacancyGrade)
    val category = assessedGrade?.let {
        "${specializationLabel(candidateSpecialization)} · ${gradeLabel(it)}" + (score?.let { s -> " ($s/100)" } ?: "")
    }
    val head = when {
        category == null ->
            if (forCandidate) "Категория не подтверждена: пройдите оценку навыков."
            else "Оценка навыков по текущему направлению не пройдена."

        !sameSpecialization ->
            "$who — $category; вакансия для направления «${specializationLabel(vacancySpecialization)}»."

        !gradeOk -> "$who — $category; требуется $required."
        else -> "$who $category подтверждена тестом и соответствует требованию $required."
    }
    val overlap = vacancyStackOverlap(vacancyStack, candidateStack)
    val tail = if (vacancyStack.isEmpty()) "" else
        " Совпадение стека: ${overlap.size} из ${vacancyStack.size}" +
                (if (overlap.isEmpty()) "." else " (${overlap.joinToString()}).")
    return VacancyFit(sameSpecialization && gradeOk, overlap, head + tail)
}

// ---- Regular short tasks -------------------------------------------------------------

internal fun requireTaskReady(profile: CandidateProfile): CandidateProfile {
    if (!profile.processingConsent) forbidden("Grant processing consent before short tasks")
    if (profile.specialization.isBlank()) bad("Set specialization before short tasks")
    return profile
}

internal fun validateShortTask(request: ShortTaskRequest): ShortTaskRequest = ShortTaskRequest(
    title = requireText(request.title, "Title", 200),
    body = requireText(request.body, "Task text", 4000),
    specialization = vacancySpecialization(request.specialization),
    grade = request.grade?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(::grade),
)

internal fun validateTaskSubmission(request: TaskSubmissionRequest): TaskSubmissionRequest {
    val kind = request.kind.trim().lowercase().takeIf { it in TASK_SUBMISSION_KINDS }
        ?: bad("kind must be solution or approach")
    return TaskSubmissionRequest(request.taskId.trim(), kind, requireText(request.answer, "Answer", 8000))
}

internal fun validateTaskRating(request: TaskRatingRequest): TaskRatingRequest {
    if (request.rating !in 1..5) bad("rating must be between 1 and 5")
    val comment = request.comment.trim()
    if (comment.length > 1000) bad("Comment must contain at most 1000 characters")
    return TaskRatingRequest(request.rating, comment)
}

/** Weekly rotation period: tasks rotate every Monday 00:00 UTC. */
internal fun taskPeriodStart(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Picks this period's task for a candidate, deterministically.
 *
 * index = SHA-256("<candidateId>|<periodStart>") as unsigned 64-bit mod |eligible|, over the
 * eligible ids sorted by UUID. Properties:
 *  - reproducible: the same candidate sees the same task all week (also persisted in
 *    short_task_offers so tasks added mid-week do not reshuffle the offer);
 *  - spread: candidates of one specialization get different tasks in the same week, so a
 *    single leaked answer does not cover the cohort and every employer's task gets answers;
 *  - rotation: the hash changes every week, and answered tasks leave the eligible pool.
 */
internal fun pickWeeklyTask(candidateId: UUID, periodStart: LocalDate, eligible: Collection<UUID>): UUID? {
    if (eligible.isEmpty()) return null
    val ordered = eligible.distinct().sortedBy { it.toString() }
    val digest = MessageDigest.getInstance("SHA-256").digest("$candidateId|$periodStart".toByteArray())
    val bucket = java.lang.Long.remainderUnsigned(ByteBuffer.wrap(digest, 0, 8).long, ordered.size.toLong())
    return ordered[bucket.toInt()]
}
