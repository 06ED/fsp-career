package ru.fsp.career.assessment.infrastructure

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.dao.id.IdTable
import java.util.UUID
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import ru.fsp.career.assessment.domain.*
import ru.fsp.career.profiles.infrastructure.CandidateProfiles
import ru.fsp.career.shared.domain.Option

internal object Questions : Table("questions") {
    val id = long("id")
    val specialization = text("specialization")
    val prompt = text("prompt")
    val options = jsonb<List<Option>>("options", Json.Default)
    val correctOption = text("correct_option")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}
internal object AssessmentAttempts : IdTable<UUID>("assessment_attempts") {
    val key = uuid("id")
    override val id = key.entityId()
    val candidateId = reference("candidate_id", CandidateProfiles.accountId)
    val specialization = text("specialization")
    val questionIds = array<Long>("question_ids")
    val startedAt = timestampWithTimeZone("started_at")
    val submittedAt = timestampWithTimeZone("submitted_at").nullable()
    val score = integer("score").nullable()
    val grade = text("grade").nullable()
    val targetGrade = text("target_grade").nullable()
    val industry = text("industry").default("")
    val survey = jsonb<AssessmentSurvey>("survey", Json.Default).default(AssessmentSurvey())
    val vacancyId = text("vacancy_id").nullable()
    val vacancyTitle = text("vacancy_title").nullable()
    val blueprint = jsonb<BlueprintSnapshot>("blueprint", Json.Default).default(BlueprintSnapshot())
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
    val attemptId = reference("attempt_id", AssessmentAttempts.key)
    val questionId = reference("question_id", Questions.id)
    val optionId = text("option_id")
    override val primaryKey = PrimaryKey(attemptId, questionId)
}
