package ru.fsp.career.profiles.application

import ru.fsp.career.profiles.domain.CandidateProfile
import ru.fsp.career.profiles.domain.ResumeFields

internal interface ResumeDocuments {
    fun export(profile: CandidateProfile): ByteArray
    fun extract(pdf: ByteArray): ResumeFields
}
