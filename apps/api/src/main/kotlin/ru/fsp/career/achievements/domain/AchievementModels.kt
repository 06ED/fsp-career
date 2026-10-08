package ru.fsp.career.achievements.domain

import kotlinx.serialization.Serializable

@Serializable data class Achievement(val title: String, val issuer: String, val points: Int)
@Serializable data class VerifiedAchievement(
    val title: String, val issuer: String, val points: Int, val source: String = "manual",
    val eventName: String? = null, val discipline: String? = null, val eventDate: String? = null,
    val level: String? = null, val levelLabel: String? = null, val place: Int? = null,
    val result: String? = null, val resultLabel: String? = null,
)
@Serializable data class FspLinkRequest(val fspId: String)
@Serializable data class FspStatus(
    val linked: Boolean, val fspId: String? = null, val linkedAt: String? = null,
    val registry: String, val achievements: List<VerifiedAchievement> = emptyList(),
    val totalPoints: Int = 0, val message: String,
)
