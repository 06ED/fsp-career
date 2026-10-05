package ru.fsp.career

import kotlinx.serialization.Serializable

@Serializable data class CvEntry(val company: String = "", val role: String = "", val startDate: String = "", val endDate: String = "", val description: String = "")
@Serializable data class CandidateProfile(val fullName: String = "", val email: String = "", val phone: String = "", val consentPublication: Boolean = false, val specialization: String = "", val industry: String = "", val desiredGrade: String = "junior", val grade: String? = null, val score: Int? = null, val techStack: List<String> = emptyList(), val experience: String = "", val softSkills: List<String> = emptyList(), val targetRoles: List<String> = emptyList(), val cvHistory: List<CvEntry> = emptyList(), val processingConsent: Boolean = false)
@Serializable data class AssessmentHistory(val results: List<Result>)
@Serializable data class ResumeFields(val fullName: String = "", val email: String = "", val phone: String = "", val experience: String = "", val techStack: List<String> = emptyList(), val softSkills: List<String> = emptyList(), val targetRoles: List<String> = emptyList(), val cvHistory: List<CvEntry> = emptyList())
@Serializable data class ResumeDraft(val draftId: String, val parsedFields: ResumeFields, val reviewed: Boolean = false)
@Serializable data class ConfirmResume(val draftId: String, val fullName: String = "", val phone: String = "", val experience: String = "", val techStack: List<String> = emptyList(), val softSkills: List<String> = emptyList(), val targetRoles: List<String> = emptyList(), val cvHistory: List<CvEntry> = emptyList())
@Serializable data class Company(val name: String = "", val description: String = "", val website: String = "")
@Serializable data class Need(val specialization: String = "", val industry: String = "", val minGrade: String = "junior", val description: String = "")
@Serializable data class Option(val id: String, val label: String)
@Serializable data class Question(val id: Long, val prompt: String, val options: List<Option>)
@Serializable data class QuestionSet(val attemptId: String, val questions: List<Question>, val expiresAt: String)
@Serializable data class Answer(val questionId: Long, val optionId: String)
@Serializable data class Submission(val attemptId: String, val answers: List<Answer>)
@Serializable data class Result(val score: Int, val grade: String, val specialization: String, val submittedAt: String)
@Serializable data class Achievement(val title: String, val issuer: String, val points: Int)
@Serializable data class Match(val candidateId: String, val fullName: String, val specialization: String, val grade: String, val score: Int, val achievementScore: Int, val totalScore: Int, val explanation: String, val achievements: List<Achievement>, val techStack: List<String> = emptyList())
@Serializable data class Matches(val matches: List<Match>)
@Serializable data class InviteRequest(val candidateId: String, val description: String, val salaryMinRub: Int, val salaryMaxRub: Int)
@Serializable data class Invitation(val id: String, val candidateId: String, val employerId: String, val description: String, val salaryMinRub: Int, val salaryMaxRub: Int, val status: String, val createdAt: String, val contactEmail: String? = null, val contactPhone: String? = null, val companyName: String = "", val candidateName: String = "")
@Serializable data class Invitations(val invitations: List<Invitation>)
@Serializable data class ErrorResponse(val error: String)

class ApiError(val status: io.ktor.http.HttpStatusCode, override val message: String): RuntimeException(message)
fun bad(message: String): Nothing = throw ApiError(io.ktor.http.HttpStatusCode.BadRequest, message)
fun forbidden(message: String): Nothing = throw ApiError(io.ktor.http.HttpStatusCode.Forbidden, message)
fun conflict(message: String): Nothing = throw ApiError(io.ktor.http.HttpStatusCode.Conflict, message)
fun missing(message: String): Nothing = throw ApiError(io.ktor.http.HttpStatusCode.NotFound, message)
fun requireText(value: String, label: String, max: Int = 500): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > max) bad("$label must contain 1..$max characters")
    return trimmed
}
fun validateCareerFields(experience: String, techStack: List<String>, softSkills: List<String>, targetRoles: List<String>, cvHistory: List<CvEntry>) {
    if (experience.length > 4000 || listOf(techStack,softSkills,targetRoles).any { items -> items.size > 30 || items.any { it.isBlank() || it.length > 100 } }) bad("Career fields exceed limits")
    if (cvHistory.size > 20 || cvHistory.any { it.company.isBlank() || it.company.length > 200 || it.role.isBlank() || it.role.length > 200 || it.startDate.length > 30 || it.endDate.length > 30 || it.description.length > 2000 }) bad("Invalid CV history")
}
fun grade(value: String): String = value.takeIf { it in listOf("junior","middle","senior") } ?: bad("grade must be junior, middle or senior")
fun uuid(value: String): java.util.UUID = try { java.util.UUID.fromString(value) } catch (_: IllegalArgumentException) { bad("Invalid UUID") }
