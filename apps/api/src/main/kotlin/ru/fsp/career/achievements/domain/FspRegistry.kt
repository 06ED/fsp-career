package ru.fsp.career.achievements.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * FSP (Федерация спортивного программирования) participant registry connector.
 *
 * The spec gives no FSP API or data model on purpose. We decided which facts are worth importing:
 *   participant: fspId, e-mail used in FSP ID, full name, region;
 *   result: event, discipline, date, level (regional / federal / international), place, outcome
 *           (winner / prize / finalist / participant), team.
 * These are the facts an employer can interpret quickly ("призёр всероссийского финала 2025") and that
 * map onto a transparent points policy (see FspPointsPolicy).
 *
 * `FspRegistry` is the integration seam. `MockFspRegistry` reads synthetic seed tables; a production
 * implementation would call the FSP registry API with a service account and identify the participant by
 * the `fsp_id` claim of an FSP ID (Keycloak) token obtained through OIDC account linking / identity
 * brokering — nothing else in the application has to change.
 */

data class FspParticipant(val fspId: String, val email: String, val fullName: String, val region: String)

data class FspResult(
    val externalId: String,
    val eventName: String,
    val discipline: String,
    val eventDate: LocalDate,
    val level: String,
    val place: Int?,
    val result: String,
    val teamName: String?,
)

data class FspRecord(val participant: FspParticipant, val results: List<FspResult>)

interface FspRegistry {
    /** Stored in achievements.source; lets several registries / environments coexist. */
    val source: String
    /** Shown to employers as the issuer of imported achievements. */
    val issuerLabel: String
    /** Human-readable name of the registry for the UI ("демо-реестр"). */
    val displayName: String
    suspend fun lookup(fspId: String): FspRecord?
}

object FspIds {
    private val pattern = Regex("^FSP-\\d{6}$")

    /** Accepts `FSP-104217`, `fsp 104217`, `104217`; returns the canonical form or null. */
    fun normalize(raw: String): String? {
        val compact = raw.trim().uppercase().replace(Regex("[\\s_]+"), "-")
        val candidate = when {
            compact.matches(Regex("^\\d{6}$")) -> "FSP-$compact"
            compact.matches(Regex("^FSP-?\\d{6}$")) -> "FSP-" + compact.takeLast(6)
            else -> compact
        }
        return candidate.takeIf { pattern.matches(it) }
    }
}

/**
 * Ownership proof for linking.
 *
 * A typed FSP ID alone proves nothing: IDs appear on diplomas, public result tables and rating pages,
 * so anyone could claim a champion's record. Linking therefore requires evidence that the *same person*
 * controls both identities. In production that evidence is an FSP ID (Keycloak) login: the platform
 * receives a token signed by FSP whose subject/`fsp_id` claim is the participant. The mock emulates the
 * same guarantee with what we already trust: the candidate's e-mail verified by our Keycloak must equal
 * the e-mail the registry holds for that participant (both sides verified the mailbox).
 */
object FspOwnership {
    fun verify(record: FspRecord, verifiedEmail: String?): Boolean =
        !verifiedEmail.isNullOrBlank() && record.participant.email.trim().equals(verifiedEmail.trim(), ignoreCase = true)
}

/**
 * Explicit points policy for imported results (stored in achievements.points, 1..20):
 *   base by level:      international 10, federal 7, regional 4
 *   × outcome:          winner (1st) 1.6, prize (2–3) 1.3, finalist 1.0, participant 0.5
 *   e.g. federal prize 9, federal winner 11, regional participant 2, international winner 16.
 * Recency is applied at ranking time (MatchingPolicy.recency), not stored, so points stay comparable.
 */
object FspPointsPolicy {
    private val base = mapOf("international" to 10.0, "federal" to 7.0, "regional" to 4.0)
    private val multiplier = mapOf("winner" to 1.6, "prize" to 1.3, "finalist" to 1.0, "participant" to 0.5)

    fun points(level: String, result: String): Int =
        ((base[level] ?: 4.0) * (multiplier[result] ?: 0.5)).roundToInt().coerceIn(1, 20)

    fun title(result: FspResult): String {
        val outcome = when (result.result) {
            "winner" -> "Победитель"
            "prize" -> "Призёр"
            "finalist" -> "Финалист"
            else -> "Участник"
        }
        val place = result.place?.let { " ($it место)" }.orEmpty()
        return "$outcome$place: ${result.eventName} — ${result.discipline}"
    }
}
