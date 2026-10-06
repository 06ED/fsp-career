package ru.fsp.career

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Single source of truth for «may this employer see this candidate's e-mail and phone?» (spec 2.2 rule 2:
 * contacts are revealed only after the candidate accepted an invitation or applied themselves).
 *
 * Today: the candidate accepted at least one invitation from this employer.
 * HOOK for the vacancies module: add «OR the candidate applied to one of this employer's vacancies»
 * here — invitation lists and the employer-facing candidate card both call this function.
 */
internal fun Transaction.disclosedCandidates(employerId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
    if (candidateIds.isEmpty()) return emptySet()
    return InvitationsTable.selectAll().where {
        (InvitationsTable.employerId eq employerId) and
            (InvitationsTable.candidateId inList candidateIds) and
            (InvitationsTable.status eq "accepted")
    }.map { it[InvitationsTable.candidateId] }.toSet()
}
