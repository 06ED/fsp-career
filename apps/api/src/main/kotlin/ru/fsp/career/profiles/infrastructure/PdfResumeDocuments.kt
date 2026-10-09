package ru.fsp.career.profiles.infrastructure

import ru.fsp.career.profiles.application.ResumeDocuments

internal class PdfResumeDocuments : ResumeDocuments {
    override fun extract(pdf: ByteArray) = extractResume(pdf)
}
