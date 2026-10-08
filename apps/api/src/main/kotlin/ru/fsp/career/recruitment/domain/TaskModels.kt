package ru.fsp.career.recruitment.domain

import kotlinx.serialization.Serializable

// DTOs for regular short tasks (TaskRoutes.kt).

@Serializable
data class ShortTaskRequest(
    val title: String,
    val body: String,
    val specialization: String,
    val grade: String? = null,
)

@Serializable
data class EmployerShortTask(
    val id: String,
    val title: String,
    val body: String,
    val specialization: String,
    val grade: String? = null,
    val active: Boolean,
    val createdAt: String,
    val submissions: Int,
    val unrated: Int,
)

@Serializable
data class EmployerShortTasks(val tasks: List<EmployerShortTask>)

/** Task as offered to a candidate: the employer is named, internal ids of the employer are not. */
@Serializable
data class OfferedTask(
    val id: String,
    val title: String,
    val body: String,
    val specialization: String,
    val grade: String? = null,
    val companyName: String,
)

/**
 * The candidate's weekly slot. state: "offered" (answer expected), "answered" (this week's task
 * is done) or "none" (no eligible active tasks for the candidate's specialization right now).
 */
@Serializable
data class CurrentShortTask(
    val periodStart: String,
    val nextRotationAt: String,
    val state: String,
    val task: OfferedTask? = null,
    val submission: CandidateTaskSubmission? = null,
    val lastActivityAt: String? = null,
)

@Serializable
data class TaskSubmissionRequest(val taskId: String, val kind: String, val answer: String)

@Serializable
data class CandidateTaskSubmission(
    val id: String,
    val taskId: String,
    val taskTitle: String,
    val companyName: String,
    val kind: String,
    val answer: String,
    val submittedAt: String,
    val rating: Int? = null,
    val ratingComment: String = "",
    val ratedAt: String? = null,
)

@Serializable
data class CandidateTaskSubmissions(val submissions: List<CandidateTaskSubmission>)

/** Submission as the task's employer sees it: category and answer, never contacts. */
@Serializable
data class EmployerTaskSubmission(
    val id: String,
    val taskId: String,
    val candidateId: String,
    val candidateName: String,
    val specialization: String,
    val assessedGrade: String? = null,
    val score: Int? = null,
    val kind: String,
    val answer: String,
    val submittedAt: String,
    val rating: Int? = null,
    val ratingComment: String = "",
    val ratedAt: String? = null,
)

@Serializable
data class EmployerTaskSubmissions(val task: EmployerShortTask, val submissions: List<EmployerTaskSubmission>)

@Serializable
data class TaskRatingRequest(val rating: Int, val comment: String = "")
