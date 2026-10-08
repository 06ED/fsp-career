package ru.fsp.career.recruitment.infrastructure

import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.profiles.infrastructure.profile
import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.forbidden
import ru.fsp.career.shared.infrastructure.Database
import java.util.UUID

internal class ExposedVacancyRepository(private val db: Database) : VacancyRepository {
    override suspend fun own(subject: String) = db.tx { EmployerVacancies(employerVacancies(account(subject, "employer"))) }
    override suspend fun create(subject: String, input: VacancyRequest) = db.tx { createVacancy(account(subject, "employer"), input) }
    override suspend fun update(subject: String, id: UUID, input: VacancyRequest) = db.tx { updateVacancy(account(subject, "employer"), id, input) }
    override suspend fun changeStatus(subject: String, id: UUID, target: String) = db.tx { changeVacancyStatus(account(subject, "employer"), id, target) }
    override suspend fun applicants(subject: String, id: UUID) = db.tx { vacancyApplicants(account(subject, "employer"), id) }
    override suspend fun changeApplication(subject: String, id: UUID, target: String) = db.tx { changeApplicationStatus(account(subject, "employer"), id, target) }
    override suspend fun browse(subject: String, filter: VacancyFilter) = db.tx {
        val candidate = account(subject, "candidate")
        VacancyCards(vacancyCards(candidate, profile(candidate), filter))
    }
    override suspend fun apply(subject: String, id: UUID, note: String) = db.tx { applyToVacancy(account(subject, "candidate"), id, note) }
    override suspend fun applications(subject: String) = db.tx { CandidateApplications(candidateApplications(account(subject, "candidate"))) }
    override suspend fun withdraw(subject: String, id: UUID) = db.tx { withdrawApplication(account(subject, "candidate"), id) }
}

internal class ExposedTaskRepository(private val db: Database) : TaskRepository {
    override suspend fun own(subject: String) = db.tx { EmployerShortTasks(employerTasks(account(subject, "employer"))) }
    override suspend fun create(subject: String, input: ShortTaskRequest) = db.tx { createTask(account(subject, "employer"), input) }
    override suspend fun changeActive(subject: String, id: UUID, active: Boolean) = db.tx { changeTaskActive(account(subject, "employer"), id, active) }
    override suspend fun submissions(subject: String, id: UUID) = db.tx { taskSubmissions(account(subject, "employer"), id) }
    override suspend fun rate(subject: String, id: UUID, input: TaskRatingRequest) = db.tx { rateSubmission(account(subject, "employer"), id, input) }
    override suspend fun current(subject: String) = db.tx {
        val id = account(subject, "candidate")
        currentShortTask(id, requireTaskReady(profile(id)))
    }
    override suspend fun submit(subject: String, input: TaskSubmissionRequest, taskId: UUID) = db.tx { submitTask(account(subject, "candidate"), input, taskId) }
    override suspend fun history(subject: String) = db.tx { CandidateTaskSubmissions(candidateSubmissions(account(subject, "candidate"))) }
}

internal class ExposedInvitationRepository(private val db: Database) : InvitationRepository {
    override suspend fun send(subject: String, input: InviteRequest) = db.tx { createInvitation(account(subject, "employer"), input) }
    override suspend fun list(subject: String) = db.tx {
        val (id, role) = boundRole(subject) ?: forbidden("Choose a role first: POST /api/me/role")
        Invitations(invitationsFor(role, id))
    }
    override suspend fun change(subject: String, id: UUID, target: String) = db.tx { changeInvitation(account(subject, "candidate"), id, target) }
}
