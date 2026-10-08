package ru.fsp.career.matching.application

import ru.fsp.career.matching.domain.CandidateFilter
import java.util.UUID

internal class MatchingService(private val queries: MatchingQueries) {
    suspend fun need(subject: String, filter: CandidateFilter, limit: Int) =
        queries.need(subject, filter, limit)

    suspend fun browse(subject: String, filter: CandidateFilter, limit: Int) =
        queries.browse(subject, filter, limit)

    suspend fun card(subject: String, id: UUID) =
        queries.card(subject, id)
}
