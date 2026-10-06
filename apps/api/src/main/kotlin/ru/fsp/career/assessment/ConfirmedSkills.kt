package ru.fsp.career.assessment

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import ru.fsp.career.AssessmentAttempts
import java.util.UUID

/**
 * Skills confirmed by test evidence (for matching), as opposed to self-reported tech stack.
 *
 * A topic is confirmed when, over the candidate's completed adaptive attempts, at least
 * [MIN_CORRECT] items of that topic were answered correctly AND at least [MIN_SHARE] of the topic's
 * administered items were correct (items left unanswered at expiry count as wrong). One lucky answer
 * is therefore never enough. Topics map to lowercase canonical skill names.
 */
object ConfirmedSkills {
    const val MIN_CORRECT = 2
    const val MIN_SHARE = 2.0 / 3.0

    val canonical: Map<String, Set<String>> = mapOf(
        "algorithms" to setOf("algorithms"),
        "http" to setOf("http", "rest"),
        "security" to setOf("security"),
        "sql" to setOf("sql"),
        "databases" to setOf("postgresql"),
        "kotlin" to setOf("kotlin"),
        "python" to setOf("python"),
        "concurrency" to setOf("concurrency"),
        "distributed" to setOf("distributed systems"),
        "javascript" to setOf("javascript"),
        "typescript" to setOf("typescript"),
        "react" to setOf("react"),
        "css" to setOf("css", "html"),
        "accessibility" to setOf("accessibility"),
        "web-performance" to setOf("web performance"),
        "statistics" to setOf("statistics"),
        "experiments" to setOf("a/b testing"),
        "ml" to setOf("machine learning"),
        "product-metrics" to setOf("product metrics"),
        "unit-economics" to setOf("unit economics"),
        "prioritization" to setOf("prioritization"),
        "research" to setOf("user research"),
        "ui-layout" to setOf("ui layout"),
        "typography" to setOf("typography"),
        "usability" to setOf("usability"),
        "design-systems" to setOf("design systems"),
    )

    /** Pure rule: (topic, correct) answers → confirmed topics. */
    fun topics(answers: List<Pair<String, Boolean>>): Set<String> =
        answers.groupBy({ it.first }, { it.second }).filterValues { results ->
            val correct = results.count { it }
            correct >= MIN_CORRECT && correct >= MIN_SHARE * results.size
        }.keys

    fun skills(topics: Set<String>): Set<String> = topics.flatMap { canonical[it].orEmpty() }.toSet()
}

/** Hook for matching (CandidateSignals.confirmedSkills): candidate → canonical skill names confirmed by tests. */
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
