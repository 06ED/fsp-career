package ru.fsp.career.assessment

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.json.jsonb
import ru.fsp.career.AssessmentAttempts

/** Served item instances of adaptive attempts (V7__assessment.sql). */
internal object AssessmentItems : Table("assessment_items") {
    val attemptId = reference("attempt_id", AssessmentAttempts.id)
    val position = integer("position")
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
