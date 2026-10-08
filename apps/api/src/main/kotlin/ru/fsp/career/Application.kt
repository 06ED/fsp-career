package ru.fsp.career

import ru.fsp.career.identity.http.*
import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.identity.domain.IdentityRepository
import ru.fsp.career.shared.domain.*
import ru.fsp.career.shared.http.*

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.client.HttpClient
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.sessions.*
import io.ktor.server.request.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.concurrent.TimeUnit

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0"
    ) {
        careerModule()
    }.start(wait = true)
}

internal fun Application.careerModule(
    issuer: String = requiredEnv("KEYCLOAK_ISSUER").trimEnd('/'),
    audience: String = requiredEnv("KEYCLOAK_AUDIENCE"),
    jwksUrl: String = System.getenv("KEYCLOAK_JWKS_URL")?.takeIf { it.isNotBlank() }
        ?: "$issuer/protocol/openid-connect/certs",
    fspKeys: com.auth0.jwk.JwkProvider? = null
) {
    val logger = environment.log
    careerDependencies(issuer)
    val authConfig: AuthConfig by dependencies
    val authHttp: HttpClient by dependencies
    val identities: IdentityRepository by dependencies
    // The production API is private behind one Traefik hop, which sets X-Forwarded-For.
    if (authConfig.secure) install(XForwardedHeaders) { useLastProxy() }

    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = false; encodeDefaults = true }) }
    install(CORS) {
        val frontend = URI(authConfig.frontendOrigin)
        allowHost(frontend.authority, schemes = listOf(frontend.scheme))
        allowCredentials = true
        allowHeader("X-CSRF-Token")
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Delete)
    }

    browserAuth(authConfig)

    install(createApplicationPlugin("BrowserCsrf") {
        onCall { call ->
            if (call.request.path().startsWith("/api/") && call.request.path() !in listOf(
                    "/api/auth/login", "/api/auth/register", "/api/auth/logout"
                ) &&
                call.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)
            ) {
                val cookie = call.sessions.get<BrowserCookie>()?.value
                if (cookie != null) call.requireCsrf(authConfig.frontendOrigin, identities.session(cookie))
            }
        }
    })

    install(StatusPages) {
        exception<DomainError> { call, cause -> call.respond(cause.status, ErrorResponse(cause.message)) }
        exception<OAuthLoginFailed> { call, _ -> call.respondRedirect("${authConfig.frontendOrigin}/app?authError=failed") }
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
        externalOAuth(authConfig, authHttp)
        session<BrowserCookie>("browser") {
            validate { cookie -> identities.session(cookie.value)?.identity?.let { BrowserPrincipal(it) } }
            challenge { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required")) }
        }
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
        browserAuthRoutes(fspKeys)
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/openapi.yaml") {
            call.respondText(
                javaClass.classLoader.getResource("openapi.yaml")!!.readText(),
                ContentType.parse("application/yaml")
            )
        }
        authenticate("keycloak", "browser", strategy = AuthenticationStrategy.FirstSuccessful) {
            careerRoutes(audience)
        }
    }
}
