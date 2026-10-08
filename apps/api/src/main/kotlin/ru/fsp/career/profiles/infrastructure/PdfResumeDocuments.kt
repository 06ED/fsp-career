package ru.fsp.career.profiles.infrastructure

import ru.fsp.career.profiles.application.ResumeDocuments
import ru.fsp.career.profiles.domain.CandidateProfile

internal class PdfResumeDocuments : ResumeDocuments {
    override fun export(profile: CandidateProfile) = resumePdf(profile)
    override fun extract(pdf: ByteArray) = extractResume(pdf)
}
