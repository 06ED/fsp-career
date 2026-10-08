package ru.fsp.career.recruitment.domain

import java.util.UUID

internal interface VacancyRepository {
    suspend fun own(subject: String): EmployerVacancies
    suspend fun create(subject: String, input: VacancyRequest): EmployerVacancy
    suspend fun update(subject: String, id: UUID, input: VacancyRequest): EmployerVacancy
    suspend fun changeStatus(subject: String, id: UUID, target: String): EmployerVacancy
    suspend fun applicants(subject: String, id: UUID): Applicants
    suspend fun changeApplication(subject: String, id: UUID, target: String): Applicant
    suspend fun browse(subject: String, filter: VacancyFilter): VacancyCards
    suspend fun apply(subject: String, id: UUID, note: String): CandidateApplication
    suspend fun applications(subject: String): CandidateApplications
    suspend fun withdraw(subject: String, id: UUID)
}

internal interface TaskRepository {
    suspend fun own(subject: String): EmployerShortTasks
    suspend fun create(subject: String, input: ShortTaskRequest): EmployerShortTask
    suspend fun changeActive(subject: String, id: UUID, active: Boolean): EmployerShortTask
    suspend fun submissions(subject: String, id: UUID): EmployerTaskSubmissions
    suspend fun rate(subject: String, id: UUID, input: TaskRatingRequest): EmployerTaskSubmission
    suspend fun current(subject: String): CurrentShortTask
    suspend fun submit(subject: String, input: TaskSubmissionRequest, taskId: UUID): CandidateTaskSubmission
    suspend fun history(subject: String): CandidateTaskSubmissions
}

internal interface InvitationRepository {
    suspend fun send(subject: String, input: InviteRequest): Invitation
    suspend fun list(subject: String): Invitations
    suspend fun change(subject: String, id: UUID, target: String): Invitation
}
