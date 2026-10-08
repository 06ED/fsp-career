package ru.fsp.career.profiles.infrastructure

import ru.fsp.career.profiles.domain.*
import ru.fsp.career.assessment.infrastructure.AssessmentAttempts
import ru.fsp.career.shared.domain.*

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

internal fun Transaction.profileSpecialization(id: UUID): String =
    CandidateProfileEntity[id].specialization

internal fun Transaction.profile(id: UUID): CandidateProfile {
    val row = CandidateProfileEntity[id]

    val best = AssessmentAttempts.selectAll()
        .where {
            (AssessmentAttempts.candidateId eq id) and
                    (AssessmentAttempts.specialization eq row.specialization) and
                    AssessmentAttempts.submittedAt.isNotNull()
        }
        .maxWithOrNull(
            compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }
                .thenBy { it[AssessmentAttempts.score]!! }
        )

    return CandidateProfile(
        fullName = row.fullName,
        email = row.email,
        phone = row.phone,
        consentPublication = row.consentPublication,
        specialization = row.specialization,
        industry = row.industry,
        desiredGrade = row.desiredGrade,
        grade = best?.get(AssessmentAttempts.grade),
        score = best?.get(AssessmentAttempts.score),
        techStack = row.techStack,
        experience = row.experience,
        softSkills = row.softSkills,
        targetRoles = row.targetRoles,
        cvHistory = row.cvHistory,
        processingConsent = row.processingConsent,
    )
}
