package ru.fsp.career.assessment.infrastructure

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import ru.fsp.career.assessment.domain.*
import java.util.UUID

internal fun Transaction.confirmedSkills(candidateIds: Collection<UUID>): Map<UUID, Set<String>> {
    if (candidateIds.isEmpty()) return emptyMap()
    val answers = (AssessmentItems innerJoin AssessmentAttempts).selectAll().where {
        (AssessmentAttempts.candidateId inList candidateIds) and AssessmentAttempts.submittedAt.isNotNull() and
            (AssessmentAttempts.engineVersion eq ENGINE_VERSION) and AssessmentItems.correct.isNotNull()
    }.mapNotNull { row ->
        val topic = ItemBank.family(row[AssessmentItems.familyId])?.topic ?: return@mapNotNull null
        row[AssessmentAttempts.candidateId] to (topic to row[AssessmentItems.correct]!!)
    }
    return answers.groupBy({ it.first }, { it.second })
        .mapValues { (_, list) -> ConfirmedSkills.skills(ConfirmedSkills.topics(list)) }
        .filterValues { it.isNotEmpty() }
}
