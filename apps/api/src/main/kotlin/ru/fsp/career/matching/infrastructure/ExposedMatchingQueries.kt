package ru.fsp.career.matching.infrastructure

import ru.fsp.career.identity.infrastructure.account
import ru.fsp.career.matching.application.MatchingQueries
import ru.fsp.career.matching.domain.CandidateFilter
import ru.fsp.career.shared.infrastructure.Database
import java.util.UUID

internal class ExposedMatchingQueries(private val db: Database) : MatchingQueries {
    override suspend fun need(subject: String, filter: CandidateFilter, limit: Int) = db.tx { matchForNeed(account(subject, "employer"), filter, limit) }
    override suspend fun browse(subject: String, filter: CandidateFilter, limit: Int) = db.tx { browseCandidates(account(subject, "employer"), filter, limit) }
    override suspend fun card(subject: String, id: UUID) = db.tx { candidateCard(account(subject, "employer"), id) }
}
