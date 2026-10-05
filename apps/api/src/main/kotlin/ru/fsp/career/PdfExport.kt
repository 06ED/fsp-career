package ru.fsp.career

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
import java.awt.Color

/** Generates a disposable, paginated candidate-owned resume; no document or contact data is persisted here. */
internal fun resumePdf(profile: CandidateProfile): ByteArray {
    if (!profile.processingConsent) forbidden("Processing consent required for CV export")
    if (profile.fullName.isBlank()) bad("Complete fullName before exporting a CV")
    return PDDocument().use { document ->
        val font = requireNotNull(PdfExport::class.java.getResourceAsStream("/fonts/JetBrainsMono-Regular.ttf")) { "Bundled JetBrains Mono font is missing" }.use { PDType0Font.load(document,it) }
        val writer = ResumeWriter(document,font)
        try {
            writer.heading("ФСП КАРЬЕРА  /  РЕЗЮМЕ")
            writer.title(profile.fullName)
            writer.line(profile.email.takeIf { it.isNotBlank() } ?: "Эл. почта не указана")
            if (profile.phone.isNotBlank()) writer.line(profile.phone)
            writer.section("КАРЬЕРНЫЙ ПРОФИЛЬ")
            writer.line("Специализация: ${profile.specialization.ifBlank { "Не указана" }}")
            writer.line("Отрасль: ${profile.industry.ifBlank { "Не указана" }}")
            writer.line("Желаемые роли: ${profile.targetRoles.joinToString(", ").ifBlank { "Не указаны" }}")
            writer.line("Предполагаемый грейд: ${profile.desiredGrade}")
            profile.grade?.let { writer.line("Подтверждённый грейд: $it${profile.score?.let { s -> " ($s/100)" }.orEmpty()}") }
            writer.section("ТЕХНОЛОГИИ")
            writer.paragraph(profile.techStack.joinToString("  •  ").ifBlank { "Не указано" })
            writer.section("ОПЫТ")
            writer.paragraph(profile.experience.ifBlank { "Не указано" })
            writer.section("ИСТОРИЯ РАБОТЫ")
            if (profile.cvHistory.isEmpty()) writer.line("Не указано")
            profile.cvHistory.forEach { entry ->
                writer.line("${entry.role}  •  ${entry.company}",bold = true)
                writer.line(listOf(entry.startDate,entry.endDate).filter { it.isNotBlank() }.joinToString(" — "))
                if (entry.description.isNotBlank()) writer.paragraph(entry.description)
            }
            writer.section("ГИБКИЕ НАВЫКИ")
            writer.paragraph(profile.softSkills.joinToString("  •  ").ifBlank { "Не указано" })
        } finally { writer.close() }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }
}

private object PdfExport
private class ResumeWriter(private val document: PDDocument, private val font: PDType0Font) : AutoCloseable {
    private var stream: PDPageContentStream? = null
    private var y = 0f
    private val margin = 46f
    private val width = PDRectangle.A4.width - 2 * margin

    init { nextPage() }
    private fun nextPage() {
        stream?.close()
        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        stream = PDPageContentStream(document,page)
        stream!!.setNonStrokingColor(Color(64, 47, 255))
        stream!!.addRect(0f,PDRectangle.A4.height - 74f,PDRectangle.A4.width,74f)
        stream!!.fill()
        y = PDRectangle.A4.height - 109f
    }
    fun heading(text: String) {
        val s = stream!!
        s.setNonStrokingColor(Color(237,237,237))
        s.beginText(); s.setFont(font,10f); s.newLineAtOffset(margin,PDRectangle.A4.height - 45f); s.showText(safe(text)); s.endText()
    }
    fun title(text: String) { line(text,22f,true); y -= 7f }
    fun section(text: String) { y -= 10f; line(text.uppercase(),12f,true); y -= 2f }
    fun paragraph(text: String) { text.take(4000).lines().forEach { wrapped(it,10f) }; y -= 5f }
    fun line(text: String, size: Float = 10f, bold: Boolean = false) { wrapped(text,size,bold) }
    private fun wrapped(text: String, size: Float, bold: Boolean = false) {
        val words = safe(text).split(Regex("\\s+"))
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (font.getStringWidth(candidate) * size / 1000f <= width || line.isEmpty()) line = candidate
            else { draw(line,size,bold); line = word }
        }
        draw(line,size,bold)
    }
    private fun draw(text: String, size: Float, bold: Boolean) {
        if (y < margin + 18) nextPage()
        val s = stream!!
        s.setNonStrokingColor(if (bold) Color(64,47,255) else Color(27,28,33))
        s.beginText(); s.setFont(font,size); s.newLineAtOffset(margin,y); s.showText(if (text.isBlank()) " " else text); s.endText()
        y -= size + 6
    }
    private fun safe(value: String): String = buildString {
        value.codePoints().forEach { point ->
            val symbol = String(Character.toChars(point))
            val renderable = if (Character.isISOControl(point)) " " else symbol
            append(try { font.getStringWidth(renderable); renderable } catch (_: Exception) { "?" })
        }
    }
    override fun close() { stream?.close(); stream = null }
}
