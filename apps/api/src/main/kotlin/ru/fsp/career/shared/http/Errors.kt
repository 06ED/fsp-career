package ru.fsp.career.shared.http

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import ru.fsp.career.shared.domain.DomainError
import ru.fsp.career.shared.domain.FailureKind

@Serializable
data class ErrorResponse(val error: String)

class ApiError(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

val DomainError.status: HttpStatusCode
    get() = when (kind) {
        FailureKind.INVALID_INPUT -> HttpStatusCode.BadRequest
        FailureKind.FORBIDDEN -> HttpStatusCode.Forbidden
        FailureKind.CONFLICT -> HttpStatusCode.Conflict
        FailureKind.NOT_FOUND -> HttpStatusCode.NotFound
    }
