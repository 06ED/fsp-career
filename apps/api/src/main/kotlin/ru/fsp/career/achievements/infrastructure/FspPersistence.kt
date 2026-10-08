package ru.fsp.career.achievements.infrastructure

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import ru.fsp.career.achievements.domain.*
import ru.fsp.career.profiles.infrastructure.CandidateProfiles
import ru.fsp.career.profiles.infrastructure.CandidateProfileEntity
import ru.fsp.career.shared.domain.conflict
import ru.fsp.career.shared.infrastructure.Database
import ru.fsp.career.identity.infrastructure.account
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun Transaction.importFsp(candidateId: UUID, record: FspRecord, registry: FspRegistry) {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    Achievements.deleteWhere { (Achievements.candidateId eq candidateId) and (Achievements.sourceName eq registry.source) }
    record.results.forEach { result ->
        AchievementEntity.new {
            this.candidateId = candidateId; title = FspPointsPolicy.title(result).take(500); issuer = registry.issuerLabel
            points = FspPointsPolicy.points(result.level, result.result); verifiedAt = now; sourceName = registry.source
            externalId = result.externalId; eventName = result.eventName; discipline = result.discipline
            eventDate = result.eventDate; level = result.level; place = result.place; resultRole = result.result
        }
    }
    CandidateProfileEntity[candidateId].apply { fspId = record.participant.fspId; fspLinkedAt = now }
}
internal fun Transaction.fspStatus(candidateId: UUID, registry: FspRegistry): FspStatus {
    val row = CandidateProfileEntity[candidateId]
    val achievements = Achievements.selectAll().where { (Achievements.candidateId eq candidateId) and (Achievements.sourceName eq registry.source) }
        .orderBy(Achievements.eventDate to SortOrder.DESC).map(::verifiedAchievement)
    val linkedId = row.fspId
    val message = when {
        linkedId == null -> "ФСП ID не привязан. Профиль участвует в подборе и без истории ФСП — категория и грейд от этого не зависят."
        achievements.isEmpty() -> "ФСП ID привязан, но в реестре нет результатов соревнований. Работодатель увидит «результатов в реестре нет» — это не штраф."
        else -> "Импортировано результатов: ${achievements.size}. Работодатели видят их как подтверждённые достижения ФСП."
    }
    return FspStatus(linkedId != null, linkedId, row.fspLinkedAt?.toInstant()?.toString(), registry.displayName, achievements, achievements.sumOf { it.points }, message)
}
internal fun Transaction.linkFsp(id: UUID, record: FspRecord, registry: FspRegistry): FspStatus {
    val owner = CandidateProfiles.selectAll().where { CandidateProfiles.fspId eq record.participant.fspId }.singleOrNull()
    if (owner != null && owner[CandidateProfiles.accountId] != id) conflict("FSP ID already linked to another account")
    importFsp(id, record, registry)
    return fspStatus(id, registry)
}
internal fun Transaction.unlinkFsp(id: UUID, registry: FspRegistry): FspStatus {
    Achievements.deleteWhere { (Achievements.candidateId eq id) and (Achievements.sourceName eq registry.source) }
    CandidateProfileEntity[id].apply { fspId = null; fspLinkedAt = null }
    return fspStatus(id, registry)
}
internal suspend fun Database.linkParticipant(subject: String, record: FspRecord, registry: FspRegistry): FspStatus =
    try { tx { linkFsp(account(subject, "candidate"), record, registry) } }
    catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
        if (e.sqlState == "23505") conflict("FSP ID already linked to another account") else throw e
    }
