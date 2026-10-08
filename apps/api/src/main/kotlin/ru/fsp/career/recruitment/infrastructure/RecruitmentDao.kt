package ru.fsp.career.recruitment.infrastructure

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID
import java.time.LocalDate

internal class VacancyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<VacancyEntity>(Vacancies)
    var employerId by Vacancies.employerId
    var title by Vacancies.title
    var description by Vacancies.description
    var specialization by Vacancies.specialization
    var grade by Vacancies.grade
    var techStack by Vacancies.techStack
    var salaryMinRub by Vacancies.salaryMinRub
    var salaryMaxRub by Vacancies.salaryMaxRub
    var workFormat by Vacancies.workFormat
    var city by Vacancies.city
    var status by Vacancies.status
    var createdAt by Vacancies.createdAt
    var updatedAt by Vacancies.updatedAt
}

internal class ApplicationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ApplicationEntity>(VacancyApplications)
    var vacancyId by VacancyApplications.vacancyId
    var candidateId by VacancyApplications.candidateId
    var coverNote by VacancyApplications.coverNote
    var status by VacancyApplications.status
    var createdAt by VacancyApplications.createdAt
    var updatedAt by VacancyApplications.updatedAt
}

internal class InvitationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<InvitationEntity>(InvitationsTable)
    var employerId by InvitationsTable.employerId
    var candidateId by InvitationsTable.candidateId
    var description by InvitationsTable.description
    var salaryMinRub by InvitationsTable.salaryMinRub
    var salaryMaxRub by InvitationsTable.salaryMaxRub
    var status by InvitationsTable.status
    var contactMethod by InvitationsTable.contactMethod
    var createdAt by InvitationsTable.createdAt
    var updatedAt by InvitationsTable.updatedAt
}

internal class TaskEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TaskEntity>(ShortTasks)
    var employerId by ShortTasks.employerId
    var title by ShortTasks.title
    var body by ShortTasks.body
    var specialization by ShortTasks.specialization
    var grade by ShortTasks.grade
    var active by ShortTasks.active
    var createdAt by ShortTasks.createdAt
}

internal class SubmissionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<SubmissionEntity>(ShortTaskSubmissions)
    var taskId by ShortTaskSubmissions.taskId
    var candidateId by ShortTaskSubmissions.candidateId
    var kind by ShortTaskSubmissions.kind
    var answer by ShortTaskSubmissions.answer
    var submittedAt by ShortTaskSubmissions.submittedAt
    var rating by ShortTaskSubmissions.rating
    var ratingComment by ShortTaskSubmissions.ratingComment
    var ratedAt by ShortTaskSubmissions.ratedAt
}

internal class TaskOfferEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<TaskOfferEntity>(ShortTaskOffers) {
        fun key(candidate: UUID, period: LocalDate) = CompositeID {
            it[ShortTaskOffers.candidateKey] = candidate
            it[ShortTaskOffers.periodKey] = period
        }
    }
    var taskId by ShortTaskOffers.taskId
    var offeredAt by ShortTaskOffers.offeredAt
}
