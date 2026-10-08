package ru.fsp.career.assessment

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.assessment.application.AssessmentService
import ru.fsp.career.assessment.infrastructure.ExposedAssessmentRepository

internal fun Application.assessmentDependencies() {
    dependencies {
        provide(::ExposedAssessmentRepository)
        provide(::AssessmentService)
    }
}
