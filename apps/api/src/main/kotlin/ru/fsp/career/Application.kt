package ru.fsp.career

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

internal fun Application.careerModule(
    db: Database,
    issuer: String,
    audience: String,
    jwksUrl: String = "$issuer/protocol/openid-connect/certs",
    authConfig: AuthConfig = AuthConfig.fromEnv(issuer),
    authHttp: HttpClient = authHttpClient(),
    fspKeys: com.auth0.jwk.JwkProvider? = null
) {
    val logger = environment.log
    monitor.subscribe(ApplicationStopped) { authHttp.close() }
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
                    "/api/auth/login", "/api/auth/register", "/api/auth/logout") &&
                call.request.httpMethod !in listOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)) {
                val cookie = call.sessions.get<BrowserCookie>()?.value
                if (cookie != null) call.requireCsrf(authConfig.frontendOrigin, db.session(cookie))
            }
        }
    })

    install(StatusPages) {
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
            validate { cookie -> db.session(cookie.value)?.identity?.let { BrowserPrincipal(it) } }
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
        browserAuthRoutes(db, authConfig, authHttp, fspKeys)
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/openapi.yaml") {
            call.respondText(
                javaClass.classLoader.getResource("openapi.yaml")!!.readText(),
                ContentType.parse("application/yaml")
            )
        }
        authenticate("keycloak", "browser", strategy = AuthenticationStrategy.FirstSuccessful) { careerRoutes(db, audience) }
    }
}

/**
 * Returns the authenticated subject. The [requiredRole] is enforced by `Transaction.account(subject, role)`,
 * which compares it with the role bound to the account in the database.
 */
@Suppress("UNUSED_PARAMETER")
fun ApplicationCall.role(audience: String, requiredRole: String): String {
    return principal<BrowserPrincipal>()?.identity?.subject ?: principal<JWTPrincipal>()?.payload?.subject
        ?: throw ApiError(HttpStatusCode.Unauthorized, "Authentication required")
}

internal data class BrowserPrincipal(val identity: BrowserIdentity) : Principal

fun ApplicationCall.authEmail(): String = principal<BrowserPrincipal>()?.identity?.email
    ?: principal<JWTPrincipal>()?.payload?.getClaim("email")?.asString().orEmpty()

fun ApplicationCall.verifiedEmail(): String = principal<BrowserPrincipal>()?.identity?.let { if (it.verified) it.email else "" }
    ?: principal<JWTPrincipal>()?.payload?.let { if (it.getClaim("email_verified").asBoolean() == true) it.getClaim("email").asString().orEmpty() else "" }.orEmpty()

/** Realm/client roles still matter for accounts created before in-app role choice (admin-assigned employers). */
fun ApplicationCall.tokenRoles(audience: String): Set<String> {
    val payload = principal<JWTPrincipal>()?.payload ?: return emptySet()
    val realm = payload.getClaim("realm_access").asMap()?.get("roles") as? List<*>
    val client = (payload.getClaim("resource_access").asMap()?.get(audience) as? Map<*, *>)?.get("roles") as? List<*>
    return (realm.orEmpty() + client.orEmpty()).filterIsInstance<String>().toSet()
}
