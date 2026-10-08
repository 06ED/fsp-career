package ru.fsp.career.profiles.domain

import kotlinx.serialization.Serializable

/** Hidden sections are neither shown to employers nor used for ranking. Publication is a separate opt-in. */
@Serializable
data class CandidatePrivacy(
    val fullName: Boolean = true, val techStack: Boolean = true,
    val experience: Boolean = true, val cvHistory: Boolean = true,
    val softSkills: Boolean = true, val fspAchievements: Boolean = true,
)
