package ru.fsp.career.matching.application

import ru.fsp.career.matching.domain.CandidateFilter
import java.util.UUID

internal class MatchingService(private val queries: MatchingQueries) {
    suspend fun need(subject: String, filter: CandidateFilter, limit: Int, offset: Int = 0) =
        queries.need(subject, filter, limit, offset)

    suspend fun browse(subject: String, filter: CandidateFilter, limit: Int, offset: Int = 0) =
        queries.browse(subject, filter, limit, offset)

    suspend fun card(subject: String, id: UUID) =
        queries.card(subject, id)
}
