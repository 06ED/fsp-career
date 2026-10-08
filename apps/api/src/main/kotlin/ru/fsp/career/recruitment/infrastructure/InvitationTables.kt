package ru.fsp.career.recruitment.infrastructure

import org.jetbrains.exposed.dao.id.IdTable
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import ru.fsp.career.profiles.infrastructure.*

internal object InvitationsTable : IdTable<UUID>("invitations") {
    val key = uuid("id")
    override val id = key.entityId()
    val employerId = reference("employer_id", EmployerCompanies.accountId)
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val description = text("description")
    val salaryMinRub = integer("salary_min_rub")
    val salaryMaxRub = integer("salary_max_rub")
    val status = text("status")
    val contactMethod = text("contact_method").default("")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
