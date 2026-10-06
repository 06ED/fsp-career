package ru.fsp.career

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.concurrent.TimeUnit

fun main() {
    val issuer = required("KEYCLOAK_ISSUER").trimEnd('/')
    val audience = required("KEYCLOAK_AUDIENCE")
    val jwksUrl = System.getenv("KEYCLOAK_JWKS_URL")
        ?.takeIf { it.isNotBlank() }
        ?: "$issuer/protocol/openid-connect/certs"

    val db = Database(
        required("DATABASE_URL"),
        required("DATABASE_USER"),
        required("DATABASE_PASSWORD")
    )

    Runtime.getRuntime().addShutdownHook(Thread { db.close() })

    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0"
    ) {
        careerModule(db, issuer, audience, jwksUrl)
    }.start(wait = true)
}

private fun required(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is required")

fun Application.careerModule(
    db: Database,
    issuer: String,
    audience: String,
    jwksUrl: String = "$issuer/protocol/openid-connect/certs"
) {
    val logger = environment.log

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; encodeDefaults = true }) }
    install(CORS) {
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHost("localhost:3000", schemes = listOf("http"))
        allowHost("127.0.0.1:5173", schemes = listOf("http"))
        allowHost("127.0.0.1:3000", schemes = listOf("http"))
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
    }

    install(StatusPages) {
        exception<ApiError> { call, cause -> call.respond(cause.status, ErrorResponse(cause.message)) }
        exception<BadRequestException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Malformed request body")
            )
        }
        exception<kotlinx.serialization.SerializationException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("Malformed request body")
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    val keys = JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("keycloak") {
            realm = "career"
            verifier(keys, issuer) { withAudience(audience); acceptLeeway(10) }
            // Keycloak only authenticates; the candidate/employer role is the account's one-time choice
            // stored in `accounts.role` (see AccountRoutes.kt), so self-registered employers need no admin.
            validate { credential ->
                if (credential.payload.subject.isNullOrBlank()) null else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Valid Keycloak bearer token required")
                )
            }
        }
    }
    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/openapi.yaml") {
            call.respondText(
                javaClass.classLoader.getResource("openapi.yaml")!!.readText(),
                ContentType.parse("application/yaml")
            )
        }
        authenticate("keycloak") { careerRoutes(db, audience) }
    }
}

/**
 * Returns the authenticated subject. The [requiredRole] is enforced by `Transaction.account(subject, role)`,
 * which compares it with the role bound to the account in the database.
 */
@Suppress("UNUSED_PARAMETER")
fun ApplicationCall.role(audience: String, requiredRole: String): String {
    val principal = principal<JWTPrincipal>()
        ?: throw ApiError(HttpStatusCode.Unauthorized, "Authentication required")
    return principal.payload.subject
}

/** Realm/client roles still matter for accounts created before in-app role choice (admin-assigned employers). */
fun ApplicationCall.tokenRoles(audience: String): Set<String> {
    val payload = principal<JWTPrincipal>()?.payload ?: return emptySet()
    val realm = payload.getClaim("realm_access").asMap()?.get("roles") as? List<*>
    val client = (payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
    return (realm.orEmpty() + client.orEmpty()).filterIsInstance<String>().toSet()
}
