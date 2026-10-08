package ru.fsp.career.profiles.infrastructure

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

// DAO entities stay transaction-local; repositories return domain values.
internal class CandidateProfileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CandidateProfileEntity>(CandidateProfiles)
    var fullName by CandidateProfiles.fullName
    var email by CandidateProfiles.email
    var phone by CandidateProfiles.phone
    var consentPublication by CandidateProfiles.consentPublication
    var processingConsent by CandidateProfiles.processingConsent
    var specialization by CandidateProfiles.specialization
    var industry by CandidateProfiles.industry
    var desiredGrade by CandidateProfiles.desiredGrade
    var techStack by CandidateProfiles.techStack
    var experience by CandidateProfiles.experience
    var softSkills by CandidateProfiles.softSkills
    var targetRoles by CandidateProfiles.targetRoles
    var cvHistory by CandidateProfiles.cvHistory
    var updatedAt by CandidateProfiles.updatedAt
    var privacy by CandidateProfiles.privacy
    var fspId by CandidateProfiles.fspId
    var fspLinkedAt by CandidateProfiles.fspLinkedAt
}

internal class CompanyEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CompanyEntity>(EmployerCompanies)
    var name by EmployerCompanies.name
    var description by EmployerCompanies.description
    var website by EmployerCompanies.website
    var industry by EmployerCompanies.industry
    var contactEmail by EmployerCompanies.contactEmail
    var contactPhone by EmployerCompanies.contactPhone
    var contactTelegram by EmployerCompanies.contactTelegram
    var contactNote by EmployerCompanies.contactNote
}

internal class NeedEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NeedEntity>(EmployerNeeds)
    var specialization by EmployerNeeds.specialization
    var industry by EmployerNeeds.industry
    var minGrade by EmployerNeeds.minGrade
    var description by EmployerNeeds.description
    var techStack by EmployerNeeds.techStack
}

internal class ResumeDraftEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ResumeDraftEntity>(ResumeDrafts)
    var candidateId by ResumeDrafts.candidateId
    var parsedFields by ResumeDrafts.parsedFields
    var uploadedAt by ResumeDrafts.uploadedAt
    var confirmedAt by ResumeDrafts.confirmedAt
}
