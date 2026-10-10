package ru.fsp.career.matching.infrastructure

import ru.fsp.career.matching.domain.*
import ru.fsp.career.assessment.infrastructure.*
import ru.fsp.career.achievements.domain.*
import ru.fsp.career.achievements.infrastructure.*
import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.recruitment.infrastructure.*
import ru.fsp.career.shared.domain.*

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
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
    // Answers to the weekly short tasks are the "fresh signal" the spec asks regular tasks to provide.
    val taskActivity = lastTaskActivity(ids)
    // Topics answered correctly in adaptive tests count as confirmed skills (weight 1.0 in the stack factor).
    val testedSkills = confirmedSkills(ids)

    return rows.map { row ->
        val id = row[CandidateProfiles.accountId]
        val privacy = row[CandidateProfiles.privacy]
        fun visible(text: String) = employerText(text, row[CandidateProfiles.email], row[CandidateProfiles.phone],
            row[CandidateProfiles.fullName].takeUnless { privacy.fullName }.orEmpty())
        val own = attempts[id].orEmpty()
        // Same rule as Transaction.profile(): highest grade in the CURRENT specialization, then highest score.
        val best = own.filter { it[AssessmentAttempts.specialization] == row[CandidateProfiles.specialization] && it[AssessmentAttempts.grade] != null && it[AssessmentAttempts.passed] != false }
            .maxWithOrNull(compareBy<ResultRow> { gradeRank(it[AssessmentAttempts.grade]!!) }.thenBy { it[AssessmentAttempts.score]!! })
        val visibleAchievements = if (privacy.fspAchievements) achievements[id].orEmpty() else emptyList()
        val lastActive = listOfNotNull(
            row[CandidateProfiles.updatedAt].toInstant(),
            own.mapNotNull { it[AssessmentAttempts.submittedAt]?.toInstant() }.maxOrNull(),
            row[CandidateProfiles.fspLinkedAt]?.toInstant(),
            taskActivity[id],
        ).maxOrNull()
        val text = buildList {
            if (privacy.experience) {
                add(row[CandidateProfiles.experience])
                addAll(row[CandidateProfiles.targetRoles])
            }
            if (privacy.cvHistory) row[CandidateProfiles.cvHistory].forEach { add(it.role); add(it.description) }
            if (privacy.softSkills) addAll(row[CandidateProfiles.softSkills])
        }.filter { it.isNotBlank() }.joinToString("\n").let(::visible)
        val signals = CandidateSignals(
            id = id.toString(),
            displayName = if (privacy.fullName) employerText(row[CandidateProfiles.fullName], row[CandidateProfiles.email], row[CandidateProfiles.phone]) else anonymousName(id),
            specialization = visible(row[CandidateProfiles.specialization]),
            industry = visible(row[CandidateProfiles.industry]),
            assessedGrade = best?.get(AssessmentAttempts.grade),
            testScore = best?.get(AssessmentAttempts.score),
            assessedAt = best?.get(AssessmentAttempts.submittedAt)?.toInstant(),
            selfReportedSkills = if (privacy.techStack) row[CandidateProfiles.techStack].map(::visible) else emptyList(),
            profileText = text,
            achievements = visibleAchievements.map(::achievementSignal),
            fspLinked = row[CandidateProfiles.fspId] != null,
            lastActiveAt = lastActive,
            confirmedSkills = testedSkills[id].orEmpty().map(TextAnalysis::skillKey).toSet(),
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

private fun CategoryStat.toDto() = MatchCategory(
    key = "$specialization:$grade", specialization = specialization, grade = grade,
    label = categoryLabel(specialization, grade), candidates = candidates, withFsp = withFsp,
    avgTestScore = avgTestScore.roundToInt(), avgStrength = avgStrength.roundToInt(),
    fit = fit, recommended = recommended, reason = reason,
    avgTextRelevance = avgTextRelevance?.let { (it * 1000).roundToInt() / 1000.0 },
)

private fun Need.toQuery() = NeedQuery(specialization, minGrade, industry, techStack, description)

internal fun Transaction.matchForNeed(employer: UUID, filter: CandidateFilter, limit: Int, offset: Int = 0): MatchResponse {
    val need = need(employer)
    if (need.specialization.isBlank() && filter.specialization == null) bad("Set employer needs first or provide specialization")
    val pool = publishedCandidates()
    val byId = pool.associateBy { it.signals.id }
    val ranking = MatchingEngine().rankForNeed(need.toQuery(), PreparedPool(pool.map { it.signals }), filter)
    val statuses = latestInvitationStatus(employer)
    return MatchResponse(
        mode = "need",
        need = MatchNeed(
            filter.specialization ?: need.specialization, filter.grade ?: need.minGrade,
            need.industry, need.techStack, need.description, filter.query.orEmpty()
        ),
        total = ranking.results.size, categories = ranking.categories.map { it.toDto() },
        matches = ranking.results.drop(offset).take(limit).map { it.toDto(byId, statuses) }, formula = needFormula(),
        nextOffset = (offset.toLong() + limit).takeIf { it < ranking.results.size }?.toInt(),
    )
}

internal fun Transaction.browseCandidates(employer: UUID, filter: CandidateFilter, limit: Int, offset: Int = 0): MatchResponse {
    val pool = publishedCandidates()
    val byId = pool.associateBy { it.signals.id }
    val ranking = MatchingEngine().browse(PreparedPool(pool.map { it.signals }), filter)
    val statuses = latestInvitationStatus(employer)
    return MatchResponse(
        mode = "bank",
        total = ranking.results.size,
        categories = ranking.categories.map { it.toDto() },
        matches = ranking.results.drop(offset).take(limit).map { it.toDto(byId, statuses) },
        nextOffset = (offset.toLong() + limit).takeIf { it < ranking.results.size }?.toInt(),
        formula = bankFormula()
    )
}

internal fun Transaction.candidateCard(employer: UUID, candidateId: UUID): CandidateCard {
    val pool = publishedCandidates()
    val disclosed = candidateId in disclosedCandidates(employer, listOf(candidateId))
    val published = pool.firstOrNull { it.accountId == candidateId }
    val member = published ?: if (disclosed) {
        CandidateProfiles.selectAll()
            .where { (CandidateProfiles.accountId eq candidateId) and (CandidateProfiles.processingConsent eq true) }
            .toList().let { poolCandidates(it) }.singleOrNull()
    } else null
    member ?: missing("Published candidate not found")
    val row = member.row
    val privacy = member.privacy
    val statuses = latestInvitationStatus(employer)
    val need = need(employer)
    val match = if (published != null && need.specialization.isNotBlank()) {
        MatchingEngine().explain(need.toQuery(), PreparedPool(pool.map { it.signals }), candidateId.toString())
            ?.toDto(pool.associateBy { it.signals.id }, statuses)
    } else null
    val invitations = InvitationsTable.selectAll()
        .where { (InvitationsTable.employerId eq employer) and (InvitationsTable.candidateId eq candidateId) }
        .orderBy(InvitationsTable.createdAt to SortOrder.DESC).map {
            InvitationBrief(
                it[InvitationsTable.id].toString(),
                it[InvitationsTable.status],
                it[InvitationsTable.createdAt].toInstant().toString(),
                it[InvitationsTable.salaryMinRub],
                it[InvitationsTable.salaryMaxRub]
            )
        }
    val signals = member.signals
    fun visible(text: String) = if (disclosed) text else employerText(text, row[CandidateProfiles.email], row[CandidateProfiles.phone],
        row[CandidateProfiles.fullName].takeUnless { privacy.fullName }.orEmpty())
    return CandidateCard(
        candidateId = candidateId.toString(),
        fullName = if (disclosed) row[CandidateProfiles.fullName] else signals.displayName,
        specialization = signals.specialization,
        grade = signals.assessedGrade,
        score = signals.testScore,
        assessedAt = signals.assessedAt?.toString(),
        category = signals.assessedGrade?.let { categoryLabel(signals.specialization, it) },
        industry = signals.industry,
        techStack = row[CandidateProfiles.techStack].map(::visible).takeIf { privacy.techStack },
        experience = visible(row[CandidateProfiles.experience]).takeIf { privacy.experience },
        targetRoles = row[CandidateProfiles.targetRoles].map(::visible).takeIf { privacy.experience },
        cvHistory = row[CandidateProfiles.cvHistory].map {
            it.copy(company = visible(it.company), role = visible(it.role), description = visible(it.description),
                startDate = visible(it.startDate), endDate = visible(it.endDate))
        }.takeIf { privacy.cvHistory },
        softSkills = row[CandidateProfiles.softSkills].map(::visible).takeIf { privacy.softSkills },
        achievements = member.achievements.takeIf { privacy.fspAchievements },
        fspLinked = signals.fspLinked.takeIf { privacy.fspAchievements },
        hiddenSections = listOfNotNull(
            "ФИО".takeUnless { privacy.fullName },
            "Стек технологий".takeUnless { privacy.techStack },
            "Опыт и желаемые роли".takeUnless { privacy.experience },
            "История работы".takeUnless { privacy.cvHistory },
            "Гибкие навыки".takeUnless { privacy.softSkills },
            "Достижения ФСП".takeUnless { privacy.fspAchievements }),
        lastActiveAt = signals.lastActiveAt?.toString(),
        contactsDisclosed = disclosed,
        contactEmail = row[CandidateProfiles.email].takeIf { disclosed },
        contactPhone = row[CandidateProfiles.phone].takeIf { disclosed },
        invitations = invitations,
        match = match,
    )
}

/** Latest invitation status per candidate for one employer (status tracking in the selection). */
internal fun Transaction.latestInvitationStatus(employerId: UUID): Map<UUID, String> =
    InvitationsTable.selectAll().where { InvitationsTable.employerId eq employerId }
        .orderBy(InvitationsTable.createdAt to SortOrder.DESC)
        .groupBy { it[InvitationsTable.candidateId] }
        .mapValues { it.value.first()[InvitationsTable.status] }

internal fun RankedCandidate.toDto(
    pool: Map<String, PoolCandidate>,
    invitationStatus: Map<UUID, String>
): CandidateMatch {
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
        factors = factors.map {
            MatchFactor(
                it.code,
                it.label,
                round1(it.weight),
                round2(it.value),
                round1(it.points),
                it.text
            )
        },
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
