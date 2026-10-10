package ru.fsp.career.assessment.domain

import ru.fsp.career.shared.domain.*
import kotlin.random.Random

internal const val ENGINE_VERSION = "cat-v1"
private const val MAX_VACANCY_TEXT = 6000
internal fun reasonCode(reason: RetakeReason) = reason.name.lowercase().replace('_', '-')
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
    testLength = Cat.TEST_LENGTH, timeLimitMinutes = Cat.EXPIRY_MINUTES,
    cooldownDays = RetakePolicy.COOLDOWN_DAYS, decisionMargin = Grading.K, familyCount = ItemBank.families.size,
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
    topic = family.topic, topicLabel = Topics.label(family.topic), prompt = instance.prompt,
    code = instance.code, language = instance.language,
    format = if (instance.isChoice) "choice" else "number",
    options = instance.options.mapIndexed { i, label -> Option(AnswerKey.optionIds[i], label) }, unit = instance.unit,
)
internal fun quotaViews(weights: Map<String, Double>, quotas: Map<String, Int>) =
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
        specialization = specialization, detectedSpecialization = blueprint.detectedSpecialization,
        vacancyId = vacancy?.id, vacancyTitle = vacancy?.title, targetGrade = target.id,
        skills = blueprint.skills.map(::skillView), uncoveredSkills = blueprint.uncovered.map(::skillView),
        topics = quotaViews(blueprint.topicWeights, blueprint.quotas),
        sampleItems = Blueprints.samples(blueprint, target, 4, rng).map { (family, instance) -> itemView(family, instance) },
        poolSize = ItemBank.pool(specialization).size, testLength = Cat.TEST_LENGTH,
    )
}

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
    if (request.vacancyId != null || request.vacancyText != null)
        bad("Qualification tests are category-based; vacancy tasks are separate")
    return StartPlan(s.copy(specialization = specialization), target, Blueprints.blueprintFor(specialization, null), null)
}
internal sealed interface StartOutcome {
    class Started(val attempt: AssessmentAttemptView) : StartOutcome
    class Blocked(val conflict: RetakeConflict) : StartOutcome
}
