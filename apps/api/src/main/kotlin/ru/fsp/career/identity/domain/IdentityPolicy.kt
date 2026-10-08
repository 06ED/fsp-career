package ru.fsp.career.identity.domain

import ru.fsp.career.shared.domain.bad

internal fun validateRole(role: String): String = role.takeIf { it == "candidate" || it == "employer" }
    ?: bad("role must be candidate or employer")
internal fun registrationEmail(input: Credentials): String {
    val email = input.email.trim().lowercase()
    if (email.length !in 3..320 || !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email) || input.password.length !in 12..128) bad("Invalid email or password")
    return email
}
internal fun loginEmail(input: Credentials): String {
    if (input.email.length > 320 || input.password.length > 128) bad("Invalid email or password")
    return input.email.trim().lowercase()
}
