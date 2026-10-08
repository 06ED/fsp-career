package ru.fsp.career.recruitment.infrastructure

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Single source of truth for «may this employer see this candidate's e-mail and phone?» (spec 2.2 rule 2:
 * contacts are revealed only after the candidate accepted an invitation or applied themselves).
 *
 * True when the candidate accepted at least one invitation from this employer OR has an application
 * (not withdrawn — withdrawal deletes the row) to one of this employer's vacancies. Invitation lists and the
 * employer-facing candidate card both call this function.
 */
internal fun Transaction.disclosedCandidates(employerId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
    if (candidateIds.isEmpty()) return emptySet()

    val accepted = InvitationsTable.selectAll()
        .where {
            (InvitationsTable.employerId eq employerId) and
                    (InvitationsTable.candidateId inList candidateIds) and
                    (InvitationsTable.status eq "accepted")
        }
        .map { it[InvitationsTable.candidateId] }

    val applied =
        VacancyApplications
            .join(
                Vacancies,
                JoinType.INNER,
                VacancyApplications.vacancyId,
                Vacancies.id
            )
            .selectAll()
            .where {
                (Vacancies.employerId eq employerId) and (VacancyApplications.candidateId inList candidateIds)
            }
            .map { it[VacancyApplications.candidateId] }

    return (accepted + applied).toSet()
}
