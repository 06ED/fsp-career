package ru.fsp.career

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date

/** Privacy JSON tolerates keys added by later versions (forward compatible). */
internal val privacyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Synthetic FSP registry used by MockFspRegistry (V4 seed). */
internal object FspRegistryParticipants : Table("fsp_registry_participants") {
    val fspId = text("fsp_id")
    val email = text("email")
    val fullName = text("full_name")
    val region = text("region")
    override val primaryKey = PrimaryKey(fspId)
}

internal object FspRegistryResults : Table("fsp_registry_results") {
    val id = text("id")
    val fspId = reference("fsp_id", FspRegistryParticipants.fspId)
    val eventName = text("event_name")
    val discipline = text("discipline")
    val eventDate = date("event_date")
    val level = text("level")
    val place = integer("place").nullable()
    val result = text("result")
    val teamName = text("team_name").nullable()
    override val primaryKey = PrimaryKey(id)
}
