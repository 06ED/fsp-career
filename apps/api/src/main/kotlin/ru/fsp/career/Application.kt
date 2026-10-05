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
            validate { credential ->
                val token = credential.payload
                val realmRoles = token.getClaim("realm_access").asMap()?.get("roles") as? List<*>
                val clientRoles =
                    (token.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
                if (token.subject.isNullOrBlank() || (realmRoles.orEmpty() + clientRoles.orEmpty()).none { it == "candidate" || it == "employer" }) null
                else JWTPrincipal(token)
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

fun ApplicationCall.role(audience: String, requiredRole: String): String {
    val principal = principal<JWTPrincipal>()
        ?: throw ApiError(HttpStatusCode.Unauthorized, "Authentication required")
    val realm = principal.payload.getClaim("realm_access")
        .asMap()
        ?.get("roles") as? List<*>
    val client = (principal.payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)
        ?.get("roles") as? List<*>
    val roles = realm.orEmpty() + client.orEmpty()

    if (roles.none { it == requiredRole } || (requiredRole == "candidate" && roles.contains("employer")))
        forbidden("$requiredRole role required")

    return principal.payload.subject
}
