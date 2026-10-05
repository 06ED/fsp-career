package ru.fsp.career

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationTest {
    @Test fun gradesAreRestricted() {
        assertEquals("senior", grade("senior"))
        assertEquals(HttpStatusCode.BadRequest, assertFailsWith<ApiError> { grade("principal") }.status)
    }
    @Test fun requiredTextRejectsBlankAndExcessiveInput() {
        assertEquals("offer", requireText(" offer ", "Description"))
        assertFailsWith<ApiError> { requireText("   ", "Description") }
        assertFailsWith<ApiError> { requireText("a".repeat(401), "Description", 400) }
    }
    @Test fun invitationRequiresSalaryFields() {
        assertFailsWith<kotlinx.serialization.MissingFieldException> {
            Json.decodeFromString<InviteRequest>("""{"candidateId":"id","description":"offer"}""")
        }
    }
    @Test fun candidateContactDefaultsToPrivate() {
        val invite = Invitation("1","2","3","offer",100,200,"sent","today")
        assertEquals(null, invite.contactEmail)
        assertEquals(null, invite.contactPhone)
    }
}
