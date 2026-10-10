package ru.fsp.career.profiles.infrastructure

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import ru.fsp.career.profiles.domain.ResumeFields
import ru.fsp.career.profiles.domain.employerText
import ru.fsp.career.shared.domain.*

internal const val MAX_PDF_BYTES = 5 * 1024 * 1024
internal fun extractResume(pdf: ByteArray): ResumeFields {
    if (pdf.size > MAX_PDF_BYTES || pdf.size < 5 || !pdf.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) bad("Upload a PDF up to 5 MB")
    val text = try {
        Loader.loadPDF(pdf).use { document ->
            if (document.numberOfPages !in 1..30 || document.isEncrypted) bad("PDF must contain 1..30 readable pages")
            PDFTextStripper().getText(document).take(50_000)
        }
    } catch (error: DomainError) { throw error }
      catch (_: Exception) { bad("PDF could not be read; upload a text-based PDF") }
    if (text.isBlank()) bad("PDF has no extractable text; upload a text-based PDF")
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(text)?.value.orEmpty()
    val phone = Regex("(?:\\+?7|8)[\\s()\\-]*\\d[\\d\\s()\\-]{8,16}").find(text)?.value.orEmpty()
    val knownSkills = listOf("Kotlin","Java","Python","JavaScript","TypeScript","React","SQL","PostgreSQL","Docker","Kubernetes","Figma","Swift","Go","AWS")
    val stack = knownSkills.filter { Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])").containsMatchIn(text) }
    val name = lines.firstOrNull()?.take(200).orEmpty()
    val experience = employerText(lines.drop(1).joinToString("\n"), email, phone, name).take(2000)
    return ResumeFields(fullName = name, email = email, phone = phone.take(50), experience = experience, techStack = stack)
}
