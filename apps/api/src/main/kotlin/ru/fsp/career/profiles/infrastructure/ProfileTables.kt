package ru.fsp.career.profiles.infrastructure

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IdTable
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import ru.fsp.career.identity.infrastructure.Accounts
import ru.fsp.career.profiles.domain.*

internal val privacyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal object CandidateProfiles : IdTable<UUID>("candidate_profiles") {
    val accountId = reference("account_id", Accounts.key)
    override val id = accountId.entityId().also { it.foreignKey = accountId.foreignKey }
    val fullName = text("full_name")
    val email = text("email")
    val phone = text("phone")
    val consentPublication = bool("consent_publication")
    val processingConsent = bool("processing_consent")
    val specialization = text("specialization")
    val industry = text("industry")
    val desiredGrade = text("desired_grade")
    val techStack = jsonb<List<String>>("tech_stack", Json.Default)
    val experience = text("experience")
    val softSkills = jsonb<List<String>>("soft_skills", Json.Default)
    val targetRoles = jsonb<List<String>>("target_roles", Json.Default)
    val cvHistory = jsonb<List<CvEntry>>("cv_history", Json.Default)
    val updatedAt = timestampWithTimeZone("updated_at")
    val privacy = jsonb<CandidatePrivacy>("privacy", privacyJson).default(CandidatePrivacy())
    val fspId = text("fsp_id").nullable()
    val fspLinkedAt = timestampWithTimeZone("fsp_linked_at").nullable()
    override val primaryKey = PrimaryKey(accountId)
}
internal object EmployerCompanies : IdTable<UUID>("employer_companies") {
    val accountId = reference("account_id", Accounts.key)
    override val id = accountId.entityId().also { it.foreignKey = accountId.foreignKey }
    val name = text("name")
    val description = text("description")
    val website = text("website")
    val industry = text("industry").default("")
    val contactEmail = text("contact_email").default("")
    val contactPhone = text("contact_phone").default("")
    val contactTelegram = text("contact_telegram").default("")
    val contactNote = text("contact_note").default("")
    override val primaryKey = PrimaryKey(accountId)
}
internal object EmployerNeeds : IdTable<UUID>("employer_needs") {
    val accountId = reference("account_id", Accounts.key)
    override val id = accountId.entityId().also { it.foreignKey = accountId.foreignKey }
    val specialization = text("specialization")
    val industry = text("industry")
    val minGrade = text("min_grade")
    val description = text("description")
    val techStack = jsonb<List<String>>("tech_stack", Json.Default).default(emptyList())
    override val primaryKey = PrimaryKey(accountId)
}
internal object ResumeDrafts : IdTable<UUID>("resume_drafts") {
    val key = uuid("id")
    override val id = key.entityId()
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val parsedFields = jsonb<ResumeFields>("parsed_fields", Json.Default)
    val uploadedAt = timestampWithTimeZone("uploaded_at")
    val confirmedAt = timestampWithTimeZone("confirmed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
