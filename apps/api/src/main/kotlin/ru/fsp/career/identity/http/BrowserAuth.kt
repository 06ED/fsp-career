package ru.fsp.career.identity.http

import com.auth0.jwk.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import ru.fsp.career.identity.application.AuthService
import ru.fsp.career.identity.domain.*
import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.shared.http.ApiError
import io.ktor.server.plugins.di.*
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable data class BrowserCookie(val value: String)
private val oauthAttempt = AttributeKey<OAuthAttempt>("oauthAttempt")
private val oauthState = AttributeKey<String>("oauthState")
private val oauthIdentity = AttributeKey<UUID>("oauthIdentity")
internal class OAuthLoginFailed : RuntimeException()
internal fun ApplicationCall.requireCsrf(origin: String, session: BrowserSession?) {
    if (!csrfValid(request.headers[HttpHeaders.Origin], origin, request.headers["X-CSRF-Token"], session))
        throw ApiError(HttpStatusCode.Forbidden, "Invalid origin or CSRF token")
}
internal fun Application.browserAuth(config: AuthConfig) {
    install(Sessions) {
        cookie<BrowserCookie>("career_session") {
            cookie.path = "/api"; cookie.httpOnly = true; cookie.secure = config.secure
            cookie.extensions["SameSite"] = "Lax"; cookie.maxAgeInSeconds = 7 * 24 * 3600
        }
    }
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 5, refillPeriod = kotlin.time.Duration.parse("1m"))
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
internal fun authHttpClient() = HttpClient(CIO) {
    install(ContentNegotiation) { json() }
    install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000 }
    expectSuccess = true; followRedirects = false
}
internal fun AuthenticationConfig.externalOAuth(config: AuthConfig, http: HttpClient) {
    for ((provider, cfg) in listOf("fsp" to config.fsp, "vk" to config.vk)) {
        if (cfg == null) continue
        oauth(provider) {
            client = http
            urlProvider = { cfg.redirectUri }
            providerLookup = {
                val call = this
                val attempt = attributes[oauthAttempt]
                OAuthServerSettings.OAuth2ServerSettings(
                    name = provider, authorizeUrl = if (provider == "fsp") config.authUrl else "https://id.vk.ru/authorize",
                    accessTokenUrl = if (provider == "fsp") config.tokenUrl else "https://id.vk.ru/oauth2/auth",
                    requestMethod = HttpMethod.Post, clientId = cfg.clientId, clientSecret = cfg.secret,
                    defaultScopes = if (provider == "fsp") listOf("openid", "email") else listOf("vkid.personal_info"),
                    nonceManager = object : NonceManager {
                        override suspend fun newNonce(): String {
                            // Ktor retries invalid_grant; a failed callback must not start another challenge.
                            if (!call.request.path().endsWith("/start") && !call.request.path().endsWith("/link")) throw OAuthLoginFailed()
                            return call.attributes[oauthState]
                        }
                        override suspend fun verifyNonce(nonce: String) = equal(nonce, call.attributes[oauthState])
                    },
                    extraAuthParameters = listOf(
                        "code_challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(attempt.verifier.toByteArray())),
                        "code_challenge_method" to "S256"
                    ) + if (provider == "fsp") listOf("nonce" to attempt.nonce) else emptyList(),
                    accessTokenInterceptor = {
                        val params = ParametersBuilder().apply {
                            appendAll((body as TextContent).text.parseUrlEncodedParameters())
                            append("code_verifier", attempt.verifier)
                            if (provider == "vk") {
                                remove("client_secret"); append("service_token", cfg.secret)
                                append("device_id", call.request.queryParameters["device_id"]!!)
                            }
                        }.build()
                        setBody(TextContent(params.formUrlEncode(), ContentType.Application.FormUrlEncoded))
                    }
                )
            }
        }
    }
}
internal fun Route.browserAuthRoutes(keys: JwkProvider? = null) {
    val service: AuthService by application.dependencies
    val identities: IdentityRepository by application.dependencies
    val config: AuthConfig by application.dependencies
    val http: HttpClient by application.dependencies
    val fspKeys = keys ?: config.fsp?.let {
        JwkProviderBuilder(URI(config.jwksUrl).toURL()).cached(10, 24, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES).build()
    }
    fun ApplicationCall.cookie() = sessions.get<BrowserCookie>()?.value
    suspend fun response(session: BrowserSession) = SessionResponse(session.identity != null, session.identity?.email, session.csrf,
        Providers(config.fsp != null, config.vk != null), session.identity?.let { identities.linkedProviders(it.id) } ?: Providers(false, false),
        emailVerificationRequired = session.identity?.let { it.provider == "local" && !it.verified } == true)
    suspend fun ApplicationCall.sessionResponse(): SessionResponse {
        val cookie = cookie() ?: identities.issueSession(null, null).also { sessions.set(BrowserCookie(it)) }
        val session = identities.session(cookie) ?: identities.issueSession(cookie, null).also { sessions.set(BrowserCookie(it)) }.let { identities.session(it)!! }
        return response(session)
    }
    suspend fun ApplicationCall.rotate(identity: UUID?): SessionResponse {
        val fresh = identities.issueSession(cookie(), identity)
        sessions.set(BrowserCookie(fresh))
        return response(identities.session(fresh)!!)
    }
    route("/api/auth") {
        get("/session") { call.response.headers.append(HttpHeaders.CacheControl, "no-store"); call.respond(call.sessionResponse()) }
        rateLimit(RateLimitName("login")) {
            post("/register") {
                call.requireCsrf(config.frontendOrigin, identities.session(call.cookie()))
                val id = service.register(call.receive<Credentials>())
                val session = call.rotate(id)
                try { service.sendVerification(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { throw ApiError(HttpStatusCode.ServiceUnavailable, "Account created, but email could not be sent. Sign in and request another verification email.") }
                call.respond(HttpStatusCode.Created, session)
            }
            post("/login") {
                call.requireCsrf(config.frontendOrigin, identities.session(call.cookie()))
                val id = service.login(call.receive<Credentials>()) ?: throw ApiError(HttpStatusCode.Unauthorized, "Invalid email or password")
                call.respond(call.rotate(id))
            }
            post("/verify-email") {
                call.requireCsrf(config.frontendOrigin, identities.session(call.cookie()))
                service.verifyEmail(call.receive<EmailVerification>().token)
                    ?: throw ApiError(HttpStatusCode.BadRequest, "Verification link is invalid or expired")
                // Mailbox proof is not a login credential; preserve only the existing authenticated identity.
                call.respond(call.rotate(identities.session(call.cookie())?.identity?.id))
            }
            post("/verification-email") {
                val session = identities.session(call.cookie())
                call.requireCsrf(config.frontendOrigin, session)
                val id = session?.identity?.id ?: throw ApiError(HttpStatusCode.Unauthorized, "Sign in to request a verification email")
                try { service.sendVerification(id) }
                catch (e: CancellationException) { throw e }
                catch (e: ru.fsp.career.shared.domain.DomainError) { throw e }
                catch (_: Exception) { throw ApiError(HttpStatusCode.ServiceUnavailable, "Email could not be sent; try again later") }
                call.respond(HttpStatusCode.NoContent)
            }
        }
        post("/logout") { call.requireCsrf(config.frontendOrigin, identities.session(call.cookie())); call.respond(call.rotate(null)) }
        for ((provider, cfg) in listOf("fsp" to config.fsp, "vk" to config.vk)) {
            if (cfg == null) {
                get("/$provider/start") { throw ApiError(HttpStatusCode.NotFound, "Provider disabled") }
                post("/$provider/link") { throw ApiError(HttpStatusCode.NotFound, "Provider disabled") }
                get("/$provider/callback") { call.respondRedirect("${config.frontendOrigin}/app?authError=failed") }
                continue
            }
            route("/$provider") {
                install(DoubleReceive)
                install(createRouteScopedPlugin("PrepareOAuth-$provider") {
                    onCall { call ->
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.response.headers.append("Referrer-Policy", "no-referrer")
                        val state: String
                        val attempt: OAuthAttempt
                        if (call.request.path().endsWith("/start") || call.request.path().endsWith("/link")) {
                            if (!call.request.queryParameters.isEmpty()) throw OAuthLoginFailed()
                            val previous = call.cookie()
                            val cookie = previous?.takeIf { identities.session(it) != null }
                                ?: identities.issueSession(previous, null).also { call.sessions.set(BrowserCookie(it)) }
                            val session = identities.session(cookie)
                            if (call.request.path().endsWith("/link")) {
                                if (session?.identity == null) throw ApiError(HttpStatusCode.Unauthorized, "Authentication required")
                                val token = call.receiveParameters()["csrfToken"]
                                if (!csrfValid(call.request.headers[HttpHeaders.Origin], config.frontendOrigin, token, session))
                                    throw ApiError(HttpStatusCode.Forbidden, "Invalid origin or CSRF token")
                            } else if (session?.identity != null) throw OAuthLoginFailed()
                            state = opaque(); attempt = OAuthAttempt(opaque(), opaque())
                            identities.saveState(state, cookie, provider, attempt)
                        } else {
                            state = call.request.queryParameters["state"].orEmpty()
                            attempt = identities.consumeState(state, call.cookie(), provider) ?: throw OAuthLoginFailed()
                            val session = identities.session(call.cookie()) ?: throw OAuthLoginFailed()
                            session.identity?.id?.let { call.attributes.put(oauthIdentity, it) }
                            val code = call.request.queryParameters["code"]
                            if (code.isNullOrBlank() || code.length > 2048 || call.request.queryParameters["error"] != null ||
                                (provider == "vk" && call.request.queryParameters["device_id"]?.length !in 1..256)) throw OAuthLoginFailed()
                        }
                        call.attributes.put(oauthState, state); call.attributes.put(oauthAttempt, attempt)
                    }
                })
                authenticate(provider) {
                    get("/start") { throw OAuthLoginFailed() }
                    post("/link") { throw OAuthLoginFailed() }
                }
                authenticate(provider, strategy = AuthenticationStrategy.Optional) {
                    get("/callback") {
                        try {
                            val token = call.principal<OAuthAccessTokenResponse.OAuth2>() ?: throw OAuthLoginFailed()
                            val attempt = call.attributes[oauthAttempt]
                            val (subject, email, verified) = if (provider == "fsp") withContext(Dispatchers.IO) {
                                verifiedFspIdentity(token.extraParameters["id_token"] ?: error("Missing ID token"), fspKeys!!, config.issuer, cfg.clientId, attempt.nonce)
                            } else {
                                require(token.extraParameters["state"] == call.attributes[oauthState])
                                val info = http.submitForm("https://id.vk.ru/oauth2/user_info", parameters {
                                    append("client_id", cfg.clientId); append("access_token", token.accessToken)
                                }).bodyAsText().let { Json.parseToJsonElement(it).jsonObject["user"]!!.jsonObject }
                                require(info["user_id"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true && token.extraParameters["user_id"] == info["user_id"]?.jsonPrimitive?.content)
                                Triple(info["user_id"]!!.jsonPrimitive.content, "", false)
                            }
                            // State is bound to an immutable session: anonymous means sign-in, authenticated means linking.
                            val linkTo = call.attributes.getOrNull(oauthIdentity)
                            val session = identities.session(call.cookie()) ?: throw OAuthLoginFailed()
                            if (session.identity?.id != linkTo) throw OAuthLoginFailed()
                            val identity = service.external(provider, if (provider == "fsp") config.issuer else "https://id.vk.ru", subject, email, verified, config.legacyIssuer, linkTo)
                            call.rotate(identity)
                            call.respondRedirect("${config.frontendOrigin}/app" + if (linkTo != null) "?section=profile" else "")
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            val query = if (identities.session(call.cookie())?.identity != null) "section=profile&providerLinkError=failed" else "authError=failed"
                            call.respondRedirect("${config.frontendOrigin}/app?$query")
                        }
                    }
                }
            }
        }
    }
}
