package ru.fsp.career.identity

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.*
import ru.fsp.career.identity.application.AccountService
import ru.fsp.career.identity.application.AuthService
import ru.fsp.career.identity.domain.PasswordHasher
import ru.fsp.career.identity.infrastructure.ExposedAccountRepository
import ru.fsp.career.identity.infrastructure.ExposedIdentityRepository
import ru.fsp.career.identity.infrastructure.Passwords

internal fun Application.identityDependencies() {
    dependencies {
        provide<PasswordHasher> { Passwords }
        provide(::ExposedAccountRepository)
        provide(::ExposedIdentityRepository)
        provide(::AuthService)
        provide(::AccountService)
    }
}
