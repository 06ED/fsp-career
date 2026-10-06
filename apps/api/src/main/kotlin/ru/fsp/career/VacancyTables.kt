package ru.fsp.career

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

// Exposed mappings for V5__vacancies_tasks.sql (Flyway owns the DDL).

internal object Vacancies : Table("vacancies") {
    val id = uuid("id")
    val employerId = reference("employer_id", EmployerCompanies.accountId)
    val title = text("title")
    val description = text("description")
    val specialization = text("specialization")
    val grade = text("grade")
    val techStack = jsonb<List<String>>("tech_stack", Json.Default)
    val salaryMinRub = integer("salary_min_rub")
    val salaryMaxRub = integer("salary_max_rub")
    val workFormat = text("work_format")
    val city = text("city")
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object VacancyApplications : Table("vacancy_applications") {
    val id = uuid("id")
    val vacancyId = reference("vacancy_id", Vacancies.id)
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val coverNote = text("cover_note")
    val status = text("status")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ShortTasks : Table("short_tasks") {
    val id = uuid("id")
    val employerId = reference("employer_id", EmployerCompanies.accountId)
    val title = text("title")
    val body = text("body")
    val specialization = text("specialization")
    val grade = text("grade").nullable()
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ShortTaskSubmissions : Table("short_task_submissions") {
    val id = uuid("id")
    val taskId = reference("task_id", ShortTasks.id)
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val kind = text("kind")
    val answer = text("answer")
    val submittedAt = timestampWithTimeZone("submitted_at")
    val rating = integer("rating").nullable()
    val ratingComment = text("rating_comment")
    val ratedAt = timestampWithTimeZone("rated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object ShortTaskOffers : Table("short_task_offers") {
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val periodStart = date("period_start")
    val taskId = reference("task_id", ShortTasks.id)
    val offeredAt = timestampWithTimeZone("offered_at")
    override val primaryKey = PrimaryKey(candidateId, periodStart)
}
