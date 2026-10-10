package ru.fsp.career.matching.http

import io.ktor.http.Parameters
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import ru.fsp.career.identity.http.role
import ru.fsp.career.matching.application.MatchingService
import ru.fsp.career.matching.domain.CandidateFilter
import ru.fsp.career.shared.domain.*
import io.ktor.server.plugins.di.*

private const val DEFAULT_LIMIT = 100
private const val MAX_LIMIT = 500
private data class SearchParams(val filter: CandidateFilter, val limit: Int, val offset: Int)
private fun searchParams(params: Parameters): SearchParams {
    var specialization = params["specialization"]?.trim()?.takeIf { it.isNotBlank() }
    if (specialization != null && specialization.length > 100) bad("Specialization too long")
    var gradeFilter = params["grade"]?.trim()?.takeIf { it.isNotBlank() }?.let { grade(it) }
    params["category"]?.trim()?.takeIf { it.isNotBlank() }?.let { category ->
        val parts = category.split(':')
        if (parts.size != 2 || parts[0].isBlank() || parts[0].length > 100) bad("category must look like backend:middle")
        specialization = parts[0].trim(); gradeFilter = grade(parts[1].trim())
    }
    val stack = params["techStack"]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    if (stack.size > 10 || stack.any { it.length > 100 }) bad("Invalid techStack filter")
    fun flag(name: String) = params[name]?.let { it.toBooleanStrictOrNull() ?: bad("$name must be true or false") } ?: false
    val industry = params["industry"]?.trim()?.takeIf { it.isNotBlank() }
    if (industry != null && industry.length > 100) bad("Industry filter too long")
    val query = params["q"]?.trim()?.takeIf { it.isNotBlank() }
    if (query != null && query.length > 300) bad("q must contain at most 300 characters")
    val limit = params["limit"]?.let { raw -> raw.toIntOrNull()?.takeIf { it in 1..MAX_LIMIT } ?: bad("limit must be 1..$MAX_LIMIT") } ?: DEFAULT_LIMIT
    val offset = params["offset"]?.let { it.toIntOrNull()?.takeIf { it >= 0 } ?: bad("offset must be a non-negative integer") } ?: 0
    return SearchParams(CandidateFilter(specialization, gradeFilter, stack, flag("verifiedAchievements") || flag("fsp"), industry, query, flag("allowLowerGrade")), limit, offset)
}
internal fun Route.matchingRoutes(audience: String) {
    val service: MatchingService by application.dependencies
    get("/matches") {
        val sub = call.role(audience, "employer")
        val (filter, limit, offset) = searchParams(call.request.queryParameters)
        call.respond(service.need(sub, filter, limit, offset))
    }
    get("/bank/candidates") {
        val sub = call.role(audience, "employer")
        val (filter, limit, offset) = searchParams(call.request.queryParameters)
        call.respond(service.browse(sub, filter, limit, offset))
    }
    get("/bank/candidates/{id}") { call.respond(service.card(call.role(audience, "employer"), uuid(call.parameters["id"] ?: bad("Candidate ID required")))) }
}
