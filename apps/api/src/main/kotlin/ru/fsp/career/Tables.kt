package ru.fsp.career

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb

// Flyway owns DDL; these Exposed tables map the existing PostgreSQL schema.
internal object Accounts : Table("accounts") {
    val id = uuid("id")
    val subject = text("subject")
    val role = text("role")
    override val primaryKey = PrimaryKey(id)
}

internal object AuthIdentities : Table("auth_identities") {
    val id = uuid("id")
    val provider = text("provider")
    val issuer = text("issuer")
    val externalSubject = text("external_subject")
    val accountId = reference("account_id", Accounts.id).nullable()
    val email = text("email")
    val emailVerified = bool("email_verified")
    override val primaryKey = PrimaryKey(id)
}

internal object LocalCredentials : Table("local_credentials") {
    val identityId = reference("identity_id", AuthIdentities.id)
    val passwordHash = text("password_hash")
    override val primaryKey = PrimaryKey(identityId)
}

internal object BrowserSessions : Table("browser_sessions") {
    val idHash = text("id_hash")
    val identityId = reference("identity_id", AuthIdentities.id).nullable()
    val csrfToken = text("csrf_token")
    val expiresAt = timestampWithTimeZone("expires_at")
    override val primaryKey = PrimaryKey(idHash)
}

internal object OauthTransactions : Table("oauth_transactions") {
    val stateHash = text("state_hash")
    val sessionHash = text("session_hash")
    val provider = text("provider")
    val verifier = text("verifier")
    val nonce = text("nonce")
    val expiresAt = timestampWithTimeZone("expires_at")
    override val primaryKey = PrimaryKey(stateHash)
}

internal object CandidateProfiles : Table("candidate_profiles") {
    val accountId = reference("account_id", Accounts.id)
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

internal object EmployerCompanies : Table("employer_companies") {
    val accountId = reference("account_id", Accounts.id)
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

internal object EmployerNeeds : Table("employer_needs") {
    val accountId = reference("account_id", Accounts.id)
    val specialization = text("specialization")
    val industry = text("industry")
    val minGrade = text("min_grade")
    val description = text("description")
    val techStack = jsonb<List<String>>("tech_stack", Json.Default).default(emptyList())
    override val primaryKey = PrimaryKey(accountId)
}

internal object Questions : Table("questions") {
    val id = long("id")
    val specialization = text("specialization")
    val prompt = text("prompt")
    val options = jsonb<List<Option>>("options", Json.Default)
    val correctOption = text("correct_option")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

internal object AssessmentAttempts : Table("assessment_attempts") {
    val id = uuid("id")
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val specialization = text("specialization")
    val questionIds = array<Long>("question_ids")
    val startedAt = timestampWithTimeZone("started_at")
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val score = integer("score").nullable()
    val grade = text("grade").nullable()
    // V7 adaptive assessment columns (see assessment/AssessmentService.kt).
    val targetGrade = text("target_grade").nullable()
    val industry = text("industry").default("")
    val survey = jsonb<ru.fsp.career.assessment.AssessmentSurvey>("survey", Json.Default).default(ru.fsp.career.assessment.AssessmentSurvey())
    val vacancyId = text("vacancy_id").nullable()
    val vacancyTitle = text("vacancy_title").nullable()
    val blueprint = jsonb<ru.fsp.career.assessment.BlueprintSnapshot>("blueprint", Json.Default).default(ru.fsp.career.assessment.BlueprintSnapshot())
    val plannedItems = integer("planned_items").default(0)
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val theta = double("theta").nullable()
    val thetaSe = double("theta_se").nullable()
    val passed = bool("passed").nullable()
    val confident = bool("confident").nullable()
    val expired = bool("expired").default(false)
    val engineVersion = text("engine_version").default("legacy-v1")
    override val primaryKey = PrimaryKey(id)
}

internal object AssessmentAnswers : Table("assessment_answers") {
    val attemptId = reference("attempt_id", AssessmentAttempts.id)
    val questionId = reference("question_id", Questions.id)
    val optionId = text("option_id")
    override val primaryKey = PrimaryKey(attemptId, questionId)
}

internal object Achievements : Table("achievements") {
    val id = long("id")
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val title = text("title")
    val issuer = text("issuer")
    val points = integer("points")
    val verifiedAt = timestampWithTimeZone("verified_at")
    val sourceName = text("source").default("manual")
    val externalId = text("external_id").nullable()
    val eventName = text("event_name").default("")
    val discipline = text("discipline").default("")
    val eventDate = date("event_date").nullable()
    val level = text("level").default("")
    val place = integer("place").nullable()
    val resultRole = text("result_role").default("")
    override val primaryKey = PrimaryKey(id)
}

internal object InvitationsTable : Table("invitations") {
    val id = uuid("id")
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

internal object ResumeDrafts : Table("resume_drafts") {
    val id = uuid("id")
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val parsedFields = jsonb<ResumeFields>("parsed_fields", Json.Default)
    val uploadedAt = timestampWithTimeZone("uploaded_at")
    val confirmedAt = timestampWithTimeZone("confirmed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
