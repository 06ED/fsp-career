package ru.fsp.career

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import ru.fsp.career.matching.AchievementSignal
import ru.fsp.career.matching.CandidateSignals
import ru.fsp.career.matching.MatchingEngine
import ru.fsp.career.matching.MatchingWeights
import ru.fsp.career.matching.RankedCandidate
import java.util.UUID
import kotlin.math.roundToInt

/** A published candidate with privacy already applied, ready for the matching engine. */
internal data class PoolCandidate(
    val accountId: UUID,
    val row: ResultRow,
    val privacy: CandidatePrivacy,
    val signals: CandidateSignals,
    /** Visible verified achievements (empty if hidden). */
    val achievements: List<VerifiedAchievement>,
    val bestAttempt: ResultRow?,
)

private val specializationLabels = mapOf(
    "backend" to "Backend-разработка",
    "frontend" to "Frontend-разработка",
    "data" to "Данные и аналитика",
    "product" to "Продукт",
    "design" to "Дизайн",
)

internal fun specializationLabel(spec: String): String =
    specializationLabels[spec.lowercase()] ?: spec.replaceFirstChar { it.uppercase() }

internal fun categoryLabel(spec: String, grade: String): String =
    "${specializationLabel(spec)} · ${grade.replaceFirstChar { it.uppercase() }}"

internal fun anonymousName(id: UUID) = "Кандидат №" + id.toString().take(6).uppercase()

/** Published = processing consent + publication consent + non-blank name (same rule as invitations). */
internal fun Transaction.publishedCandidates(onlyIds: Collection<UUID>? = null): List<PoolCandidate> {
    val rows = CandidateProfiles.selectAll().where {
        val base = (CandidateProfiles.processingConsent eq true) and
            (CandidateProfiles.consentPublication eq true) and
            (CandidateProfiles.fullName neq "")
        if (onlyIds != null) base and (CandidateProfiles.accountId inList onlyIds) else base
    }.toList()
    return poolCandidates(rows)
}

internal fun Transaction.poolCandidates(rows: List<ResultRow>): List<PoolCandidate> {
    if (rows.isEmpty()) return emptyList()
    val ids = rows.map { it[CandidateProfiles.accountId] }
    val attempts = AssessmentAttempts.selectAll().where {
        (AssessmentAttempts.candidateId inList ids) and AssessmentAttempts.submittedAt.isNotNull()
    }.groupBy { it[AssessmentAttempts.candidateId] }
    val achievements = Achievements.selectAll().where { Achievements.candidateId inList ids }
        .orderBy(Achievements.points to SortOrder.DESC)
        .groupBy { it[Achievements.candidateId] }

    return rows.map { row ->
        val id = row[CandidateProfiles.accountId]
        val privacy = row[CandidateProfiles.privacy]
        val own = attempts[id].orEmpty()
        // Same rule as Transaction.profile(): highest grade in the CURRENT specialization, then highest score.
        val best = own.filter { it[AssessmentAttempts.specialization] == row[CandidateProfiles.specialization] }
            .maxWithOrNull(compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }.thenBy { it[AssessmentAttempts.score]!! })
        val visibleAchievements = if (privacy.fspAchievements) achievements[id].orEmpty() else emptyList()
        val lastActive = listOfNotNull(
            row[CandidateProfiles.updatedAt].toInstant(),
            own.mapNotNull { it[AssessmentAttempts.submittedAt]?.toInstant() }.maxOrNull(),
            row[CandidateProfiles.fspLinkedAt]?.toInstant(),
        ).maxOrNull()
        val text = buildList {
            if (privacy.experience) {
                add(row[CandidateProfiles.experience])
                addAll(row[CandidateProfiles.targetRoles])
            }
            if (privacy.cvHistory) row[CandidateProfiles.cvHistory].forEach { add(it.role); add(it.description) }
            if (privacy.softSkills) addAll(row[CandidateProfiles.softSkills])
        }.filter { it.isNotBlank() }.joinToString("\n")
        val signals = CandidateSignals(
            id = id.toString(),
            displayName = if (privacy.fullName) row[CandidateProfiles.fullName] else anonymousName(id),
            specialization = row[CandidateProfiles.specialization],
            industry = row[CandidateProfiles.industry],
            assessedGrade = best?.get(AssessmentAttempts.grade),
            testScore = best?.get(AssessmentAttempts.score),
            assessedAt = best?.get(AssessmentAttempts.submittedAt)?.toInstant(),
            selfReportedSkills = if (privacy.techStack) row[CandidateProfiles.techStack] else emptyList(),
            profileText = text,
            achievements = visibleAchievements.map(::achievementSignal),
            fspLinked = row[CandidateProfiles.fspId] != null,
            lastActiveAt = lastActive,
            stackHidden = !privacy.techStack,
            textHidden = !privacy.experience && !privacy.cvHistory,
            achievementsHidden = !privacy.fspAchievements,
        )
        PoolCandidate(id, row, privacy, signals, visibleAchievements.map(::verifiedAchievement), best)
    }
}

internal fun achievementSignal(row: ResultRow) = AchievementSignal(
    title = row[Achievements.title],
    issuer = row[Achievements.issuer],
    points = row[Achievements.points],
    source = row[Achievements.sourceName],
    eventName = row[Achievements.eventName].ifBlank { null },
    discipline = row[Achievements.discipline].ifBlank { null },
    eventDate = row[Achievements.eventDate] ?: row[Achievements.verifiedAt].toLocalDate(),
    level = row[Achievements.level].ifBlank { null },
    place = row[Achievements.place],
    result = row[Achievements.resultRole].ifBlank { null },
    verifiedAt = row[Achievements.verifiedAt].toInstant(),
)

internal fun verifiedAchievement(row: ResultRow) = VerifiedAchievement(
    title = row[Achievements.title],
    issuer = row[Achievements.issuer],
    points = row[Achievements.points],
    source = row[Achievements.sourceName],
    eventName = row[Achievements.eventName].ifBlank { null },
    discipline = row[Achievements.discipline].ifBlank { null },
    eventDate = row[Achievements.eventDate]?.toString(),
    level = row[Achievements.level].ifBlank { null },
    levelLabel = MatchingEngine.levelLabel(row[Achievements.level]),
    place = row[Achievements.place],
    result = row[Achievements.resultRole].ifBlank { null },
    resultLabel = MatchingEngine.resultLabel(row[Achievements.resultRole]),
)

/** Latest invitation status per candidate for one employer (status tracking in the selection). */
internal fun Transaction.latestInvitationStatus(employerId: UUID): Map<UUID, String> =
    InvitationsTable.selectAll().where { InvitationsTable.employerId eq employerId }
        .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
        .groupBy { it[InvitationsTable.candidateId] }
        .mapValues { it.value.first()[InvitationsTable.status] }

internal fun RankedCandidate.toDto(pool: Map<String, PoolCandidate>, invitationStatus: Map<UUID, String>): CandidateMatch {
    val c = candidate
    val member = pool.getValue(c.id)
    return CandidateMatch(
        candidateId = c.id,
        fullName = c.displayName,
        specialization = c.specialization,
        grade = c.assessedGrade.orEmpty(),
        category = categoryLabel(c.specialization, c.assessedGrade.orEmpty()),
        score = c.testScore ?: 0,
        achievementScore = member.achievements.sumOf { it.points }.coerceAtMost(20),
        totalScore = relevance.roundToInt(),
        relevance = round1(relevance),
        strength = round1(strength),
        explanation = summary,
        factors = factors.map { MatchFactor(it.code, it.label, round1(it.weight), round2(it.value), round1(it.points), it.text) },
        flags = flags,
        achievements = member.achievements,
        techStack = c.selfReportedSkills,
        industry = c.industry,
        fspLinked = c.fspLinked,
        lastActiveAt = c.lastActiveAt?.toString(),
        assessedAt = c.assessedAt?.toString(),
        invitationStatus = invitationStatus[member.accountId],
    )
}

internal fun needFormula(w: MatchingWeights = MatchingWeights()): List<String> = listOf(
    "Категория = специализация + грейд, подтверждённый тестом; самооценка из резюме в категорию не попадает.",
    "Грейд: точное совпадение ×1.00, выше на ступень ×0.85, на две ×0.70; ниже — только если разрешено (×0.60).",
    "Релевантность = грейд × (тест ${w.test.fmt()} + текст ${w.text.fmt()} + стек ${w.stack.fmt()} + ФСП ${w.fsp.fmt()} + отрасль ${w.industry.fmt()} + актуальность ${w.freshness.fmt()}) баллов из 100.",
    "Текст — TF-IDF-сходство описания потребности и описания опыта кандидата (русский/английский, со стеммингом); теги стека сюда не входят.",
    "Стек: навык, подтверждённый тестом, 1.0; заявленный и описанный в опыте 0.8; только описанный 0.5; только заявленный — до 0.5 с поправкой на согласованность всего заявленного стека с опытом.",
    "Факторы, не заданные в потребности (стек, отрасль), исключаются, веса остальных масштабируются до 100.",
    "Нет истории ФСП — фактор ФСП даёт 0 баллов, дополнительного штрафа нет.",
)

internal fun bankFormula(w: MatchingWeights = MatchingWeights()): List<String> = listOf(
    "Банк кандидатов: только подтверждённые категории (специализация + грейд по итогам теста).",
    "Порядок внутри категории — сила подтверждённого профиля = тест ${w.bankTest.fmt()} + ФСП ${w.bankFsp.fmt()} + актуальность ${w.bankFreshness.fmt()} баллов из 100.",
    "Самоописанные данные (стек, текст) работают только как фильтры и не поднимают кандидата выше.",
    "Нет истории ФСП — фактор ФСП даёт 0 баллов, дополнительного штрафа нет.",
)

private fun Double.fmt() = if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
private fun round2(v: Double) = (v * 100).roundToInt() / 100.0
