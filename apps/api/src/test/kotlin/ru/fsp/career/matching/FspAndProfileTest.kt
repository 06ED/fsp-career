package ru.fsp.career.matching

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import ru.fsp.career.ApiError
import ru.fsp.career.CandidatePrivacy
import ru.fsp.career.Company
import ru.fsp.career.InviteRequest
import ru.fsp.career.Need
import ru.fsp.career.companyContactLine
import ru.fsp.career.fsp.FspIds
import ru.fsp.career.fsp.FspOwnership
import ru.fsp.career.fsp.FspParticipant
import ru.fsp.career.fsp.FspPointsPolicy
import ru.fsp.career.fsp.FspRecord
import ru.fsp.career.fsp.FspResult
import ru.fsp.career.validateCompany
import ru.fsp.career.validateNeedStack
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FspAndProfileTest {
    @Test fun fspPointsPolicyIsExplicitAndBounded() {
        assertEquals(9, FspPointsPolicy.points("federal", "prize"))
        assertEquals(11, FspPointsPolicy.points("federal", "winner"))
        assertEquals(2, FspPointsPolicy.points("regional", "participant"))
        assertEquals(16, FspPointsPolicy.points("international", "winner"))
        for (level in listOf("regional", "federal", "international", "unknown"))
            for (result in listOf("winner", "prize", "finalist", "participant", "unknown"))
                assertTrue(FspPointsPolicy.points(level, result) in 1..20)
    }

    @Test fun fspIdsAreNormalised() {
        assertEquals("FSP-104217", FspIds.normalize(" fsp-104217 "))
        assertEquals("FSP-104217", FspIds.normalize("104217"))
        assertEquals("FSP-104217", FspIds.normalize("FSP 104217"))
        assertEquals("FSP-104217", FspIds.normalize("FSP104217"))
        assertNull(FspIds.normalize("FSP-12"))
        assertNull(FspIds.normalize("'; drop table accounts; --"))
    }

    @Test fun typedIdAloneIsNotProofOfOwnership() {
        val record = FspRecord(FspParticipant("FSP-100001", "ivan.petrov@demo.fsp.local", "Иван Петров", "Москва"), emptyList())
        assertTrue(FspOwnership.verify(record, "Ivan.Petrov@demo.fsp.local"))
        assertFalse(FspOwnership.verify(record, "someone@example.com"))
        assertFalse(FspOwnership.verify(record, null))
        assertFalse(FspOwnership.verify(record, " "))
    }

    @Test fun importedTitleNamesOutcomePlaceEventAndDiscipline() {
        val result = FspResult("R-1", "Кубок России (демо)", "продуктовое программирование", LocalDate.parse("2025-05-01"), "federal", 2, "prize", null)
        assertEquals("Призёр (2 место): Кубок России (демо) — продуктовое программирование", FspPointsPolicy.title(result))
    }

    @Test fun companyContactsAreValidatedAndNormalised() {
        val company = validateCompany(Company(name = " Acme ", website = "https://acme.example", contactEmail = "hr@acme.example",
            contactPhone = "+7 (900) 000-00-00", contactTelegram = "https://t.me/acme_hr", industry = " финтех "))
        assertEquals("Acme", company.name)
        assertEquals("@acme_hr", company.contactTelegram)
        assertEquals("финтех", company.industry)
        assertEquals("Email: hr@acme.example · Телефон: +7 (900) 000-00-00 · Telegram: @acme_hr", companyContactLine(company))
        listOf(
            Company(name = "A", contactEmail = "not-an-email"),
            Company(name = "A", contactPhone = "call me"),
            Company(name = "A", contactTelegram = "@ab"),
            Company(name = "A", website = "ftp://acme"),
            Company(name = " "),
        ).forEach { assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { validateCompany(it) }.status) }
        assertEquals("", companyContactLine(Company(name = "A")))
    }

    @Test fun needStackIsDedupedAndLimited() {
        assertEquals(listOf("Kotlin", "Kafka"), validateNeedStack(listOf(" Kotlin ", "kotlin", "", "Kafka")))
        assertFailsWith<ApiError> { validateNeedStack((1..16).map { "skill$it" }) }
        assertFailsWith<ApiError> { validateNeedStack(listOf("x".repeat(51))) }
    }

    @Test fun olderPayloadsStillDecode() {
        val json = Json { ignoreUnknownKeys = false }
        assertEquals("", json.decodeFromString<Company>("""{"name":"A","description":"","website":""}""").contactEmail)
        assertEquals(emptyList(), json.decodeFromString<Need>("""{"specialization":"backend"}""").techStack)
        assertEquals("", json.decodeFromString<InviteRequest>("""{"candidateId":"x","description":"d","salaryMinRub":1,"salaryMaxRub":2}""").contactMethod)
        assertEquals(CandidatePrivacy(), json.decodeFromString<CandidatePrivacy>("{}"))
    }
}
