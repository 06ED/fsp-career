package ru.fsp.career.profiles.application

import ru.fsp.career.profiles.domain.ResumeFields

internal interface ResumeDocuments {
    fun extract(pdf: ByteArray): ResumeFields
}
