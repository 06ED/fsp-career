package ru.fsp.career.recruitment.infrastructure

import org.jetbrains.exposed.sql.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.recruitment.domain.*
import ru.fsp.career.shared.domain.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun Transaction.invitation(id: UUID): Invitation =
    invitationRows(InvitationsTable.selectAll().where { InvitationsTable.id eq id }.toList()).single()

internal fun Transaction.invitationsFor(role: String, accountId: UUID): List<Invitation> = invitationRows(
    InvitationsTable.selectAll().where {
        if (role == "candidate") InvitationsTable.candidateId eq accountId else InvitationsTable.employerId eq accountId
    }.orderBy(InvitationsTable.createdAt to SortOrder.DESC).toList()
)

private fun Transaction.invitationRows(rows: List<ResultRow>): List<Invitation> {
    if (rows.isEmpty()) return emptyList()

    val candidates = CandidateProfiles.selectAll()
        .where { CandidateProfiles.accountId inList rows.map { it[InvitationsTable.candidateId] }.distinct() }
        .associateBy { it[CandidateProfiles.accountId] }

    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList rows.map { it[InvitationsTable.employerId] }.distinct() }
        .associateBy { it[EmployerCompanies.accountId] }

    val disclosed = rows.groupBy { it[InvitationsTable.employerId] }.mapValues { (employer, group) ->
        disclosedCandidates(employer, group.map { it[InvitationsTable.candidateId] }.distinct())
    }

    return rows.map { row ->
        val candidateId = row[InvitationsTable.candidateId]
        val employerId = row[InvitationsTable.employerId]
        val candidate = candidates.getValue(candidateId)
        val company = companies.getValue(employerId)
        val contacts = candidateId in disclosed[employerId].orEmpty()
        Invitation(
            id = row[InvitationsTable.id].toString(),
            candidateId = candidateId.toString(),
            employerId = employerId.toString(),
            description = row[InvitationsTable.description],
            salaryMinRub = row[InvitationsTable.salaryMinRub],
            salaryMaxRub = row[InvitationsTable.salaryMaxRub],
            status = row[InvitationsTable.status],
            createdAt = row[InvitationsTable.createdAt].toInstant().toString(),
            contactEmail = candidate[CandidateProfiles.email].takeIf { contacts },
            contactPhone = candidate[CandidateProfiles.phone].takeIf { contacts },
            companyName = company[EmployerCompanies.name],
            candidateName =
                if (contacts) candidate[CandidateProfiles.fullName]
                else if (candidate[CandidateProfiles.privacy].fullName)
                    employerText(candidate[CandidateProfiles.fullName], candidate[CandidateProfiles.email], candidate[CandidateProfiles.phone])
                else anonymousName(candidateId),
            contactMethod = row[InvitationsTable.contactMethod]
                .ifBlank { companyContactLine(companyFrom(company)) },
        )
    }
}

internal fun Transaction.createInvitation(employer: UUID, request: InviteRequest): Invitation {
    val candidate = uuid(request.candidateId)
    val available = CandidateProfiles.selectAll()
        .where {
            (CandidateProfiles.accountId eq candidate) and
                    (CandidateProfiles.processingConsent eq true) and
                    (CandidateProfiles.consentPublication eq true) and
                    (CandidateProfiles.fullName neq "")
        }
        .any()
    if (!available) missing("Published candidate not found")

    val company = companyFrom(
        EmployerCompanies.selectAll()
            .where { EmployerCompanies.accountId eq employer }
            .single()
    )
    if (company.name.isBlank()) bad("Set company name first")

    val contact = request.contactMethod.trim()
        .ifBlank { companyContactLine(company) }
    if (contact.isBlank()) bad("Set company contact data or provide contactMethod")

    val id = UUID.randomUUID()
    val now = OffsetDateTime.now(ZoneOffset.UTC)

    InvitationEntity.new(id) {
        employerId = employer; candidateId = candidate
        description = request.description; salaryMinRub = request.salaryMinRub; salaryMaxRub = request.salaryMaxRub
        contactMethod = contact; status = "sent"; createdAt = now; updatedAt = now
    }.flush()

    return invitation(id)
}

internal fun Transaction.changeInvitation(candidate: UUID, id: UUID, target: String): Invitation {
    val current = InvitationEntity.find {
        (InvitationsTable.key eq id) and (InvitationsTable.candidateId eq candidate)
    }.forUpdate().singleOrNull() ?: missing("Invitation not found")
    if (!invitationTransitionAllowed(
            current.status,
            target
        )
    ) conflict("Invalid invitation transition")
    current.apply { status = target; updatedAt = OffsetDateTime.now(ZoneOffset.UTC); flush() }
    return invitation(id)
}
