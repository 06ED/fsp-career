package ru.fsp.career.profiles.application

import ru.fsp.career.profiles.domain.*
import ru.fsp.career.shared.domain.*

internal class ResumeService(private val profiles: ProfileRepository, private val documents: ResumeDocuments) {
    suspend fun export(subject: String) = documents.export(profiles.candidate(subject))

    suspend fun requireUploadConsent(subject: String) {
        if (!profiles.candidate(subject).processingConsent) forbidden("Grant processing consent before uploading a resume")
    }

    suspend fun upload(subject: String, pdf: ByteArray): ResumeDraft {
        val parsed = documents.extract(pdf)
        return profiles.saveResumeDraft(subject, parsed)
    }

    suspend fun confirm(subject: String, reviewed: ConfirmResume): CandidateProfile {
        validateCareerFields(
            reviewed.experience,
            reviewed.techStack,
            reviewed.softSkills,
            reviewed.targetRoles,
            reviewed.cvHistory
        )

        if (reviewed.fullName.length > 200 || reviewed.phone.length > 50)
            bad("Reviewed contact fields too long")

        return profiles.confirmResume(subject, reviewed)
    }
}
