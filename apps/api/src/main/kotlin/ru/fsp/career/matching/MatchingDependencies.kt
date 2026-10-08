package ru.fsp.career.matching

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.matching.application.MatchingService
import ru.fsp.career.matching.infrastructure.ExposedMatchingQueries

internal fun Application.matchingDependencies() {
    dependencies {
        provide(::ExposedMatchingQueries)
        provide(::MatchingService)
    }
}
