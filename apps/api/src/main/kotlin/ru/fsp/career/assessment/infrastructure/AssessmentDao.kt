package ru.fsp.career.assessment.infrastructure

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

internal class AssessmentAttemptEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AssessmentAttemptEntity>(AssessmentAttempts)
    var candidateId by AssessmentAttempts.candidateId
    var specialization by AssessmentAttempts.specialization
    var questionIds by AssessmentAttempts.questionIds
    var startedAt by AssessmentAttempts.startedAt
    var submittedAt by AssessmentAttempts.submittedAt
    var score by AssessmentAttempts.score
    var grade by AssessmentAttempts.grade
    var targetGrade by AssessmentAttempts.targetGrade
    var industry by AssessmentAttempts.industry
    var survey by AssessmentAttempts.survey
    var vacancyId by AssessmentAttempts.vacancyId
    var vacancyTitle by AssessmentAttempts.vacancyTitle
    var blueprint by AssessmentAttempts.blueprint
    var plannedItems by AssessmentAttempts.plannedItems
    var expiresAt by AssessmentAttempts.expiresAt
    var theta by AssessmentAttempts.theta
    var thetaSe by AssessmentAttempts.thetaSe
    var passed by AssessmentAttempts.passed
    var confident by AssessmentAttempts.confident
    var expired by AssessmentAttempts.expired
    var engineVersion by AssessmentAttempts.engineVersion
}

internal class AssessmentItemEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<AssessmentItemEntity>(AssessmentItems) {
        fun key(attempt: UUID, position: Int) = CompositeID {
            it[AssessmentItems.attemptKey] = attempt
            it[AssessmentItems.positionKey] = position
        }
    }
    var familyId by AssessmentItems.familyId
    var seed by AssessmentItems.seed
    var view by AssessmentItems.view
    var answerKey by AssessmentItems.answerKey
    var tolerance by AssessmentItems.tolerance
    var response by AssessmentItems.response
    var correct by AssessmentItems.correct
    var servedAt by AssessmentItems.servedAt
    var answeredAt by AssessmentItems.answeredAt
}
