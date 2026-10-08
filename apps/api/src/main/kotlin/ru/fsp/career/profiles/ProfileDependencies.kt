package ru.fsp.career.profiles

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.profiles.application.ProfileService
import ru.fsp.career.profiles.application.ResumeDocuments
import ru.fsp.career.profiles.application.ResumeService
import ru.fsp.career.profiles.infrastructure.ExposedProfileRepository
import ru.fsp.career.profiles.infrastructure.PdfResumeDocuments

internal fun Application.profileDependencies() {
    dependencies {
        provide(::ExposedProfileRepository)
        provide<ResumeDocuments> { PdfResumeDocuments() }
        provide(::ProfileService)
        provide(::ResumeService)
    }
}
