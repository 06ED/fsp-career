package ru.fsp.career.assessment.infrastructure

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import ru.fsp.career.assessment.domain.AssessmentItemView

/** Served item instances of adaptive attempts (V7__assessment.sql). */
internal object AssessmentItems : CompositeIdTable("assessment_items") {
    val attemptId = reference("attempt_id", AssessmentAttempts.key)
    val attemptKey = attemptId.entityId().also { it.foreignKey = attemptId.foreignKey }
    val position = integer("position")
    val positionKey = position.entityId()
    val familyId = text("family_id")
    val seed = long("seed")
    val view = jsonb<AssessmentItemView>("view", Json.Default)
    val answerKey = text("answer_key")
    val tolerance = double("tolerance")
    val response = text("response").nullable()
    val correct = bool("correct").nullable()
    val servedAt = timestampWithTimeZone("served_at")
    val answeredAt = timestampWithTimeZone("answered_at").nullable()
    override val primaryKey = PrimaryKey(attemptId, position)
}
