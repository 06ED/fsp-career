package ru.fsp.career.matching.application

import ru.fsp.career.matching.domain.*
import java.util.UUID

internal interface MatchingQueries {
    suspend fun need(subject: String, filter: CandidateFilter, limit: Int, offset: Int): MatchResponse
    suspend fun browse(subject: String, filter: CandidateFilter, limit: Int, offset: Int): MatchResponse
    suspend fun card(subject: String, id: UUID): CandidateCard
}
