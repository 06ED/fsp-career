package ru.fsp.career.assessment

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.fsp.career.AssessmentAttempts
import ru.fsp.career.CandidateProfiles
import ru.fsp.career.Option
import ru.fsp.career.bad
import ru.fsp.career.conflict
import ru.fsp.career.forbidden
import ru.fsp.career.missing
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

/** Engine version stored with every adaptive attempt (legacy five-question attempts are "legacy-v1"). */
internal const val ENGINE_VERSION = "cat-v1"
private const val MAX_VACANCY_TEXT = 6000

/** Seeds come from a CSPRNG so a candidate cannot predict the next instance. */
private val seeds = SecureRandom()

internal fun nowUtc(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

private fun reasonCode(reason: RetakeReason) = reason.name.lowercase().replace('_', '-')

private fun AssessmentCatalog.Entry.view() = AssessmentCatalogEntry(id, label, description)

internal fun catalogView() = AssessmentCatalogView(
    specializations = AssessmentCatalog.specializations.map { it.view() },
    industries = AssessmentCatalog.industries.map { it.view() },
    teamRoles = AssessmentCatalog.teamRoles.map { it.view() },
    workFormats = AssessmentCatalog.workFormats.map { it.view() },
    grades = AssessmentCatalog.grades.map { entry ->
        val grade = Grade.of(entry.id)!!
        AssessmentGradeView(entry.id, entry.label, entry.description, grade.cut, Grading.score(grade.cut))
    },
    topics = Topics.labels.map { (id, label) -> AssessmentCatalogEntry(id, label) },
    vacancies = DemoVacancies.all.map { AssessmentVacancyView(it.id, it.title, it.company, it.specialization, it.industry, it.grade, it.text) },
    testLength = Cat.TEST_LENGTH,
    timeLimitMinutes = Cat.EXPIRY_MINUTES,
    cooldownDays = RetakePolicy.COOLDOWN_DAYS,
    decisionMargin = Grading.K,
    familyCount = ItemBank.families.size,
)

internal fun requireSpecialization(value: String): String =
    value.trim().lowercase().takeIf { AssessmentCatalog.has(AssessmentCatalog.specializations, it) }
        ?: bad("specialization must be one of backend, frontend, data, product, design")

private fun vacancyText(vacancyId: String?, raw: String?, specialization: String?): Pair<DemoVacancy?, String?> {
    if (vacancyId != null && raw != null) bad("Pass either vacancyId or vacancyText, not both")
    val vacancy = vacancyId?.let { DemoVacancies.find(it) ?: missing("Vacancy not found") }
    if (vacancy != null && specialization != null && vacancy.specialization != specialization)
        bad("Vacancy belongs to another specialization")
    val text = vacancy?.text ?: raw?.trim()?.takeIf { it.isNotEmpty() }
    if (text != null && text.length > MAX_VACANCY_TEXT) bad("vacancyText must contain at most $MAX_VACANCY_TEXT characters")
    return vacancy to text
}

internal fun itemView(family: ItemFamily, instance: Generated) = AssessmentItemView(
    topic = family.topic,
    topicLabel = Topics.label(family.topic),
    prompt = instance.prompt,
    code = instance.code,
    language = instance.language,
    format = if (instance.isChoice) "choice" else "number",
    options = instance.options.mapIndexed { i, label -> Option(AnswerKey.optionIds[i], label) },
    unit = instance.unit,
)

private fun quotaViews(weights: Map<String, Double>, quotas: Map<String, Int>) =
    quotas.map { (topic, items) -> AssessmentTopicQuota(topic, Topics.label(topic), weights[topic] ?: 0.0, items) }

internal fun previewBlueprint(request: BlueprintPreviewRequest, rng: Random): BlueprintPreview {
    val explicit = request.specialization?.let(::requireSpecialization)
    val (vacancy, text) = vacancyText(request.vacancyId, request.vacancyText, explicit)
    val specialization = explicit ?: vacancy?.specialization ?: text?.let { Blueprints.detectSpecialization(it) }
        ?: bad("Specialization could not be detected; pass specialization")
    val target = request.targetGrade?.let { Grade.of(it) ?: bad("targetGrade must be junior, middle or senior") }
        ?: vacancy?.grade?.let(Grade::of) ?: Grade.MIDDLE
    val blueprint = Blueprints.blueprintFor(specialization, text)
    fun skillView(d: DetectedSkill) = AssessmentSkillView(d.skill.id, d.skill.label, d.skill.topics, d.hits, d.matched)
    return BlueprintPreview(
        specialization = specialization,
        detectedSpecialization = blueprint.detectedSpecialization,
        vacancyId = vacancy?.id,
        vacancyTitle = vacancy?.title,
        targetGrade = target.id,
        skills = blueprint.skills.map(::skillView),
        uncoveredSkills = blueprint.uncovered.map(::skillView),
        topics = quotaViews(blueprint.topicWeights, blueprint.quotas),
        sampleItems = Blueprints.samples(blueprint, target, 4, rng).map { (family, instance) -> itemView(family, instance) },
        poolSize = ItemBank.pool(specialization).size,
        testLength = Cat.TEST_LENGTH,
    )
}

/** Validated start request, prepared outside the transaction. */
internal class StartPlan(val survey: AssessmentSurvey, val target: Grade, val blueprint: Blueprint, val vacancy: DemoVacancy?)

internal fun planStart(request: StartAssessmentRequest): StartPlan {
    val s = request.survey
    val specialization = requireSpecialization(s.specialization)
    if (!AssessmentCatalog.has(AssessmentCatalog.industries, s.industry)) bad("Unknown industry")
    if (s.experienceYears !in 0..50) bad("experienceYears must be between 0 and 50")
    if (s.teamRoles.size > AssessmentCatalog.teamRoles.size || s.teamRoles.toSet().size != s.teamRoles.size ||
        s.teamRoles.any { !AssessmentCatalog.has(AssessmentCatalog.teamRoles, it) }) bad("Invalid teamRoles")
    if (!AssessmentCatalog.has(AssessmentCatalog.workFormats, s.workFormat)) bad("Unknown workFormat")
    val target = Grade.of(request.targetGrade) ?: bad("targetGrade must be junior, middle or senior")
    val (vacancy, text) = vacancyText(request.vacancyId, request.vacancyText, specialization)
    return StartPlan(s.copy(specialization = specialization), target, Blueprints.blueprintFor(specialization, text), vacancy)
}

internal sealed interface StartOutcome {
    class Started(val attempt: AssessmentAttemptView) : StartOutcome
    class Blocked(val conflict: RetakeConflict) : StartOutcome
}

private fun Transaction.openAttempt(candidate: UUID): ResultRow? = AssessmentAttempts.selectAll().where {
    (AssessmentAttempts.candidateId eq candidate) and AssessmentAttempts.submittedAt.isNull()
}.forUpdate().singleOrNull()

private fun Transaction.items(attempt: UUID): List<ResultRow> = AssessmentItems.selectAll()
    .where { AssessmentItems.attemptId eq attempt }.orderBy(AssessmentItems.position).toList()

private fun Transaction.completed(candidate: UUID, specialization: String? = null): List<ResultRow> =
    AssessmentAttempts.selectAll().where {
        val base = (AssessmentAttempts.candidateId eq candidate) and AssessmentAttempts.submittedAt.isNotNull()
        if (specialization == null) base else base and (AssessmentAttempts.specialization eq specialization)
    }.orderBy(AssessmentAttempts.submittedAt to SortOrder.DESC).toList()

private fun ResultRow.toPast() = PastAttempt(
    target = Grade.of(this[AssessmentAttempts.targetGrade]),
    passed = this[AssessmentAttempts.passed],
    confident = this[AssessmentAttempts.confident],
    confirmed = Grade.of(this[AssessmentAttempts.grade]) ?: Grade.JUNIOR,
    submittedAt = this[AssessmentAttempts.submittedAt]!!.toInstant(),
)

private fun Transaction.optionViews(candidate: UUID, specialization: String, now: OffsetDateTime) =
    RetakePolicy.options(completed(candidate, specialization).map { it.toPast() }, now.toInstant()).map {
        AssessmentGradeOptionView(it.grade.id, it.allowed, reasonCode(it.reason), it.reason.message, it.availableAt?.toString())
    }

internal fun Transaction.eligibility(candidate: UUID, specialization: String, now: OffsetDateTime): AssessmentEligibilityView {
    val done = completed(candidate, specialization)
    val best = done.maxWithOrNull(compareBy<ResultRow> { Grade.of(it[AssessmentAttempts.grade])?.ordinal ?: 0 }.thenBy { it[AssessmentAttempts.score] ?: 0 })
    return AssessmentEligibilityView(
        specialization = specialization,
        confirmedGrade = best?.get(AssessmentAttempts.grade),
        score = best?.get(AssessmentAttempts.score),
        lastAttemptAt = done.firstOrNull()?.get(AssessmentAttempts.submittedAt)?.toInstant()?.toString(),
        options = optionViews(candidate, specialization, now),
        openAttemptId = openAttempt(candidate)?.get(AssessmentAttempts.id)?.toString(),
        cooldownDays = RetakePolicy.COOLDOWN_DAYS,
    )
}

/**
 * Closes an open attempt whose 30 minutes are over. The item on screen counts as wrong and the attempt is
 * scored on what was answered — abandoning a test can therefore never be better than finishing it, and it
 * starts the same retake clock. Open legacy attempts are discarded: their submit endpoint no longer exists.
 */
internal fun Transaction.closeExpired(candidate: UUID, now: OffsetDateTime): AssessmentResultView? {
    val open = openAttempt(candidate) ?: return null
    if (open[AssessmentAttempts.engineVersion] != ENGINE_VERSION) {
        AssessmentAttempts.deleteWhere { AssessmentAttempts.id eq open[AssessmentAttempts.id] }
        return null
    }
    return if (open[AssessmentAttempts.expiresAt]!!.isBefore(now)) finalize(open, now, expired = true) else null
}

internal fun Transaction.currentAttempt(candidate: UUID): AssessmentAttemptView? = openAttempt(candidate)?.let { attemptView(it) }

internal fun Transaction.startAttempt(candidate: UUID, plan: StartPlan, now: OffsetDateTime): StartOutcome {
    val profile = CandidateProfiles.selectAll().where { CandidateProfiles.accountId eq candidate }.single()
    if (!profile[CandidateProfiles.processingConsent]) forbidden("Grant processing consent before assessment")
    closeExpired(candidate, now)
    openAttempt(candidate)?.let {
        return StartOutcome.Blocked(RetakeConflict("Finish the open assessment first", "open-attempt", openAttemptId = it[AssessmentAttempts.id].toString()))
    }
    val specialization = plan.survey.specialization
    val option = RetakePolicy.option(completed(candidate, specialization).map { it.toPast() }, plan.target, now.toInstant())
    if (!option.allowed) return StartOutcome.Blocked(RetakeConflict(
        "Assessment for this grade is not available now", reasonCode(option.reason), option.availableAt?.toString(),
    ))
    // The survey is the source of truth for the candidate's category fields.
    CandidateProfiles.update({ CandidateProfiles.accountId eq candidate }) {
        it[CandidateProfiles.specialization] = specialization
        it[industry] = plan.survey.industry
        it[desiredGrade] = plan.target.id
        it[updatedAt] = now
    }
    val attemptId = UUID.randomUUID()
    AssessmentAttempts.insert {
        it[id] = attemptId
        it[candidateId] = candidate
        it[AssessmentAttempts.specialization] = specialization
        it[questionIds] = emptyList()
        it[startedAt] = now
        it[targetGrade] = plan.target.id
        it[industry] = plan.survey.industry
        it[survey] = plan.survey
        it[vacancyId] = plan.vacancy?.id
        it[vacancyTitle] = plan.vacancy?.title
        it[blueprint] = BlueprintSnapshot(plan.blueprint.quotas, plan.blueprint.topicWeights, plan.blueprint.skills.map { d -> d.skill.id })
        it[plannedItems] = Cat.TEST_LENGTH
        it[expiresAt] = now.plusMinutes(Cat.EXPIRY_MINUTES)
        it[engineVersion] = ENGINE_VERSION
    }
    val row = AssessmentAttempts.selectAll().where { AssessmentAttempts.id eq attemptId }.single()
    serveNext(row, emptyList(), now)
    return StartOutcome.Started(attemptView(row))
}

private fun responsesOf(items: List<ResultRow>): List<Pair<ItemParams, Boolean>> = items.mapNotNull { item ->
    val family = ItemBank.family(item[AssessmentItems.familyId]) ?: return@mapNotNull null
    item[AssessmentItems.correct]?.let { family.params to it }
}

/** Picks and stores the next item instance; false when the pool is exhausted. */
private fun Transaction.serveNext(attempt: ResultRow, served: List<ResultRow>, now: OffsetDateTime): Boolean {
    val pool = ItemBank.pool(attempt[AssessmentAttempts.specialization])
    val target = Grade.of(attempt[AssessmentAttempts.targetGrade])!!
    val administered = served.mapNotNull { ItemBank.family(it[AssessmentItems.familyId]) }
    val theta = Cat.routingEstimate(target, LikelihoodGrid.of(responsesOf(served))).theta
    val counter = AssessmentItems.familyId.count()
    val exposure = AssessmentItems.select(AssessmentItems.familyId, counter)
        .where { AssessmentItems.familyId inList pool.map { it.id } }
        .groupBy(AssessmentItems.familyId)
        .associate { it[AssessmentItems.familyId] to it[counter] }
    val family = Cat.nextFamily(pool, attempt[AssessmentAttempts.blueprint].quotas, administered, theta,
        { exposure[it] ?: 0L }, Random(seeds.nextLong())) ?: return false
    val seed = seeds.nextLong()
    val instance = family.instance(seed)
    AssessmentItems.insert {
        it[attemptId] = attempt[AssessmentAttempts.id]
        it[position] = served.size
        it[familyId] = family.id
        it[AssessmentItems.seed] = seed
        it[view] = itemView(family, instance)
        it[answerKey] = instance.answer
        it[tolerance] = instance.tolerance
        it[servedAt] = now
    }
    return true
}

private fun Transaction.attemptView(attempt: ResultRow): AssessmentAttemptView {
    val served = items(attempt[AssessmentAttempts.id])
    val current = served.lastOrNull()?.takeIf { it[AssessmentItems.answeredAt] == null }
    val snapshot = attempt[AssessmentAttempts.blueprint]
    return AssessmentAttemptView(
        attemptId = attempt[AssessmentAttempts.id].toString(),
        specialization = attempt[AssessmentAttempts.specialization],
        industry = attempt[AssessmentAttempts.industry],
        targetGrade = attempt[AssessmentAttempts.targetGrade] ?: "",
        vacancyId = attempt[AssessmentAttempts.vacancyId],
        vacancyTitle = attempt[AssessmentAttempts.vacancyTitle],
        startedAt = attempt[AssessmentAttempts.startedAt].toInstant().toString(),
        expiresAt = (attempt[AssessmentAttempts.expiresAt] ?: attempt[AssessmentAttempts.startedAt].plusMinutes(Cat.EXPIRY_MINUTES)).toInstant().toString(),
        position = served.count { it[AssessmentItems.answeredAt] != null },
        total = attempt[AssessmentAttempts.plannedItems],
        item = current?.get(AssessmentItems.view),
        blueprint = quotaViews(snapshot.weights, snapshot.quotas),
    )
}

internal fun Transaction.answerItem(candidate: UUID, attemptId: UUID, request: AssessmentAnswerRequest, now: OffsetDateTime): AssessmentAnswerOutcome {
    val attempt = AssessmentAttempts.selectAll().where {
        (AssessmentAttempts.id eq attemptId) and (AssessmentAttempts.candidateId eq candidate)
    }.forUpdate().singleOrNull() ?: missing("Assessment not found")
    if (attempt[AssessmentAttempts.submittedAt] != null) conflict("Assessment already completed")
    if (attempt[AssessmentAttempts.engineVersion] != ENGINE_VERSION) conflict("Legacy assessment cannot be continued")
    if (attempt[AssessmentAttempts.expiresAt]!!.isBefore(now))
        return AssessmentAnswerOutcome(done = true, result = finalize(attempt, now, expired = true))
    val served = items(attemptId)
    val current = served.lastOrNull()?.takeIf { it[AssessmentItems.answeredAt] == null } ?: conflict("No item awaits an answer")
    if (request.position != current[AssessmentItems.position]) conflict("Answer the current item (position ${current[AssessmentItems.position]})")
    val answer = request.answer.trim()
    if (answer.isEmpty() || answer.length > 64) bad("answer must contain 1..64 characters")
    val view = current[AssessmentItems.view]
    val choice = view.format == "choice"
    if (choice && view.options.none { it.id == answer }) bad("Unknown option id")
    if (!choice && AnswerKey.parseNumber(answer) == null) bad("answer must be a number")
    val correct = AnswerKey.check(choice, current[AssessmentItems.answerKey], current[AssessmentItems.tolerance], answer)
    AssessmentItems.update({ (AssessmentItems.attemptId eq attemptId) and (AssessmentItems.position eq current[AssessmentItems.position]) }) {
        it[response] = answer
        it[AssessmentItems.correct] = correct
        it[answeredAt] = now
    }
    val answered = items(attemptId)
    val more = answered.size < attempt[AssessmentAttempts.plannedItems] && serveNext(attempt, answered, now)
    // Per-item correctness is deliberately not returned: it would let a candidate probe the key mid-test.
    return if (more) AssessmentAnswerOutcome(done = false, attempt = attemptView(attempt))
    else AssessmentAnswerOutcome(done = true, result = finalize(attempt, now, expired = false))
}

private fun Transaction.finalize(attempt: ResultRow, now: OffsetDateTime, expired: Boolean): AssessmentResultView {
    val attemptId = attempt[AssessmentAttempts.id]
    if (expired) {
        AssessmentItems.update({ (AssessmentItems.attemptId eq attemptId) and AssessmentItems.answeredAt.isNull() }) {
            it[correct] = false
        }
    }
    val served = items(attemptId)
    val target = Grade.of(attempt[AssessmentAttempts.targetGrade])!!
    val responses = responsesOf(served).toMutableList()
    if (expired) {
        // Items never reached count as wrong answers at the declared level; otherwise the N(0, 1) prior alone
        // would score an abandoned attempt near the population mean.
        repeat(attempt[AssessmentAttempts.plannedItems] - served.size) { responses += ItemParams(1.3, target.anchor) to false }
    }
    val estimate = Cat.finalEstimate(LikelihoodGrid.of(responses))
    // An attempt that ran out of time never confirms a grade or unlocks an upgrade.
    val decision = Grading.decide(target, estimate).let { if (expired) it.copy(passed = false, confident = false) else it }
    val specialization = attempt[AssessmentAttempts.specialization]
    val candidate = attempt[AssessmentAttempts.candidateId]
    val previous = completed(candidate, specialization).mapNotNull { Grade.of(it[AssessmentAttempts.grade]) }.maxOrNull()
    val confirmed = Grading.confirmedAfter(previous, target, decision.passed)
    AssessmentAttempts.update({ AssessmentAttempts.id eq attemptId }) {
        it[submittedAt] = now
        it[score] = decision.score
        it[grade] = confirmed.id
        it[theta] = estimate.theta
        it[thetaSe] = estimate.se
        it[passed] = decision.passed
        it[confident] = decision.confident
        it[AssessmentAttempts.expired] = expired
    }
    val row = AssessmentAttempts.selectAll().where { AssessmentAttempts.id eq attemptId }.single()
    return resultView(row, served, previous).copy(nextOptions = optionViews(candidate, specialization, now))
}

private fun summary(row: ResultRow, previous: Grade?): String {
    val target = Grade.of(row[AssessmentAttempts.targetGrade]) ?: return "Тест старого формата: 5 вопросов, оценка по доле верных ответов."
    val confirmed = Grade.of(row[AssessmentAttempts.grade]) ?: Grade.JUNIOR
    val expiredNote = if (row[AssessmentAttempts.expired]) " Время попытки истекло — засчитаны данные ответы." else ""
    return if (row[AssessmentAttempts.passed] == true) {
        val upgrade = target.next()?.takeIf { row[AssessmentAttempts.confident] == true }
        "Уровень ${target.label} подтверждён." + (upgrade?.let { " Уверенный результат: можно сразу пройти тест на ${it.label}." } ?: "") + expiredNote
    } else {
        val kept = when {
            previous != null -> " Подтверждённый ранее грейд ${confirmed.label} сохранён."
            target == Grade.JUNIOR -> " Категория по умолчанию — Junior."
            else -> " Категория по умолчанию — Junior; можно сразу пройти тест на ${target.previous()!!.label}."
        }
        "Уровень ${target.label} пока не подтверждён.$kept$expiredNote"
    }
}

private fun resultView(row: ResultRow, served: List<ResultRow>, previous: Grade?): AssessmentResultView {
    val legacy = row[AssessmentAttempts.engineVersion] != ENGINE_VERSION
    val legacyTotal = row[AssessmentAttempts.questionIds].size
    val survey = row[AssessmentAttempts.survey].takeIf { it.specialization.isNotBlank() }
    return AssessmentResultView(
        attemptId = row[AssessmentAttempts.id].toString(),
        specialization = row[AssessmentAttempts.specialization],
        industry = row[AssessmentAttempts.industry],
        targetGrade = row[AssessmentAttempts.targetGrade],
        passed = row[AssessmentAttempts.passed],
        confident = row[AssessmentAttempts.confident],
        theta = row[AssessmentAttempts.theta],
        thetaSe = row[AssessmentAttempts.thetaSe],
        score = row[AssessmentAttempts.score]!!,
        grade = row[AssessmentAttempts.grade]!!,
        previousGrade = previous?.id,
        answered = if (legacy) legacyTotal else served.count { it[AssessmentItems.answeredAt] != null },
        correct = if (legacy) row[AssessmentAttempts.score]!! * legacyTotal / 100 else served.count { it[AssessmentItems.correct] == true },
        total = if (legacy) legacyTotal else row[AssessmentAttempts.plannedItems],
        expired = row[AssessmentAttempts.expired],
        startedAt = row[AssessmentAttempts.startedAt].toInstant().toString(),
        submittedAt = row[AssessmentAttempts.submittedAt]!!.toInstant().toString(),
        survey = survey,
        vacancyId = row[AssessmentAttempts.vacancyId],
        vacancyTitle = row[AssessmentAttempts.vacancyTitle],
        method = row[AssessmentAttempts.engineVersion],
        summary = summary(row, previous),
    )
}

/** Completed attempts newest first; previousGrade is the confirmed grade in that specialization before each attempt. */
internal fun Transaction.history(candidate: UUID): List<AssessmentResultView> {
    val rows = completed(candidate)
    val served = if (rows.isEmpty()) emptyMap() else AssessmentItems.selectAll()
        .where { AssessmentItems.attemptId inList rows.map { it[AssessmentAttempts.id] } }
        .groupBy { it[AssessmentItems.attemptId] }
    val best = mutableMapOf<String, Grade>()
    val previous = mutableMapOf<UUID, Grade?>()
    for (row in rows.asReversed()) {
        val spec = row[AssessmentAttempts.specialization]
        previous[row[AssessmentAttempts.id]] = best[spec]
        Grade.of(row[AssessmentAttempts.grade])?.let { g -> best[spec] = Grade.max(best[spec], g)!! }
    }
    return rows.map { resultView(it, served[it[AssessmentAttempts.id]].orEmpty(), previous[it[AssessmentAttempts.id]]) }
}
