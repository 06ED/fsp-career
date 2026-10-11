package ru.fsp.career

import ru.fsp.career.profiles.domain.CandidateProfile
import ru.fsp.career.profiles.domain.validateProfile
import ru.fsp.career.shared.domain.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfilePolicyTest {
    @Test fun contactEmailCanBeSavedWithoutVerifiedIdentityEmail() {
        val profile = CandidateProfile(email = " contact@example.org ", processingConsent = true)
        assertEquals("contact@example.org", validateProfile(profile, "").email)
        assertEquals("contact@example.org", validateProfile(profile, "login@example.org").email)
    }

    @Test fun blankContactEmailKeepsTheVerifiedIdentityDefault() {
        val profile = CandidateProfile(processingConsent = true)
        assertEquals("login@example.org", validateProfile(profile, "login@example.org").email)
        assertEquals("", validateProfile(profile, "").email)
    }

    @Test fun invalidContactEmailIsRejectedEvenWithVerifiedIdentity() {
        for (email in listOf("not-an-email", "contact@example", "contact @example.org", "a@b@example.org", "a".repeat(309) + "@example.org")) {
            assertFailsWith<DomainError> {
                validateProfile(CandidateProfile(email = email, processingConsent = true), "login@example.org")
            }
        }
    }
}
