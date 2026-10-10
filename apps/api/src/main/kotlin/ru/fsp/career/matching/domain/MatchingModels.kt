package ru.fsp.career.matching.domain

import kotlinx.serialization.Serializable
import ru.fsp.career.profiles.domain.CvEntry
import ru.fsp.career.achievements.domain.VerifiedAchievement

@Serializable
data class MatchFactor(
    val code: String,
    val label: String,
    /** Maximum points this factor could give after weight rescaling (0 for multipliers/informational). */
    val weight: Double,
    /** Normalised factor value 0..1 (for `grade` it is the multiplier). */
    val value: Double,
    val points: Double,
    val text: String,
)

@Serializable
data class CandidateMatch(
    val candidateId: String,
    /** Name, or «Кандидат №XXXX» when the candidate hides it. */
    val fullName: String,
    val specialization: String,
    val grade: String,
    val category: String,
    /** Confirmed test score 0..100. */
    val score: Int,
    /** Sum of verified achievement points capped at 20 (kept for compatibility). */
    val achievementScore: Int,
    /** Rounded relevance (need mode) or strength (bank mode), 0..100. */
    val totalScore: Int,
    val relevance: Double,
    val strength: Double,
    val explanation: String,
    val factors: List<MatchFactor>,
    val flags: List<String>,
    val achievements: List<VerifiedAchievement>,
    val techStack: List<String>,
    val industry: String,
    val fspLinked: Boolean,
    val lastActiveAt: String? = null,
    val assessedAt: String? = null,
    /** Latest invitation status from THIS employer, if any. */
    val invitationStatus: String? = null,
)

@Serializable
data class MatchCategory(
    val key: String,
    val specialization: String,
    val grade: String,
    val label: String,
    val candidates: Int,
    val withFsp: Int,
    val avgTestScore: Int,
    val avgStrength: Int,
    /** exact | higher | lower | adjacent | browse */
    val fit: String,
    val recommended: Boolean,
    val reason: String,
    val avgTextRelevance: Double? = null,
)

@Serializable
data class MatchNeed(
    val specialization: String,
    val grade: String,
    val industry: String,
    val techStack: List<String>,
    val description: String,
    val query: String = "",
)

@Serializable
data class MatchResponse(
    /** need | bank */
    val mode: String,
    val nextOffset: Int? = null,
    val need: MatchNeed? = null,
    val total: Int,
    val categories: List<MatchCategory>,
    val matches: List<CandidateMatch>,
    /** Human-readable ranking formula for the UI («Как считается рейтинг»). */
    val formula: List<String>,
)

@Serializable
data class InvitationBrief(val id: String, val status: String, val createdAt: String, val salaryMinRub: Int, val salaryMaxRub: Int)

@Serializable
data class CandidateCard(
    val candidateId: String,
    val fullName: String,
    val specialization: String,
    val grade: String? = null,
    val score: Int? = null,
    val assessedAt: String? = null,
    val category: String? = null,
    val industry: String,
    val techStack: List<String>? = null,
    val experience: String? = null,
    val targetRoles: List<String>? = null,
    val cvHistory: List<CvEntry>? = null,
    val softSkills: List<String>? = null,
    val achievements: List<VerifiedAchievement>? = null,
    val fspLinked: Boolean? = null,
    /** Sections the candidate chose to hide (Russian labels). */
    val hiddenSections: List<String>,
    val lastActiveAt: String? = null,
    /** True only after the candidate accepted this employer's invitation (see ContactDisclosure). */
    val contactsDisclosed: Boolean,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val invitations: List<InvitationBrief>,
    /** Explanation against the employer's saved need, when the candidate fits its specialization. */
    val match: CandidateMatch? = null,
)
