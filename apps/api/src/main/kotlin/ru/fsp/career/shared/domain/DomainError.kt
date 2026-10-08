package ru.fsp.career.shared.domain

enum class FailureKind { INVALID_INPUT, FORBIDDEN, CONFLICT, NOT_FOUND }

class DomainError(val kind: FailureKind, override val message: String) : RuntimeException(message)

fun bad(message: String): Nothing = throw DomainError(FailureKind.INVALID_INPUT, message)

fun forbidden(message: String): Nothing = throw DomainError(FailureKind.FORBIDDEN, message)

fun conflict(message: String): Nothing = throw DomainError(FailureKind.CONFLICT, message)

fun missing(message: String): Nothing = throw DomainError(FailureKind.NOT_FOUND, message)

fun requireText(value: String, label: String, max: Int = 500): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > max) bad("$label must contain 1..$max characters")
    return trimmed
}

fun uuid(value: String): java.util.UUID = try {
    java.util.UUID.fromString(value)
} catch (_: IllegalArgumentException) {
    bad("Invalid UUID")
}
