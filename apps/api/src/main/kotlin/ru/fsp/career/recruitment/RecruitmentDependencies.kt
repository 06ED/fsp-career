package ru.fsp.career.recruitment

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.recruitment.application.InvitationService
import ru.fsp.career.recruitment.application.TaskService
import ru.fsp.career.recruitment.application.VacancyService
import ru.fsp.career.recruitment.infrastructure.ExposedInvitationRepository
import ru.fsp.career.recruitment.infrastructure.ExposedTaskRepository
import ru.fsp.career.recruitment.infrastructure.ExposedVacancyRepository

internal fun Application.recruitmentDependencies() {
    dependencies {
        provide(::ExposedVacancyRepository)
        provide(::ExposedTaskRepository)
        provide(::ExposedInvitationRepository)
        provide(::VacancyService)
        provide(::TaskService)
        provide(::InvitationService)
    }
}
