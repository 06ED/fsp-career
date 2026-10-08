package ru.fsp.career.identity.infrastructure

import org.jetbrains.exposed.sql.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.shared.domain.*
import ru.fsp.career.identity.domain.BrowserIdentity
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal fun Transaction.boundRole(subject: String): Pair<UUID, String>? =
    AccountEntity.find { Accounts.subject eq subject }.forUpdate().singleOrNull()
        ?.let { it.id.value to it.role }

internal fun Transaction.account(subject: String, role: String): UUID {
    val (id, bound) = boundRole(subject) ?: forbidden("Choose a role first: POST /api/me/role")
    if (bound != role) forbidden("Account bound to another role")
    return id
}

internal fun Transaction.createAccount(subject: String, role: String): UUID {
    val id = UUID.randomUUID()
    AccountEntity.new(id) { this.subject = subject; this.role = role }.flush()
    // One atomic transaction creates the account and its role-specific profile.
    if (role == "candidate") CandidateProfileEntity.new(id) {
        fullName = ""; email = ""; phone = ""
        processingConsent = false; consentPublication = false
        specialization = ""; industry = ""; desiredGrade = "junior"
        techStack = emptyList(); experience = ""; softSkills = emptyList()
        targetRoles = emptyList(); cvHistory = emptyList(); updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
    } else {
        CompanyEntity.new(id) { name = ""; description = ""; website = "" }
        NeedEntity.new(id) { specialization = ""; industry = ""; minGrade = "junior"; description = "" }
    }
    return id
}

internal fun Transaction.roleFor(subject: String, adminEmployer: Boolean): String? =
    boundRole(subject)?.second ?: if (adminEmployer) { createAccount(subject, "employer"); "employer" } else null

internal fun Transaction.chooseRole(subject: String, role: String, identity: BrowserIdentity?): String {
    val bound = boundRole(subject)?.second
    if (bound != null && bound != role) conflict("Account already bound to $bound")
    if (bound == null) {
        if (identity != null) {
            val row = IdentityEntity.find { AuthIdentities.key eq identity.id }.forUpdate().single()
            if (row.accountId != null) conflict("Account already bound")
        }
        val id = createAccount(identity?.let { "user:${it.id}" } ?: subject, role)
        if (identity != null) IdentityEntity[identity.id].accountId = id
    }
    return role
}
