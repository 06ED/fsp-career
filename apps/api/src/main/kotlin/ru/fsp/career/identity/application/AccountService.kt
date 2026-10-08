package ru.fsp.career.identity.application

import ru.fsp.career.identity.domain.*

internal class AccountService(private val accounts: AccountRepository) {
    suspend fun me(subject: String, email: String, verified: Boolean, adminEmployer: Boolean) =
        Me(accounts.role(subject, adminEmployer), email, verified)
    suspend fun choose(subject: String, input: RoleChoice, identity: BrowserIdentity?): Me {
        val role = validateRole(input.role)
        return Me(accounts.choose(subject, role, identity))
    }
}
