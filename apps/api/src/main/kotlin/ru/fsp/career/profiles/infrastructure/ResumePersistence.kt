package ru.fsp.career.profiles.infrastructure

import org.jetbrains.exposed.sql.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.shared.domain.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun Transaction.saveResumeDraft(id: UUID, parsed: ResumeFields): ResumeDraft {
    if (!profile(id).processingConsent) forbidden("Processing consent was revoked during upload")
    val draft = UUID.randomUUID()
    ResumeDraftEntity.new(draft) { candidateId = id; parsedFields = parsed; uploadedAt = OffsetDateTime.now(ZoneOffset.UTC) }
    return ResumeDraft(draft.toString(), parsed)
}
internal fun Transaction.confirmResume(id: UUID, reviewed: ConfirmResume): CandidateProfile {
    if (!profile(id).processingConsent) forbidden("Grant processing consent before confirming a resume")
    val confirmed = OffsetDateTime.now(ZoneOffset.UTC)
    val draft = ResumeDraftEntity.find {
        (ResumeDrafts.key eq uuid(reviewed.draftId)) and (ResumeDrafts.candidateId eq id) and
            ResumeDrafts.confirmedAt.isNull() and (ResumeDrafts.uploadedAt greater confirmed.minusDays(1))
    }.forUpdate().singleOrNull() ?: missing("Unconfirmed resume draft not found or expired")
    draft.confirmedAt = confirmed
    CandidateProfileEntity[id].apply {
        fullName = reviewed.fullName.trim(); phone = reviewed.phone.trim(); experience = reviewed.experience.trim()
        techStack = reviewed.techStack; softSkills = reviewed.softSkills
        targetRoles = reviewed.targetRoles; cvHistory = reviewed.cvHistory; updatedAt = confirmed
    }
    return profile(id)
}
