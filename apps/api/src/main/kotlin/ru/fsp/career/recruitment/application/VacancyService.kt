package ru.fsp.career.recruitment.application

import ru.fsp.career.recruitment.domain.*
import java.util.UUID

internal class VacancyService(private val vacancies: VacancyRepository) {
    suspend fun own(subject: String) =
        vacancies.own(subject)

    suspend fun create(subject: String, request: VacancyRequest): EmployerVacancy {
        val input = validateVacancy(request)
        return vacancies.create(subject, input)
    }

    suspend fun update(subject: String, id: UUID, request: VacancyRequest): EmployerVacancy {
        val input = validateVacancy(request)
        return vacancies.update(subject, id, input)
    }

    suspend fun changeStatus(subject: String, id: UUID, target: String) =
        vacancies.changeStatus(subject, id, target)

    suspend fun applicants(subject: String, id: UUID) =
        vacancies.applicants(subject, id)

    suspend fun changeApplication(subject: String, id: UUID, request: ApplicationStatusRequest): Applicant {
        val target = applicationStatus(request.status)
        return vacancies.changeApplication(subject, id, target)
    }

    suspend fun browse(subject: String, filter: VacancyFilter) = vacancies.browse(subject, filter)

    suspend fun apply(subject: String, id: UUID, request: ApplyRequest): CandidateApplication {
        val note = coverNote(request.coverNote)
        return vacancies.apply(subject, id, note)
    }

    suspend fun applications(subject: String) =
        vacancies.applications(subject)

    suspend fun withdraw(subject: String, id: UUID) =
        vacancies.withdraw(subject, id)
}
