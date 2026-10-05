package ru.fsp.career

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val MAX_PDF_BYTES = 5 * 1024 * 1024

internal fun extractResume(pdf: ByteArray): ResumeFields {
    if (pdf.size > MAX_PDF_BYTES || pdf.size < 5 || !pdf.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) bad("Upload a PDF up to 5 MB")
    val text = try {
        Loader.loadPDF(pdf).use { document ->
            if (document.numberOfPages !in 1..30 || document.isEncrypted) bad("PDF must contain 1..30 readable pages")
            PDFTextStripper().getText(document).take(50_000)
        }
    } catch (error: ApiError) { throw error }
      catch (_: Exception) { bad("PDF could not be read; upload a text-based PDF") }
    if (text.isBlank()) bad("PDF has no extractable text; upload a text-based PDF")
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(text)?.value.orEmpty()
    val phone = Regex("(?:\\+?7|8)[\\s()\\-]*\\d[\\d\\s()\\-]{8,16}").find(text)?.value.orEmpty()
    val knownSkills = listOf("Kotlin","Java","Python","JavaScript","TypeScript","React","SQL","PostgreSQL","Docker","Kubernetes","Figma","Swift","Go","AWS")
    val stack = knownSkills.filter { Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])").containsMatchIn(text) }
    return ResumeFields(fullName = lines.firstOrNull()?.take(200).orEmpty(),email=email,phone=phone.take(50),experience=text.take(2000),techStack=stack)
}

fun Route.resumeRoutes(db: Database, audience: String) {
    get("/candidates/me/resume.pdf") {
        val sub = call.role(audience,"candidate")
        val profile = db.tx { c -> c.profile(c.account(sub,"candidate")) }
        val bytes = resumePdf(profile)
        call.response.header(HttpHeaders.ContentDisposition,"attachment; filename=\"fsp-resume.pdf\"")
        call.respondBytes(bytes,ContentType.Application.Pdf)
    }
    post("/candidates/me/resume-upload") {
        val sub = call.role(audience,"candidate")
        db.tx { c -> if (!c.profile(c.account(sub,"candidate")).processingConsent) forbidden("Grant processing consent before uploading a resume") }
        val multipart = call.receiveMultipart()
        var pdf: ByteArray? = null
        multipart.forEachPart { part ->
            try {
                if (part is PartData.FileItem && part.name == "file") {
                    if (pdf != null) bad("Upload exactly one PDF")
                    if (part.contentType?.match(ContentType.Application.Pdf) != true && part.contentType != ContentType.Application.OctetStream) bad("Expected application/pdf")
                    val output = ByteArrayOutputStream()
                    val channel = part.provider()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = channel.readAvailable(buffer,0,buffer.size)
                        if (count < 0) break
                        if (output.size() + count > MAX_PDF_BYTES) bad("PDF exceeds 5 MB")
                        output.write(buffer,0,count)
                    }
                    pdf = output.toByteArray()
                }
            } finally { part.dispose() }
        }
        val parsed = extractResume(pdf ?: bad("Missing file field"))
        call.respond(HttpStatusCode.Created,db.tx { c ->
            val id = c.account(sub,"candidate")
            if (!c.profile(id).processingConsent) forbidden("Processing consent was revoked during upload")
            val draft = c.rows("INSERT INTO resume_drafts(candidate_id,parsed_fields) VALUES (?,?::jsonb) RETURNING id",id,Json.encodeToString(parsed)) { it.getObject(1,UUID::class.java) }.single()
            ResumeDraft(draft.toString(),parsed)
        })
    }
    post("/candidates/me/resume-confirm") {
        val sub = call.role(audience,"candidate")
        val reviewed = call.receive<ConfirmResume>()
        validateCareerFields(reviewed.experience,reviewed.techStack,reviewed.softSkills,reviewed.targetRoles,reviewed.cvHistory)
        if (reviewed.fullName.length > 200 || reviewed.phone.length > 50) bad("Reviewed contact fields too long")
        call.respond(db.tx { c ->
            val id = c.account(sub,"candidate")
            if (!c.profile(id).processingConsent) forbidden("Grant processing consent before confirming a resume")
            val changed = c.exec("UPDATE resume_drafts SET confirmed_at=now() WHERE id=? AND candidate_id=? AND confirmed_at IS NULL AND uploaded_at>now()-interval '1 day'",uuid(reviewed.draftId),id)
            if (changed == 0) missing("Unconfirmed resume draft not found or expired")
            c.exec("UPDATE candidate_profiles SET full_name=?,phone=?,experience=?,tech_stack=?::jsonb,soft_skills=?::jsonb,target_roles=?::jsonb,cv_history=?::jsonb,updated_at=now() WHERE account_id=?",reviewed.fullName.trim(),reviewed.phone.trim(),reviewed.experience.trim(),Json.encodeToString(reviewed.techStack),Json.encodeToString(reviewed.softSkills),Json.encodeToString(reviewed.targetRoles),Json.encodeToString(reviewed.cvHistory),id)
            c.profile(id)
        })
    }
}
