package ru.fsp.career.achievements.domain

internal interface AchievementRepository {
    suspend fun status(subject: String): FspStatus
    suspend fun unlink(subject: String): FspStatus
    suspend fun requireConsent(subject: String)
    suspend fun link(subject: String, record: FspRecord): FspStatus
}
