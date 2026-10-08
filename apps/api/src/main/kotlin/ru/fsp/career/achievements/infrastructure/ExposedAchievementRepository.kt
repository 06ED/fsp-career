package ru.fsp.career.achievements.infrastructure

import ru.fsp.career.achievements.domain.*
import ru.fsp.career.identity.infrastructure.account
import ru.fsp.career.profiles.infrastructure.profile
import ru.fsp.career.shared.domain.forbidden
import ru.fsp.career.shared.infrastructure.Database

internal class ExposedAchievementRepository(private val db: Database, private val registry: FspRegistry) : AchievementRepository {
    override suspend fun status(subject: String) = db.tx { fspStatus(account(subject, "candidate"), registry) }
    override suspend fun unlink(subject: String) = db.tx { unlinkFsp(account(subject, "candidate"), registry) }
    override suspend fun requireConsent(subject: String) {
        db.tx { if (!profile(account(subject, "candidate")).processingConsent) forbidden("Grant processing consent before linking FSP ID") }
    }
    override suspend fun link(subject: String, record: FspRecord) = db.linkParticipant(subject, record, registry)
}
