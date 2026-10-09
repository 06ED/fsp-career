package ru.fsp.career.profiles.http

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import ru.fsp.career.identity.http.role
import ru.fsp.career.profiles.application.ResumeService
import ru.fsp.career.profiles.domain.ConfirmResume
import ru.fsp.career.profiles.infrastructure.MAX_PDF_BYTES
import ru.fsp.career.shared.domain.bad
import io.ktor.server.plugins.di.*
import java.io.ByteArrayOutputStream

internal fun Route.resumeRoutes(audience: String) {
    val service: ResumeService by application.dependencies

    post("/candidates/me/resume-upload") {
        val sub = call.role(audience, "candidate")
        service.requireUploadConsent(sub)
        var pdf: ByteArray? = null

        call.receiveMultipart().forEachPart { part ->
            try {
                if (part is PartData.FileItem && part.name == "file") {
                    if (pdf != null) bad("Upload exactly one PDF")
                    if (part.contentType?.match(ContentType.Application.Pdf) != true && part.contentType != ContentType.Application.OctetStream) bad(
                        "Expected application/pdf"
                    )
                    val output = ByteArrayOutputStream()
                    val channel = part.provider()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (output.size() + count > MAX_PDF_BYTES) bad("PDF exceeds 5 MB")
                        output.write(buffer, 0, count)
                    }
                    pdf = output.toByteArray()
                }
            } finally {
                part.dispose()
            }
        }

        call.respond(
            HttpStatusCode.Created,
            service.upload(sub, pdf ?: bad("Missing file field"))
        )
    }

    post("/candidates/me/resume-confirm") {
        call.respond(
            service.confirm(
                call.role(audience, "candidate"),
                call.receive<ConfirmResume>()
            )
        )
    }
}
