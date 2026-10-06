package ru.fsp.career

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun gradeRank(value: String): Int = when (value) {
    "senior" -> 3
    "middle" -> 2
    else -> 1
}

internal fun Transaction.boundRole(subject: String): Pair<UUID, String>? =
    Accounts.selectAll().where { Accounts.subject eq subject }.forUpdate().singleOrNull()
        ?.let { it[Accounts.id] to it[Accounts.role] }

/** Resolves the caller's account and enforces that it is bound to [role]; the role is chosen once via POST /api/me/role. */
internal fun Transaction.account(subject: String, role: String): UUID {
    val (id, bound) = boundRole(subject) ?: forbidden("Choose a role first: POST /api/me/role")
    if (bound != role) forbidden("Account bound to another role")
    return id
}

internal fun Transaction.createAccount(subject: String, role: String): UUID {
    val id = UUID.randomUUID()
    Accounts.insert {
        it[Accounts.id] = id
        it[Accounts.subject] = subject
        it[Accounts.role] = role
    }
    if (role == "candidate") {
        CandidateProfiles.insert {
            it[accountId] = id
            it[fullName] = ""
            it[email] = ""
            it[phone] = ""
            it[processingConsent] = false
            it[consentPublication] = false
            it[specialization] = ""
            it[industry] = ""
            it[desiredGrade] = "junior"
            it[techStack] = emptyList()
            it[experience] = ""
            it[softSkills] = emptyList()
            it[targetRoles] = emptyList()
            it[cvHistory] = emptyList()
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    } else {
        EmployerCompanies.insert {
            it[accountId] = id
            it[name] = ""
            it[description] = ""
            it[website] = ""
        }
        EmployerNeeds.insert {
            it[accountId] = id
            it[specialization] = ""
            it[industry] = ""
            it[minGrade] = "junior"
            it[description] = ""
        }
    }
    return id
}

internal fun Transaction.profile(id: UUID): CandidateProfile {
    val row = CandidateProfiles.selectAll()
        .where { CandidateProfiles.accountId eq id }
        .single()

    val best = AssessmentAttempts.selectAll()
        .where {
            (AssessmentAttempts.candidateId eq id) and
                    (AssessmentAttempts.specialization eq row[CandidateProfiles.specialization]) and
                    AssessmentAttempts.submittedAt.isNotNull()
        }
        .maxWithOrNull(
            compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }
                .thenBy { it[AssessmentAttempts.score]!! }
        )

    return CandidateProfile(
        fullName = row[CandidateProfiles.fullName],
        email = row[CandidateProfiles.email],
        phone = row[CandidateProfiles.phone],
        consentPublication = row[CandidateProfiles.consentPublication],
        specialization = row[CandidateProfiles.specialization],
        industry = row[CandidateProfiles.industry],
        desiredGrade = row[CandidateProfiles.desiredGrade],
        grade = best?.get(AssessmentAttempts.grade),
        score = best?.get(AssessmentAttempts.score),
        techStack = row[CandidateProfiles.techStack],
        experience = row[CandidateProfiles.experience],
        softSkills = row[CandidateProfiles.softSkills],
        targetRoles = row[CandidateProfiles.targetRoles],
        cvHistory = row[CandidateProfiles.cvHistory],
        processingConsent = row[CandidateProfiles.processingConsent],
    )
}

internal fun Transaction.invitation(id: UUID): Invitation =
    invitationRows(InvitationsTable.selectAll().where { InvitationsTable.id eq id }.toList()).single()

internal fun Transaction.invitationsFor(role: String, accountId: UUID): List<Invitation> {
    val rows = InvitationsTable.selectAll().where {
        if (role == "candidate") InvitationsTable.candidateId eq accountId
        else InvitationsTable.employerId eq accountId
    }.orderBy(InvitationsTable.createdAt to SortOrder.DESC).toList()
    return invitationRows(rows)
}

private fun Transaction.invitationRows(rows: List<ResultRow>): List<Invitation> {
    if (rows.isEmpty()) return emptyList()

    val candidateIds = rows.map { it[InvitationsTable.candidateId] }.distinct()
    val employerIds = rows.map { it[InvitationsTable.employerId] }.distinct()

    val candidates = CandidateProfiles.selectAll()
        .where { CandidateProfiles.accountId inList candidateIds }
        .associateBy { it[CandidateProfiles.accountId] }
    val companies = EmployerCompanies.selectAll()
        .where { EmployerCompanies.accountId inList employerIds }
        .associateBy { it[EmployerCompanies.accountId] }

    return rows.map { row ->
        val candidateId = row[InvitationsTable.candidateId]
        val employerId = row[InvitationsTable.employerId]
        val candidate = candidates.getValue(candidateId)
        val company = companies.getValue(employerId)
        val accepted = row[InvitationsTable.status] == "accepted"
        Invitation(
            id = row[InvitationsTable.id].toString(),
            candidateId = candidateId.toString(),
            employerId = employerId.toString(),
            description = row[InvitationsTable.description],
            salaryMinRub = row[InvitationsTable.salaryMinRub],
            salaryMaxRub = row[InvitationsTable.salaryMaxRub],
            status = row[InvitationsTable.status],
            createdAt = row[InvitationsTable.createdAt].toInstant().toString(),
            contactEmail = candidate[CandidateProfiles.email].takeIf { accepted },
            contactPhone = candidate[CandidateProfiles.phone].takeIf { accepted },
            companyName = company[EmployerCompanies.name],
            candidateName = candidate[CandidateProfiles.fullName],
        )
    }
}
