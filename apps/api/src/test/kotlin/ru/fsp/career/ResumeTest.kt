package ru.fsp.career

import ru.fsp.career.profiles.domain.*
import ru.fsp.career.profiles.infrastructure.*
import ru.fsp.career.shared.domain.DomainError

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResumeTest {
    @Test fun rejectsNonPdfAndOversizedFiles() {
        assertFailsWith<DomainError> { extractResume("not a PDF".toByteArray()) }
        assertFailsWith<DomainError> { extractResume(ByteArray(5*1024*1024+1)) }
    }
    @Test fun extractsBoundedSuggestionsWithoutApplyingThem() {
        val pdf = requireNotNull(javaClass.getResourceAsStream("/resume.pdf")).use { it.readBytes() }
        val parsed = extractResume(pdf)
        assertEquals("ada@example.org",parsed.email)
        assertEquals(listOf("Kotlin","SQL"),parsed.techStack)
        assertTrue(!parsed.experience.contains("Ada Example"))
        assertTrue(!parsed.experience.contains(parsed.email))
        assertEquals(false, CandidateProfile().processingConsent)
    }
    @Test fun validatesCvHistoryAndSkillLimits() {
        assertFailsWith<DomainError> { validateCareerFields("",listOf(""), emptyList(),emptyList(), emptyList()) }
        assertFailsWith<DomainError> { validateCareerFields("",emptyList(), emptyList(),emptyList(), listOf(CvEntry(company="",role="dev"))) }
    }
    @Test fun hidesContactsAndPrivateNamesInExistingFreeText() {
        val text = "Ada Example: ada@example.org, +7 (999) 123-45-67, https://t.me/ada, @ada_dev. Kotlin SQL 2020-2024"
        val visible = employerText(text, hiddenName = "Ada Example")
        assertTrue(listOf("Ada", "Example", "ada@example.org", "999", "t.me", "@ada_dev").none { it in visible })
        assertTrue("Kotlin SQL 2020-2024" in visible)
    }
}
