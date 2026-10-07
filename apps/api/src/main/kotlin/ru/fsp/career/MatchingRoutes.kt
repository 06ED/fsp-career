package ru.fsp.career

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.fsp.career.fsp.FspIds
import ru.fsp.career.fsp.FspOwnership
import ru.fsp.career.fsp.FspPointsPolicy
import ru.fsp.career.fsp.FspRecord
import ru.fsp.career.fsp.FspRegistry
import ru.fsp.career.fsp.MockFspRegistry
import ru.fsp.career.matching.CandidateFilter
import ru.fsp.career.matching.CategoryStat
import ru.fsp.career.matching.MatchingEngine
import ru.fsp.career.matching.NeedQuery
import ru.fsp.career.matching.PreparedPool
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.roundToInt

private const val DEFAULT_LIMIT = 100
private const val MAX_LIMIT = 500

private data class SearchParams(val filter: CandidateFilter, val limit: Int)

/** Parses and validates the shared search filters (400 on anything malformed). */
private fun searchParams(params: Parameters): SearchParams {
    var specialization = params["specialization"]?.trim()?.takeIf { it.isNotBlank() }
    if (specialization != null && specialization.length > 100) bad("Specialization too long")
    var gradeFilter = params["grade"]?.trim()?.takeIf { it.isNotBlank() }?.let { grade(it) }
    params["category"]?.trim()?.takeIf { it.isNotBlank() }?.let { category ->
        val parts = category.split(':')
        if (parts.size != 2 || parts[0].isBlank() || parts[0].length > 100) bad("category must look like backend:middle")
        specialization = parts[0].trim()
        gradeFilter = grade(parts[1].trim())
    }
    val stack = params["techStack"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    if (stack.size > 10 || stack.any { it.length > 100 }) bad("Invalid techStack filter")
    fun flag(name: String) = params[name]?.let { it.toBooleanStrictOrNull() ?: bad("$name must be true or false") } ?: false
    val industry = params["industry"]?.trim()?.takeIf { it.isNotBlank() }
    if (industry != null && industry.length > 100) bad("Industry filter too long")
    val query = params["q"]?.trim()?.takeIf { it.isNotBlank() }
    if (query != null && query.length > 300) bad("q must contain at most 300 characters")
    val limit = params["limit"]?.let { raw -> raw.toIntOrNull()?.takeIf { it in 1..MAX_LIMIT } ?: bad("limit must be 1..$MAX_LIMIT") } ?: DEFAULT_LIMIT
    return SearchParams(
        CandidateFilter(
            specialization = specialization,
            grade = gradeFilter,
            stack = stack,
            fspOnly = flag("verifiedAchievements") || flag("fsp"),
            industry = industry,
            query = query,
            allowLowerGrade = flag("allowLowerGrade"),
        ),
        limit,
    )
}

private fun CategoryStat.toDto() = MatchCategory(
    key = "$specialization:$grade",
    specialization = specialization,
    grade = grade,
    label = categoryLabel(specialization, grade),
    candidates = candidates,
    withFsp = withFsp,
    avgTestScore = avgTestScore.roundToInt(),
    avgStrength = avgStrength.roundToInt(),
    fit = fit,
    recommended = recommended,
    reason = reason,
    avgTextRelevance = avgTextRelevance?.let { (it * 1000).roundToInt() / 1000.0 },
)

private fun Need.toQuery() = NeedQuery(specialization, minGrade, industry, techStack, description)

fun Route.matchingRoutes(db: Database, audience: String, registry: FspRegistry = MockFspRegistry(db)) {
    // Need-based selection: ranked by relevance to the employer's described need, with explanations.
    get("/matches") {
        val sub = call.role(audience, "employer")
        val (filter, limit) = searchParams(call.request.queryParameters)
        call.respond(db.tx {
            val employer = account(sub, "employer")
            val need = needFrom(EmployerNeeds.selectAll().where { EmployerNeeds.accountId eq employer }.single())
            if (need.specialization.isBlank() && filter.specialization == null)
                bad("Set employer needs first or provide specialization")
            val pool = publishedCandidates()
            val byId = pool.associateBy { it.signals.id }
            val ranking = MatchingEngine().rankForNeed(need.toQuery(), PreparedPool(pool.map { it.signals }), filter)
            val statuses = latestInvitationStatus(employer)
            MatchResponse(
                mode = "need",
                need = MatchNeed(
                    specialization = filter.specialization ?: need.specialization,
                    grade = filter.grade ?: need.minGrade,
                    industry = need.industry,
                    techStack = need.techStack,
                    description = need.description,
                    query = filter.query.orEmpty(),
                ),
                total = ranking.results.size,
                categories = ranking.categories.map { it.toDto() },
                matches = ranking.results.take(limit).map { it.toDto(byId, statuses) },
                formula = needFormula(),
            )
        })
    }

    // Whole candidate bank: confirmed categories, ordered by confirmed-profile strength.
    get("/bank/candidates") {
        val sub = call.role(audience, "employer")
        val (filter, limit) = searchParams(call.request.queryParameters)
        call.respond(db.tx {
            val employer = account(sub, "employer")
            val pool = publishedCandidates()
            val byId = pool.associateBy { it.signals.id }
            val ranking = MatchingEngine().browse(PreparedPool(pool.map { it.signals }), filter)
            val statuses = latestInvitationStatus(employer)
            MatchResponse(
                mode = "bank",
                total = ranking.results.size,
                categories = ranking.categories.map { it.toDto() },
                matches = ranking.results.take(limit).map { it.toDto(byId, statuses) },
                formula = bankFormula(),
            )
        })
    }

    // Employer-facing candidate card. Privacy-filtered; contacts only after disclosure.
    get("/bank/candidates/{id}") {
        val sub = call.role(audience, "employer")
        val candidateId = uuid(call.parameters["id"] ?: bad("Candidate ID required"))
        call.respond(db.tx {
            val employer = account(sub, "employer")
            val pool = publishedCandidates()
            val disclosed = candidateId in disclosedCandidates(employer, listOf(candidateId))
            val published = pool.firstOrNull { it.accountId == candidateId }
            // A candidate who later hid the profile stays visible only to employers they already shared contacts with.
            val member = published ?: if (disclosed) {
                CandidateProfiles.selectAll().where {
                    (CandidateProfiles.accountId eq candidateId) and (CandidateProfiles.processingConsent eq true)
                }.toList().let { poolCandidates(it) }.singleOrNull()
            } else null
            member ?: missing("Published candidate not found")
            val row = member.row
            val privacy = member.privacy
            val statuses = latestInvitationStatus(employer)
            val need = needFrom(EmployerNeeds.selectAll().where { EmployerNeeds.accountId eq employer }.single())
            val match = if (published != null && need.specialization.isNotBlank()) {
                val byId = pool.associateBy { it.signals.id }
                MatchingEngine().explain(need.toQuery(), PreparedPool(pool.map { it.signals }), candidateId.toString())
                    ?.toDto(byId, statuses)
            } else null
            val invitations = InvitationsTable.selectAll().where {
                (InvitationsTable.employerId eq employer) and (InvitationsTable.candidateId eq candidateId)
            }.orderBy(InvitationsTable.createdAt to SortOrder.DESC).map {
                InvitationBrief(it[InvitationsTable.id].toString(), it[InvitationsTable.status],
                    it[InvitationsTable.createdAt].toInstant().toString(), it[InvitationsTable.salaryMinRub], it[InvitationsTable.salaryMaxRub])
            }
            val signals = member.signals
            CandidateCard(
                candidateId = candidateId.toString(),
                fullName = if (disclosed) row[CandidateProfiles.fullName] else signals.displayName,
                specialization = signals.specialization,
                grade = signals.assessedGrade,
                score = signals.testScore,
                assessedAt = signals.assessedAt?.toString(),
                category = signals.assessedGrade?.let { categoryLabel(signals.specialization, it) },
                industry = signals.industry,
                techStack = row[CandidateProfiles.techStack].takeIf { privacy.techStack },
                experience = row[CandidateProfiles.experience].takeIf { privacy.experience },
                targetRoles = row[CandidateProfiles.targetRoles].takeIf { privacy.experience },
                cvHistory = row[CandidateProfiles.cvHistory].takeIf { privacy.cvHistory },
                softSkills = row[CandidateProfiles.softSkills].takeIf { privacy.softSkills },
                achievements = member.achievements.takeIf { privacy.fspAchievements },
                fspLinked = signals.fspLinked.takeIf { privacy.fspAchievements },
                hiddenSections = hiddenSections(privacy),
                lastActiveAt = signals.lastActiveAt?.toString(),
                contactsDisclosed = disclosed,
                contactEmail = row[CandidateProfiles.email].takeIf { disclosed },
                contactPhone = row[CandidateProfiles.phone].takeIf { disclosed },
                invitations = invitations,
                match = match,
            )
        })
    }

    get("/candidates/me/privacy") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx {
            val id = account(sub, "candidate")
            CandidateProfiles.selectAll().where { CandidateProfiles.accountId eq id }.single()[CandidateProfiles.privacy]
        })
    }

    put("/candidates/me/privacy") {
        val sub = call.role(audience, "candidate")
        val privacy = call.receive<CandidatePrivacy>()
        call.respond(db.tx {
            val id = account(sub, "candidate")
            if (!profile(id).processingConsent) forbidden("Grant processing consent before changing privacy settings")
            CandidateProfiles.update({ CandidateProfiles.accountId eq id }) { it[CandidateProfiles.privacy] = privacy }
            privacy
        })
    }

    get("/candidates/me/fsp") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx { fspStatus(account(sub, "candidate"), registry) })
    }

    post("/candidates/me/fsp") {
        val sub = call.role(audience, "candidate")
        val request = call.receive<FspLinkRequest>()
        val fspId = FspIds.normalize(request.fspId) ?: bad("FSP ID must look like FSP-123456")
        val verifiedEmail = call.verifiedEmail().takeIf { it.isNotBlank() }
            ?: forbidden("A verified e-mail is required to link an FSP ID")
        db.tx {
            if (!profile(account(sub, "candidate")).processingConsent) forbidden("Grant processing consent before linking FSP ID")
        }
        val record = registry.lookup(fspId)
        // Same answer for "unknown ID" and "ID of somebody else": the endpoint must not confirm which IDs exist.
        if (record == null || !FspOwnership.verify(record, verifiedEmail))
            missing("FSP participant not found for your verified e-mail")
        val status = try {
            db.tx {
                val id = account(sub, "candidate")
                val owner = CandidateProfiles.selectAll().where { CandidateProfiles.fspId eq fspId }.singleOrNull()
                if (owner != null && owner[CandidateProfiles.accountId] != id) conflict("FSP ID already linked to another account")
                importFsp(id, record, registry)
                fspStatus(id, registry)
            }
        } catch (error: ExposedSQLException) {
            if (error.sqlState == "23505") conflict("FSP ID already linked to another account") else throw error
        }
        call.respond(status)
    }

    delete("/candidates/me/fsp") {
        val sub = call.role(audience, "candidate")
        call.respond(db.tx {
            val id = account(sub, "candidate")
            Achievements.deleteWhere { (Achievements.candidateId eq id) and (Achievements.sourceName eq registry.source) }
            CandidateProfiles.update({ CandidateProfiles.accountId eq id }) {
                it[fspId] = null
                it[fspLinkedAt] = null
            }
            fspStatus(id, registry)
        })
    }
}

private fun hiddenSections(privacy: CandidatePrivacy) = listOfNotNull(
    "ФИО".takeUnless { privacy.fullName },
    "Стек технологий".takeUnless { privacy.techStack },
    "Опыт и желаемые роли".takeUnless { privacy.experience },
    "История работы".takeUnless { privacy.cvHistory },
    "Гибкие навыки".takeUnless { privacy.softSkills },
    "Достижения ФСП".takeUnless { privacy.fspAchievements },
)

/** Replaces previously imported results of this registry (idempotent re-sync) and binds the FSP ID. */
internal fun Transaction.importFsp(candidateId: UUID, record: FspRecord, registry: FspRegistry) {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    Achievements.deleteWhere { (Achievements.candidateId eq candidateId) and (Achievements.sourceName eq registry.source) }
    record.results.forEach { result ->
        Achievements.insert {
            it[Achievements.candidateId] = candidateId
            it[title] = FspPointsPolicy.title(result).take(500)
            it[issuer] = registry.issuerLabel
            it[points] = FspPointsPolicy.points(result.level, result.result)
            it[verifiedAt] = now
            it[sourceName] = registry.source
            it[externalId] = result.externalId
            it[eventName] = result.eventName
            it[discipline] = result.discipline
            it[eventDate] = result.eventDate
            it[level] = result.level
            it[place] = result.place
            it[resultRole] = result.result
        }
    }
    CandidateProfiles.update({ CandidateProfiles.accountId eq candidateId }) {
        it[fspId] = record.participant.fspId
        it[fspLinkedAt] = now
    }
}

internal fun Transaction.fspStatus(candidateId: UUID, registry: FspRegistry): FspStatus {
    val row = CandidateProfiles.selectAll().where { CandidateProfiles.accountId eq candidateId }.single()
    val achievements = Achievements.selectAll().where {
        (Achievements.candidateId eq candidateId) and (Achievements.sourceName eq registry.source)
    }.orderBy(Achievements.eventDate to SortOrder.DESC).map(::verifiedAchievement)
    val linkedId = row[CandidateProfiles.fspId]
    val message = when {
        linkedId == null -> "ФСП ID не привязан. Профиль участвует в подборе и без истории ФСП — категория и грейд от этого не зависят."
        achievements.isEmpty() -> "ФСП ID привязан, но в реестре нет результатов соревнований. Работодатель увидит «результатов в реестре нет» — это не штраф."
        else -> "Импортировано результатов: ${achievements.size}. Работодатели видят их как подтверждённые достижения ФСП."
    }
    return FspStatus(
        linked = linkedId != null,
        fspId = linkedId,
        linkedAt = row[CandidateProfiles.fspLinkedAt]?.toInstant()?.toString(),
        registry = registry.displayName,
        achievements = achievements,
        totalPoints = achievements.sumOf { it.points },
        message = message,
    )
}
