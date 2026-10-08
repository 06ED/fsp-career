package ru.fsp.career.assessment.infrastructure

import ru.fsp.career.assessment.domain.*
import ru.fsp.career.identity.infrastructure.account
import ru.fsp.career.profiles.infrastructure.profileSpecialization
import ru.fsp.career.shared.domain.bad
import ru.fsp.career.shared.infrastructure.Database
import java.util.UUID

internal class ExposedAssessmentRepository(private val db: Database) : AssessmentRepository {
    override suspend fun eligibility(subject: String, specialization: String?) = db.tx {
        val id = account(subject, "candidate")
        val now = nowUtc()
        closeExpired(id, now)
        val category = specialization ?: profileSpecialization(id).takeIf { it.isNotBlank() }?.let(::requireSpecialization)
            ?: bad("Pass specialization or complete the survey first")
        eligibility(id, category, now)
    }
    override suspend fun history(subject: String) = db.tx {
        val id = account(subject, "candidate"); closeExpired(id, nowUtc()); AssessmentHistoryView(history(id))
    }
    override suspend fun current(subject: String) = db.tx {
        val id = account(subject, "candidate"); val closed = closeExpired(id, nowUtc()); CurrentAssessmentView(currentAttempt(id), closed)
    }
    override suspend fun start(subject: String, plan: StartPlan) = db.tx { startAttempt(account(subject, "candidate"), plan, nowUtc()) }
    override suspend fun answer(subject: String, id: UUID, request: AssessmentAnswerRequest) = db.tx { answerItem(account(subject, "candidate"), id, request, nowUtc()) }
}
