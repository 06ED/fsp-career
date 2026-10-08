package ru.fsp.career

import io.ktor.client.HttpClient
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.identity.http.authHttpClient
import ru.fsp.career.identity.infrastructure.AuthConfig
import ru.fsp.career.shared.infrastructure.Database

internal fun Application.platformDependencies(issuer: String) {
    dependencies {
        if (!contains(DependencyKey<Database>())) provide<Database> {
            Database(requiredEnv("DATABASE_URL"), requiredEnv("DATABASE_USER"), requiredEnv("DATABASE_PASSWORD"))
        }
        if (!contains(DependencyKey<AuthConfig>())) provide<AuthConfig> { AuthConfig.fromEnv(issuer) }
        if (!contains(DependencyKey<HttpClient>())) provide<HttpClient> { authHttpClient() }
    }
}

internal fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is required")
