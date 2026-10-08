package ru.fsp.career.achievements.application

import ru.fsp.career.achievements.domain.*
import ru.fsp.career.shared.domain.*

internal class AchievementService(private val achievements: AchievementRepository, private val registry: FspRegistry) {
    suspend fun status(subject: String) = achievements.status(subject)
    suspend fun unlink(subject: String) = achievements.unlink(subject)
    suspend fun link(subject: String, request: FspLinkRequest, email: String): FspStatus {
        val fspId = FspIds.normalize(request.fspId) ?: bad("FSP ID must look like FSP-123456")
        val verifiedEmail = email.takeIf { it.isNotBlank() } ?: forbidden("A verified e-mail is required to link an FSP ID")
        achievements.requireConsent(subject)
        val record = registry.lookup(fspId)
        if (record == null || !FspOwnership.verify(record, verifiedEmail)) missing("FSP participant not found for your verified e-mail")
        return achievements.link(subject, record)
    }
}
