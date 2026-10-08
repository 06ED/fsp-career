package ru.fsp.career.achievements

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.achievements.application.AchievementService
import ru.fsp.career.achievements.infrastructure.ExposedAchievementRepository
import ru.fsp.career.achievements.infrastructure.MockFspRegistry

internal fun Application.achievementDependencies() {
    dependencies {
        provide(::MockFspRegistry)
        provide(::ExposedAchievementRepository)
        provide(::AchievementService)
    }
}
