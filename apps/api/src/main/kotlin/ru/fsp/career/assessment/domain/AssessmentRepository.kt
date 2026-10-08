package ru.fsp.career.assessment.domain

import java.util.UUID

internal interface AssessmentRepository {
    suspend fun eligibility(subject: String, specialization: String?): AssessmentEligibilityView
    suspend fun history(subject: String): AssessmentHistoryView
    suspend fun current(subject: String): CurrentAssessmentView
    suspend fun start(subject: String, plan: StartPlan): StartOutcome
    suspend fun answer(subject: String, id: UUID, request: AssessmentAnswerRequest): AssessmentAnswerOutcome
}
