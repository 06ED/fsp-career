package ru.fsp.career.achievements.infrastructure

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import ru.fsp.career.profiles.infrastructure.CandidateProfiles

internal object Achievements : LongIdTable("achievements") {
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val title = text("title")
    val issuer = text("issuer")
    val points = integer("points")
    val verifiedAt = timestampWithTimeZone("verified_at")
    val sourceName = text("source").default("manual")
    val externalId = text("external_id").nullable()
    val eventName = text("event_name").default("")
    val discipline = text("discipline").default("")
    val eventDate = date("event_date").nullable()
    val level = text("level").default("")
    val place = integer("place").nullable()
    val resultRole = text("result_role").default("")
}
