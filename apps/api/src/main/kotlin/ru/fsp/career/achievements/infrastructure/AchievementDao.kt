package ru.fsp.career.achievements.infrastructure

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

internal class AchievementEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AchievementEntity>(Achievements)
    var candidateId by Achievements.candidateId
    var title by Achievements.title
    var issuer by Achievements.issuer
    var points by Achievements.points
    var verifiedAt by Achievements.verifiedAt
    var sourceName by Achievements.sourceName
    var externalId by Achievements.externalId
    var eventName by Achievements.eventName
    var discipline by Achievements.discipline
    var eventDate by Achievements.eventDate
    var level by Achievements.level
    var place by Achievements.place
    var resultRole by Achievements.resultRole
}
