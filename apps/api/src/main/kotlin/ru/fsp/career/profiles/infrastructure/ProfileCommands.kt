package ru.fsp.career.profiles.infrastructure

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.shared.domain.forbidden
import ru.fsp.career.achievements.infrastructure.Achievements
import ru.fsp.career.assessment.infrastructure.AssessmentAttempts
import ru.fsp.career.recruitment.infrastructure.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun Transaction.saveProfile(id: UUID, p: CandidateProfile): CandidateProfile {
    CandidateProfileEntity[id].apply {
        fullName = p.fullName; email = p.email; phone = p.phone
        processingConsent = true; consentPublication = p.consentPublication
        specialization = p.specialization; industry = p.industry; desiredGrade = p.desiredGrade
        techStack = p.techStack; experience = p.experience; softSkills = p.softSkills
        targetRoles = p.targetRoles; cvHistory = p.cvHistory; updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    }
    return profile(id)
}
internal fun Transaction.revokeConsent(id: UUID): CandidateProfile {
    eraseVacancyAndTaskData(id)
    InvitationsTable.deleteWhere { InvitationsTable.candidateId eq id }
    Achievements.deleteWhere { Achievements.candidateId eq id }
    AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq id }
    ResumeDrafts.deleteWhere { ResumeDrafts.candidateId eq id }
    CandidateProfileEntity[id].apply {
        fullName = ""; email = ""; phone = ""
        processingConsent = false; consentPublication = false
        specialization = ""; industry = ""; desiredGrade = "junior"
        techStack = emptyList(); experience = ""; softSkills = emptyList()
        targetRoles = emptyList(); cvHistory = emptyList(); updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        fspId = null; fspLinkedAt = null; privacy = CandidatePrivacy()
    }
    return profile(id)
}
internal fun Transaction.company(id: UUID): Company = CompanyEntity[id].let {
    Company(it.name, it.description, it.website, it.industry, it.contactEmail, it.contactPhone, it.contactTelegram, it.contactNote)
}
internal fun Transaction.saveCompany(id: UUID, company: Company): Company {
    CompanyEntity[id].apply {
        name = company.name; description = company.description; website = company.website; industry = company.industry
        contactEmail = company.contactEmail; contactPhone = company.contactPhone
        contactTelegram = company.contactTelegram; contactNote = company.contactNote
    }
    return company
}
internal fun Transaction.need(id: UUID): Need = NeedEntity[id].let {
    Need(it.specialization, it.industry, it.minGrade, it.description, it.techStack)
}
internal fun Transaction.saveNeed(id: UUID, need: Need): Need {
    NeedEntity[id].apply {
        specialization = need.specialization; industry = need.industry; minGrade = need.minGrade
        description = need.description; techStack = need.techStack
    }
    return need
}
internal fun Transaction.privacy(id: UUID) = CandidateProfileEntity[id].privacy
internal fun Transaction.savePrivacy(id: UUID, privacy: CandidatePrivacy): CandidatePrivacy {
    if (!profile(id).processingConsent) forbidden("Grant processing consent before changing privacy settings")
    CandidateProfileEntity[id].privacy = privacy
    return privacy
}
