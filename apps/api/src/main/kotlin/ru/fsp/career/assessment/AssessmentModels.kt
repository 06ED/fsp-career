package ru.fsp.career.assessment

import kotlinx.serialization.Serializable
import ru.fsp.career.Option

@Serializable data class AssessmentCatalogEntry(val id: String, val label: String, val description: String = "")

@Serializable data class AssessmentGradeView(
    val id: String, val label: String, val description: String,
    /** Cut-point on θ used to confirm this grade (junior: entry level). */
    val thetaCut: Double,
    /** The same cut expressed on the 0..100 score scale (100·Φ(cut)). */
    val scoreCut: Int,
)

@Serializable data class AssessmentVacancyView(
    val id: String, val title: String, val company: String, val specialization: String,
    val industry: String, val grade: String, val text: String,
)

@Serializable data class AssessmentCatalogView(
    val specializations: List<AssessmentCatalogEntry>,
    val industries: List<AssessmentCatalogEntry>,
    val teamRoles: List<AssessmentCatalogEntry>,
    val workFormats: List<AssessmentCatalogEntry>,
    val grades: List<AssessmentGradeView>,
    val topics: List<AssessmentCatalogEntry>,
    val vacancies: List<AssessmentVacancyView>,
    val testLength: Int,
    val timeLimitMinutes: Long,
    val cooldownDays: Long,
    val decisionMargin: Double,
    val familyCount: Int,
)

/** Survey answered before every attempt; stored with the attempt and copied to the profile. */
@Serializable data class AssessmentSurvey(
    val industry: String = "",
    val specialization: String = "",
    val experienceYears: Int = 0,
    val teamRoles: List<String> = emptyList(),
    val workFormat: String = "",
)

@Serializable data class StartAssessmentRequest(
    val survey: AssessmentSurvey,
    val targetGrade: String,
    val vacancyId: String? = null,
    val vacancyText: String? = null,
)

/** What the candidate sees for one item. Contains no answer key by construction. */
@Serializable data class AssessmentItemView(
    val topic: String,
    val topicLabel: String,
    val prompt: String,
    val code: String? = null,
    val language: String? = null,
    /** "choice" (send an option id) or "number" (send a number; comma or dot as decimal separator). */
    val format: String,
    val options: List<Option> = emptyList(),
    val unit: String? = null,
)

@Serializable data class AssessmentTopicQuota(val topic: String, val label: String, val weight: Double, val items: Int)

@Serializable data class AssessmentAttemptView(
    val attemptId: String,
    val specialization: String,
    val industry: String,
    val targetGrade: String,
    val vacancyId: String? = null,
    val vacancyTitle: String? = null,
    val startedAt: String,
    val expiresAt: String,
    /** 0-based position of the current item; equals the number of answered items. */
    val position: Int,
    val total: Int,
    val item: AssessmentItemView? = null,
    val blueprint: List<AssessmentTopicQuota> = emptyList(),
)

@Serializable data class AssessmentAnswerRequest(val position: Int, val answer: String)

@Serializable data class AssessmentGradeOptionView(
    val grade: String, val allowed: Boolean, val reason: String, val message: String, val availableAt: String? = null,
)

@Serializable data class AssessmentResultView(
    val attemptId: String,
    val specialization: String,
    val industry: String = "",
    /** Declared grade the attempt tested; null for legacy (pre-adaptive) attempts. */
    val targetGrade: String? = null,
    val passed: Boolean? = null,
    val confident: Boolean? = null,
    val theta: Double? = null,
    val thetaSe: Double? = null,
    /** 100·Φ(θ̂), stored in assessment_attempts.score. */
    val score: Int,
    /** Confirmed grade in the specialization after this attempt (never lower than before). */
    val grade: String,
    val previousGrade: String? = null,
    val answered: Int,
    val correct: Int,
    val total: Int,
    val expired: Boolean = false,
    val startedAt: String,
    val submittedAt: String,
    val survey: AssessmentSurvey? = null,
    val vacancyId: String? = null,
    val vacancyTitle: String? = null,
    val method: String,
    val summary: String,
    /** Filled for a just-finished attempt: what the candidate may do right now. */
    val nextOptions: List<AssessmentGradeOptionView> = emptyList(),
)

@Serializable data class AssessmentHistoryView(val results: List<AssessmentResultView>)

@Serializable data class AssessmentAnswerOutcome(
    val done: Boolean,
    val attempt: AssessmentAttemptView? = null,
    val result: AssessmentResultView? = null,
)

@Serializable data class CurrentAssessmentView(
    val attempt: AssessmentAttemptView? = null,
    /** Set when this request closed an attempt whose time had run out. */
    val closed: AssessmentResultView? = null,
)

@Serializable data class AssessmentEligibilityView(
    val specialization: String,
    val confirmedGrade: String? = null,
    val score: Int? = null,
    val lastAttemptAt: String? = null,
    val options: List<AssessmentGradeOptionView>,
    val openAttemptId: String? = null,
    val cooldownDays: Long,
)

@Serializable data class RetakeConflict(val error: String, val reason: String, val retakeAvailableAt: String? = null, val openAttemptId: String? = null)

@Serializable data class BlueprintPreviewRequest(
    val specialization: String? = null,
    val vacancyId: String? = null,
    val vacancyText: String? = null,
    val targetGrade: String? = null,
)

@Serializable data class AssessmentSkillView(val id: String, val label: String, val topics: List<String>, val hits: Int, val matched: List<String>)

@Serializable data class BlueprintPreview(
    val specialization: String,
    val detectedSpecialization: String? = null,
    val vacancyId: String? = null,
    val vacancyTitle: String? = null,
    val targetGrade: String,
    val skills: List<AssessmentSkillView>,
    val uncoveredSkills: List<AssessmentSkillView>,
    val topics: List<AssessmentTopicQuota>,
    val sampleItems: List<AssessmentItemView>,
    val poolSize: Int,
    val testLength: Int,
)

/** Blueprint snapshot stored with the attempt (assessment_attempts.blueprint). */
@Serializable data class BlueprintSnapshot(
    val quotas: Map<String, Int> = emptyMap(),
    val weights: Map<String, Double> = emptyMap(),
    val skills: List<String> = emptyList(),
)
