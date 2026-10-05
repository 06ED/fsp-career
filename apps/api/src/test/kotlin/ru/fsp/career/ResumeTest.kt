package ru.fsp.career

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResumeTest {
    @Test fun rejectsNonPdfAndOversizedFiles() {
        assertFailsWith<ApiError> { extractResume("not a PDF".toByteArray()) }
        assertFailsWith<ApiError> { extractResume(ByteArray(5*1024*1024+1)) }
    }
    @Test fun extractsBoundedSuggestionsWithoutApplyingThem() {
        val pdf = PDDocument().use { document ->
            val page = PDPage(); document.addPage(page)
            PDPageContentStream(document,page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA),12f)
                stream.newLineAtOffset(40f,700f)
                stream.showText("Ada Example ada@example.org Kotlin SQL")
                stream.endText()
            }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }
        val parsed = extractResume(pdf)
        assertEquals("ada@example.org",parsed.email)
        assertEquals(listOf("Kotlin","SQL"),parsed.techStack)
        assertTrue(parsed.experience.contains("Ada Example"))
        assertEquals(false, CandidateProfile().processingConsent)
    }
    @Test fun pdfExportPreservesCyrillicAndHandlesUnsupportedCharacters() {
        val profile = CandidateProfile(fullName="Анна Иванова",email="anna@example.org",processingConsent=true,experience="Карьера Kotlin 🚀",techStack=listOf("Kotlin","PostgreSQL"),cvHistory=listOf(CvEntry(company="Компания",role="Разработчик",startDate="2020",endDate="2024",description="API")))
        val bytes = resumePdf(profile)
        assertTrue(bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray()))
        val text = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
        assertTrue(text.contains("Анна Иванова"))
        assertTrue(text.contains("Компания"))
        assertTrue(text.contains("PostgreSQL"))
        assertFailsWith<ApiError> { resumePdf(profile.copy(processingConsent=false)) }
        assertFailsWith<ApiError> { resumePdf(profile.copy(fullName="")) }
    }
    @Test fun validatesCvHistoryAndSkillLimits() {
        assertFailsWith<ApiError> { validateCareerFields("",listOf(""), emptyList(),emptyList(), emptyList()) }
        assertFailsWith<ApiError> { validateCareerFields("",emptyList(), emptyList(),emptyList(), listOf(CvEntry(company="",role="dev"))) }
    }
}
