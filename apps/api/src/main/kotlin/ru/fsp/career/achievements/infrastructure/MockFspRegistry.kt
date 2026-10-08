package ru.fsp.career.achievements.infrastructure

import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import ru.fsp.career.shared.infrastructure.Database
import ru.fsp.career.achievements.domain.*

/**
 * Demo connector backed by the synthetic `fsp_registry_*` tables seeded in V6 (e-mails `*@demo.fsp.local`,
 * event names marked «(демо)»). It is a stand-in for the real FSP registry API, not real FSP data.
 */
class MockFspRegistry(private val db: Database) : FspRegistry {
    override val source = "fsp"
    override val issuerLabel = "ФСП · демо-реестр"
    override val displayName = "демо-реестр ФСП (синтетические данные)"

    override suspend fun lookup(fspId: String): FspRecord? = db.tx {
        val participant = FspRegistryParticipants.selectAll().where { FspRegistryParticipants.fspId eq fspId }
            .singleOrNull() ?: return@tx null
        val results = FspRegistryResults.selectAll().where { FspRegistryResults.fspId eq fspId }
            .orderBy(FspRegistryResults.eventDate to SortOrder.DESC)
            .map {
                FspResult(
                    externalId = it[FspRegistryResults.id],
                    eventName = it[FspRegistryResults.eventName],
                    discipline = it[FspRegistryResults.discipline],
                    eventDate = it[FspRegistryResults.eventDate],
                    level = it[FspRegistryResults.level],
                    place = it[FspRegistryResults.place],
                    result = it[FspRegistryResults.result],
                    teamName = it[FspRegistryResults.teamName],
                )
            }
        FspRecord(
            FspParticipant(
                participant[FspRegistryParticipants.fspId],
                participant[FspRegistryParticipants.email],
                participant[FspRegistryParticipants.fullName],
                participant[FspRegistryParticipants.region],
            ),
            results,
        )
    }
}
