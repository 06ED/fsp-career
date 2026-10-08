package ru.fsp.career.assessment.application

import ru.fsp.career.assessment.domain.*
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.asKotlinRandom

internal class AssessmentService(private val assessments: AssessmentRepository) {
    private val previewRandom = SecureRandom().asKotlinRandom()
    fun catalog() = catalogView()
    fun preview(request: BlueprintPreviewRequest) = previewBlueprint(request, previewRandom)
    suspend fun eligibility(subject: String, requested: String?): AssessmentEligibilityView {
        val explicit = requested?.let(::requireSpecialization)
        return assessments.eligibility(subject, explicit)
    }
    suspend fun history(subject: String) = assessments.history(subject)
    suspend fun current(subject: String) = assessments.current(subject)
    suspend fun start(subject: String, request: StartAssessmentRequest): StartOutcome {
        val plan = planStart(request)
        return assessments.start(subject, plan)
    }
    suspend fun answer(subject: String, id: UUID, request: AssessmentAnswerRequest) =
        assessments.answer(subject, id, request)
}
