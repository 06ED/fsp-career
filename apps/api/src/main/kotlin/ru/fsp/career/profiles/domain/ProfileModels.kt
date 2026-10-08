package ru.fsp.career.profiles.domain

import kotlinx.serialization.Serializable
import ru.fsp.career.shared.domain.bad

@Serializable
data class CvEntry(
    val company: String = "",
    val role: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val description: String = ""
)

@Serializable
data class CandidateProfile(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val consentPublication: Boolean = false,
    val specialization: String = "",
    val industry: String = "",
    val desiredGrade: String = "junior",
    val grade: String? = null,
    val score: Int? = null,
    val techStack: List<String> = emptyList(),
    val experience: String = "",
    val softSkills: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    val cvHistory: List<CvEntry> = emptyList(),
    val processingConsent: Boolean = false
)

@Serializable
data class ResumeFields(
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val experience: String = "",
    val techStack: List<String> = emptyList(),
    val softSkills: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    val cvHistory: List<CvEntry> = emptyList()
)

@Serializable
data class ResumeDraft(val draftId: String, val parsedFields: ResumeFields, val reviewed: Boolean = false)

@Serializable
data class ConfirmResume(
    val draftId: String,
    val fullName: String = "",
    val phone: String = "",
    val experience: String = "",
    val techStack: List<String> = emptyList(),
    val softSkills: List<String> = emptyList(),
    val targetRoles: List<String> = emptyList(),
    val cvHistory: List<CvEntry> = emptyList()
)

@Serializable
data class Company(
    val name: String = "",
    val description: String = "",
    val website: String = "",
    val industry: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val contactTelegram: String = "",
    val contactNote: String = ""
)

@Serializable
data class Need(
    val specialization: String = "",
    val industry: String = "",
    val minGrade: String = "junior",
    val description: String = "",
    val techStack: List<String> = emptyList()
)

fun validateCareerFields(
    experience: String,
    techStack: List<String>,
    softSkills: List<String>,
    targetRoles: List<String>,
    cvHistory: List<CvEntry>
) {
    if (experience.length > 4000 || listOf(
            techStack,
            softSkills,
            targetRoles
        ).any { items -> items.size > 30 || items.any { it.isBlank() || it.length > 100 } }
    ) bad("Career fields exceed limits")

    if (cvHistory.size > 20 || cvHistory.any {
            it.company.isBlank() ||
                    it.company.length > 200 ||
                    it.role.isBlank() ||
                    it.role.length > 200 ||
                    it.startDate.length > 30 ||
                    it.endDate.length > 30 ||
                    it.description.length > 2000
        }
    ) bad("Invalid CV history")
}
