package ru.fsp.career

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val random = SecureRandom()
internal fun opaque(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
private fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

internal object Passwords {
    // ponytail: PBKDF2 is JDK-native; upgrade the version/iteration count when the hardware budget changes.
    private const val rounds = 600_000
    private val workers = Dispatchers.IO.limitedParallelism(4)
    suspend fun hash(password: String): String = withContext(workers) {
        val salt = ByteArray(16).also(random::nextBytes)
        "pbkdf2-sha256:$rounds:${Base64.getEncoder().encodeToString(salt)}:${derive(password, salt, rounds)}"
    }
    suspend fun verify(password: String, encoded: String): Boolean = withContext(workers) {
        val parts = encoded.split(':')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256" || parts[1] != "$rounds") false
        else equal(derive(password, Base64.getDecoder().decode(parts[2]), rounds), parts[3])
    }
    private fun derive(password: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return try { Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded) }
        finally { spec.clearPassword() }
    }
}

@Serializable data class BrowserCookie(val value: String)
@Serializable data class Credentials(val email: String, val password: String)
@Serializable data class Providers(val fsp: Boolean, val vk: Boolean)
@Serializable data class SessionResponse(val authenticated: Boolean, val email: String?, val csrfToken: String, val providers: Providers)

internal data class BrowserIdentity(val id: UUID, val subject: String, val email: String, val verified: Boolean)
internal data class BrowserSession(val identity: BrowserIdentity?, val csrf: String)
internal data class OAuthAttempt(val verifier: String, val nonce: String)
private val oauthAttempt = AttributeKey<OAuthAttempt>("oauthAttempt")
private val oauthState = AttributeKey<String>("oauthState")
internal class OAuthLoginFailed : RuntimeException()

internal suspend fun Database.session(cookie: String?): BrowserSession? {
    if (cookie == null || cookie.length != 43) return null
    return tx {
        val row = BrowserSessions.selectAll().where { BrowserSessions.idHash eq digest(cookie) }.singleOrNull()
            ?.takeIf { it[BrowserSessions.expiresAt].isAfter(now()) } ?: return@tx null
        val identity = row[BrowserSessions.identityId]?.let { id ->
            AuthIdentities.selectAll().where { AuthIdentities.id eq id }.singleOrNull()?.let {
                BrowserIdentity(id, it[AuthIdentities.accountId]?.let { account ->
                    Accounts.selectAll().where { Accounts.id eq account }.single()[Accounts.subject]
                } ?: "pending:$id", it[AuthIdentities.email], it[AuthIdentities.emailVerified])
            }
        }
        BrowserSession(identity, row[BrowserSessions.csrfToken])
    }
}

internal suspend fun Database.issueSession(old: String?, identity: UUID?): String = tx {
    if (old != null) BrowserSessions.deleteWhere { BrowserSessions.idHash eq digest(old) }
    val token = opaque()
    BrowserSessions.insert {
        it[idHash] = digest(token)
        it[identityId] = identity
        it[csrfToken] = opaque()
        it[expiresAt] = now().plusDays(7)
    }
    token
}

internal suspend fun Database.consumeState(state: String?, cookie: String?, provider: String): OAuthAttempt? {
    if (state == null || state.length != 43 || cookie == null || cookie.length != 43) return null
    return tx {
        val row = OauthTransactions.selectAll().where { OauthTransactions.stateHash eq digest(state) }.forUpdate().singleOrNull()
            ?: return@tx null
        OauthTransactions.deleteWhere { OauthTransactions.stateHash eq digest(state) }
        if (row[OauthTransactions.provider] != provider || row[OauthTransactions.sessionHash] != digest(cookie) ||
            !row[OauthTransactions.expiresAt].isAfter(now()) ||
            BrowserSessions.selectAll().where { BrowserSessions.idHash eq digest(cookie) }.singleOrNull()
                ?.get(BrowserSessions.expiresAt)?.isAfter(now()) != true) null
        else OAuthAttempt(row[OauthTransactions.verifier], row[OauthTransactions.nonce])
    }
}

internal data class ProviderConfig(val clientId: String, val secret: String, val redirectUri: String)
internal class AuthConfig(val frontendOrigin: String, val apiOrigin: String, val issuer: String, val legacyIssuer: String,
    val fsp: ProviderConfig?, val vk: ProviderConfig?, val authUrl: String, val tokenUrl: String, val jwksUrl: String,
    val secure: Boolean) {
    companion object {
        fun fromEnv(issuer: String): AuthConfig {
            fun env(key: String) = System.getenv(key)?.takeIf(String::isNotBlank)
            fun provider(prefix: String, callback: String, api: String): ProviderConfig? {
                val id = env("${prefix}_CLIENT_ID")
                val secret = env("${prefix}_CLIENT_SECRET")
                return if (id == null || secret == null) null else ProviderConfig(id, secret, "$api/api/auth/$callback/callback")
            }
            val frontend = env("FRONTEND_ORIGIN") ?: "http://localhost:5173"
            val api = env("API_ORIGIN") ?: "http://localhost:8080"
            for (value in listOf(frontend, api)) {
                val parsed = URI(value)
                require(parsed.host != null && parsed.userInfo == null && parsed.rawQuery == null &&
                    parsed.rawFragment == null && parsed.path in listOf("", "/") &&
                    parsed.scheme in listOf("http", "https")) { "Invalid configured origin" }
            }
            val secure = env("APP_ENV") == "production"
            if (secure) require(frontend.startsWith("https://") && api.startsWith("https://") &&
                env("FRONTEND_ORIGIN") != null && env("API_ORIGIN") != null) { "Production origins must be explicit HTTPS" }
            val fspIssuer = (env("FSP_ISSUER") ?: issuer).trimEnd('/')
            if (secure) require(fspIssuer.startsWith("https://")) { "FSP_ISSUER must be HTTPS in production" }
            // ponytail: the public issuer stays in JWT validation; only back-channel URLs use Docker DNS.
            if (secure) require(env("FSP_AUTH_URL")?.startsWith("https://") != false)
            return AuthConfig(frontend.trimEnd('/'), api.trimEnd('/'), fspIssuer, issuer,
                provider("FSP", "fsp", api.trimEnd('/')), provider("VK", "vk", api.trimEnd('/')),
                env("FSP_AUTH_URL") ?: "$fspIssuer/protocol/openid-connect/auth",
                env("FSP_TOKEN_URL") ?: "$fspIssuer/protocol/openid-connect/token",
                env("FSP_JWKS_URL") ?: "$fspIssuer/protocol/openid-connect/certs", secure)
        }
    }
}

internal fun ApplicationCall.requireCsrf(origin: String, session: BrowserSession?) {
    if (!csrfValid(request.headers[HttpHeaders.Origin], origin, request.headers["X-CSRF-Token"], session))
        throw ApiError(HttpStatusCode.Forbidden, "Invalid origin or CSRF token")
}

internal fun csrfValid(actualOrigin: String?, allowedOrigin: String, token: String?, session: BrowserSession?): Boolean =
    actualOrigin == allowedOrigin && session != null && equal(token.orEmpty(), session.csrf)

internal fun verifiedFspIdentity(token: String, keys: JwkProvider, issuer: String, audience: String, nonce: String): Triple<String, String, Boolean> {
    val decoded = JWT.decode(token)
    require(decoded.algorithm == "RS256")
    val key = keys.get(decoded.keyId)
    val claims = JWT.require(Algorithm.RSA256(key.publicKey as java.security.interfaces.RSAPublicKey, null))
        .withIssuer(issuer).withAudience(audience).build().verify(token)
    require(claims.expiresAt != null && claims.getClaim("nonce").asString() == nonce && claims.subject.isNotBlank())
    return Triple(claims.subject, claims.getClaim("email").asString().orEmpty(),
        claims.getClaim("email_verified").asBoolean() == true)
}

internal fun Transaction.providerIdentity(provider: String, issuer: String, subject: String,
    email: String, verified: Boolean, legacyIssuer: String): UUID {
    require(subject.isNotBlank() && subject.length <= 256)
    val existing = AuthIdentities.selectAll().where { (AuthIdentities.provider eq provider) and
        (AuthIdentities.issuer eq issuer) and (AuthIdentities.externalSubject eq subject) }.singleOrNull()
    val legacy = if (provider == "fsp" && issuer == legacyIssuer && !subject.startsWith("demo-seed") &&
        !subject.startsWith("user:"))
        Accounts.selectAll().where { Accounts.subject eq subject }.singleOrNull()?.get(Accounts.id) else null
    if (existing != null) {
        AuthIdentities.update({ AuthIdentities.id eq existing[AuthIdentities.id] }) {
            if (existing[AuthIdentities.accountId] == null && legacy != null) it[accountId] = legacy
            it[AuthIdentities.email] = email
            it[emailVerified] = verified
        }
        return existing[AuthIdentities.id]
    }
    val id = UUID.randomUUID()
    // Only FSP's verified issuer+subject can claim a pre-existing Keycloak account; never email-match.
    AuthIdentities.insert {
        it[AuthIdentities.id] = id; it[AuthIdentities.provider] = provider; it[AuthIdentities.issuer] = issuer
        it[externalSubject] = subject; it[accountId] = legacy
        it[AuthIdentities.email] = email; it[emailVerified] = verified
    }
    return id
}

internal fun Application.browserAuth(config: AuthConfig) {
    install(Sessions) {
        cookie<BrowserCookie>("career_session") {
            cookie.path = "/api"
            cookie.httpOnly = true
            cookie.secure = config.secure
            cookie.extensions["SameSite"] = "Lax"
            cookie.maxAgeInSeconds = 7 * 24 * 3600
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
    expectSuccess = true
    followRedirects = false
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
                    name = provider,
                    authorizeUrl = if (provider == "fsp") config.authUrl else "https://id.vk.ru/authorize",
                    accessTokenUrl = if (provider == "fsp") config.tokenUrl else "https://id.vk.ru/oauth2/auth",
                    requestMethod = HttpMethod.Post,
                    clientId = cfg.clientId, clientSecret = cfg.secret,
                    defaultScopes = if (provider == "fsp") listOf("openid", "email") else listOf("vkid.personal_info"),
                    nonceManager = object : NonceManager {
                        override suspend fun newNonce(): String {
                            // Ktor retries invalid_grant with a new challenge; callbacks must fail instead.
                            if (!call.request.path().endsWith("/start")) throw OAuthLoginFailed()
                            return call.attributes[oauthState]
                        }
                        override suspend fun verifyNonce(nonce: String) = equal(nonce, call.attributes[oauthState])
                    },
                    extraAuthParameters = listOf(
                        "code_challenge" to Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(attempt.verifier.toByteArray())),
                        "code_challenge_method" to "S256"
                    ) + if (provider == "fsp") listOf("nonce" to attempt.nonce) else emptyList(),
                    accessTokenInterceptor = {
                        val params = ParametersBuilder().apply {
                            appendAll((body as TextContent).text.parseUrlEncodedParameters())
                            append("code_verifier", attempt.verifier)
                            if (provider == "vk") {
                                remove("client_secret")
                                append("service_token", cfg.secret)
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

internal fun Route.browserAuthRoutes(db: Database, config: AuthConfig, http: HttpClient, keys: JwkProvider? = null) {
    val fspKeys = keys ?: config.fsp?.let {
        JwkProviderBuilder(URI(config.jwksUrl).toURL()).cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES).build()
    }
    fun ApplicationCall.cookie() = sessions.get<BrowserCookie>()?.value
    suspend fun ApplicationCall.response(): SessionResponse {
        val cookie = cookie() ?: db.issueSession(null, null).also { sessions.set(BrowserCookie(it)) }
        val session = db.session(cookie) ?: db.issueSession(cookie, null).also { sessions.set(BrowserCookie(it)) }.let { db.session(it)!! }
        return SessionResponse(session.identity != null, session.identity?.email, session.csrf, Providers(config.fsp != null, config.vk != null))
    }
    suspend fun ApplicationCall.rotate(identity: UUID?): SessionResponse {
        val fresh = db.issueSession(cookie(), identity)
        sessions.set(BrowserCookie(fresh))
        val session = db.session(fresh)!!
        return SessionResponse(session.identity != null, session.identity?.email, session.csrf, Providers(config.fsp != null, config.vk != null))
    }
    route("/api/auth") {
        get("/session") {
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
            call.respond(call.response())
        }
        rateLimit(RateLimitName("login")) {
            post("/register") {
                call.requireCsrf(config.frontendOrigin, db.session(call.cookie()))
                val input = call.receive<Credentials>()
                val email = input.email.trim().lowercase()
                if (email.length !in 3..320 || !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email) ||
                    input.password.length !in 12..128) bad("Invalid email or password")
                val hash = Passwords.hash(input.password)
                val identity = try { db.tx {
                    val id = UUID.randomUUID()
                    AuthIdentities.insert {
                        it[AuthIdentities.id] = id; it[provider] = "local"; it[issuer] = "local"
                        it[externalSubject] = id.toString(); it[AuthIdentities.email] = email; it[emailVerified] = false
                    }
                    LocalCredentials.insert { it[identityId] = id; it[passwordHash] = hash }
                    id
                } } catch (e: org.jetbrains.exposed.exceptions.ExposedSQLException) {
                    if (e.sqlState != "23505") throw e
                    throw ApiError(HttpStatusCode.BadRequest, "Invalid email or password")
                }
                call.respond(HttpStatusCode.Created, call.rotate(identity))
            }
            post("/login") {
                call.requireCsrf(config.frontendOrigin, db.session(call.cookie()))
                val input = call.receive<Credentials>()
                if (input.email.length > 320 || input.password.length > 128) bad("Invalid email or password")
                val hash = db.tx {
                    val id = AuthIdentities.selectAll().where { (AuthIdentities.provider eq "local") and
                        (AuthIdentities.email eq input.email.trim().lowercase()) }.singleOrNull()?.get(AuthIdentities.id)
                    id?.let { LocalCredentials.selectAll().where { LocalCredentials.identityId eq it }.singleOrNull()?.get(LocalCredentials.passwordHash) }
                }
                // Equal-cost miss to avoid trivially revealing which email exists.
                val valid = Passwords.verify(input.password, hash ?: dummyHash)
                if (!valid || hash == null) throw ApiError(HttpStatusCode.Unauthorized, "Invalid email or password")
                val id = db.tx { AuthIdentities.selectAll().where { (AuthIdentities.provider eq "local") and
                    (AuthIdentities.email eq input.email.trim().lowercase()) }.single()[AuthIdentities.id] }
                call.respond(call.rotate(id))
            }
        }
        post("/logout") {
            call.requireCsrf(config.frontendOrigin, db.session(call.cookie()))
            call.respond(call.rotate(null))
        }
        for ((provider, cfg) in listOf("fsp" to config.fsp, "vk" to config.vk)) {
            if (cfg == null) {
                get("/$provider/start") { throw ApiError(HttpStatusCode.NotFound, "Provider disabled") }
                get("/$provider/callback") { call.respondRedirect("${config.frontendOrigin}/app?authError=failed") }
                continue
            }
            route("/$provider") {
                install(createRouteScopedPlugin("PrepareOAuth-$provider") {
                    onCall { call ->
                        call.response.headers.append(HttpHeaders.CacheControl, "no-store")
                        call.response.headers.append("Referrer-Policy", "no-referrer")
                        val state: String
                        val attempt: OAuthAttempt
                        if (call.request.path().endsWith("/start")) {
                            if (!call.request.queryParameters.isEmpty()) throw OAuthLoginFailed()
                            val previous = call.cookie()
                            val cookie = previous?.takeIf { db.session(it) != null }
                                ?: db.issueSession(previous, null).also { call.sessions.set(BrowserCookie(it)) }
                            if (db.session(cookie)?.identity != null) throw OAuthLoginFailed()
                            state = opaque(); attempt = OAuthAttempt(opaque(), opaque())
                            db.tx { OauthTransactions.insert {
                                it[stateHash] = digest(state); it[sessionHash] = digest(cookie); it[OauthTransactions.provider] = provider
                                it[verifier] = attempt.verifier; it[nonce] = attempt.nonce; it[expiresAt] = now().plusMinutes(5)
                            } }
                        } else {
                            state = call.request.queryParameters["state"].orEmpty()
                            attempt = db.consumeState(state, call.cookie(), provider) ?: throw OAuthLoginFailed()
                            val code = call.request.queryParameters["code"]
                            if (code.isNullOrBlank() || code.length > 2048 || call.request.queryParameters["error"] != null ||
                                db.session(call.cookie())?.identity != null || (provider == "vk" &&
                                call.request.queryParameters["device_id"]?.length !in 1..256)) throw OAuthLoginFailed()
                        }
                        call.attributes.put(oauthState, state)
                        call.attributes.put(oauthAttempt, attempt)
                    }
                })
                authenticate(provider) {
                    get("/start") { throw OAuthLoginFailed() }
                }
                authenticate(provider, strategy = AuthenticationStrategy.Optional) {
                    get("/callback") {
                        try {
                            val token = call.principal<OAuthAccessTokenResponse.OAuth2>() ?: throw OAuthLoginFailed()
                            val attempt = call.attributes[oauthAttempt]
                            val (subject, email, verified) = if (provider == "fsp") {
                                withContext(Dispatchers.IO) {
                                    verifiedFspIdentity(token.extraParameters["id_token"] ?: error("Missing ID token"),
                                        fspKeys!!, config.issuer, cfg.clientId, attempt.nonce)
                                }
                            } else {
                                require(token.extraParameters["state"] == call.attributes[oauthState])
                                val info = http.submitForm("https://id.vk.ru/oauth2/user_info", parameters {
                                    append("client_id", cfg.clientId); append("access_token", token.accessToken)
                                }).bodyAsText().let { Json.parseToJsonElement(it).jsonObject["user"]!!.jsonObject }
                                require(info["user_id"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
                                    token.extraParameters["user_id"] == info["user_id"]?.jsonPrimitive?.content)
                                Triple(info["user_id"]!!.jsonPrimitive.content, "", false)
                            }
                            val issuer = if (provider == "fsp") config.issuer else "https://id.vk.ru"
                            val identity = db.tx { providerIdentity(provider, issuer, subject, email, verified, config.legacyIssuer) }
                            // Do not turn a provider sign-in into silent account linking.
                            call.rotate(identity)
                            call.respondRedirect("${config.frontendOrigin}/app")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            call.respondRedirect("${config.frontendOrigin}/app?authError=failed")
                        }
                    }
                }
            }
        }
    }
}

// Fixed, correctly formatted PBKDF2 hash for unknown users (generated from a non-secret password).
private val dummyHash by lazy {
    val salt = ByteArray(16)
    val spec = PBEKeySpec("unknown".toCharArray(), salt, 600_000, 256)
    "pbkdf2-sha256:600000:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)}"
}
