package ru.fsp.career

import ru.fsp.career.identity.infrastructure.*
import ru.fsp.career.shared.infrastructure.Database

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.plugins.di.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.*

class OAuthHttpTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = pair.public as RSAPublicKey
    private val algorithm = Algorithm.RSA256(publicKey, pair.private as RSAPrivateKey)
    private val keys = JwkProvider { Jwk.fromValues(mapOf("kid" to "test", "kty" to "RSA", "alg" to "RS256",
        "n" to Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()),
        "e" to Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.publicExponent.toByteArray()))) }
    private val issuer = "https://fsp.example/realms/fsp"
    private fun idToken(nonce: String, subject: String = "subject", aud: String = "fsp-service", iss: String = issuer,
        expiry: Instant = Instant.now().plusSeconds(60)) = JWT.create().withKeyId("test").withIssuer(iss)
        .withAudience(aud).withSubject(subject).withExpiresAt(Date.from(expiry)).withClaim("nonce", nonce)
        .withClaim("email", "verified@example.com").withClaim("email_verified", true).sign(algorithm)

    @Test fun idTokenChecksIssuerAudienceNonceExpiryAndSignature() {
        assertEquals(Triple("subject", "verified@example.com", true), verifiedFspIdentity(idToken("nonce"), keys, issuer, "fsp-service", "nonce"))
        for (token in listOf(idToken("wrong"), idToken("nonce", aud = "wrong"), idToken("nonce", iss = "https://other.example"),
            idToken("nonce", expiry = Instant.now().minusSeconds(60)), JWT.create().withSubject("subject").sign(Algorithm.none()))) {
            assertFails { verifiedFspIdentity(token, keys, issuer, "fsp-service", "nonce") }
        }
        val forged = JWT.create().withKeyId("test").withIssuer(issuer).withAudience("fsp-service").withSubject("subject")
            .withExpiresAt(Date.from(Instant.now().plusSeconds(60))).withClaim("nonce", "nonce")
            .sign(Algorithm.RSA256(null, KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private as RSAPrivateKey))
        assertFails { verifiedFspIdentity(forged, keys, issuer, "fsp-service", "nonce") }
    }

    @Test fun ktorOAuthExchangesBothProvidersAndRejectsReplayAndBadCallbacks() = testApplication {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            val subject = "oauth-${UUID.randomUUID()}"
            var nonce = ""
            var rejected = false
            var exchanges = 0
            var revokeDuringExchange: String? = null
            val http = HttpClient(MockEngine { request ->
                val params = request.body.toByteArray().decodeToString().parseUrlEncodedParameters()
                assertEquals("POST", request.method.value)
                if (request.url.encodedPath.endsWith("user_info")) {
                    assertEquals("access", params["access_token"])
                    respond("""{"user":{"user_id":"$subject"}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    exchanges++
                    revokeDuringExchange?.let { db.issueSession(it, null); revokeDuringExchange = null }
                    assertEquals("authorization_code", params["grant_type"])
                    assertEquals("test-code", params["code"])
                    assertEquals(43, params["code_verifier"]?.length)
                    if (request.url.host == "id.vk.ru") {
                        assertEquals("device", params["device_id"])
                        assertEquals("service-secret", params["service_token"])
                        assertNull(params["client_secret"])
                    } else assertEquals("fsp-secret", params["client_secret"])
                    val body = if (rejected) """{"error":"invalid_grant"}"""
                        else if (request.url.host == "id.vk.ru") """{"access_token":"access","state":"${params["state"]}","user_id":"$subject"}"""
                        else """{"access_token":"access","id_token":"${idToken(nonce, subject)}"}"""
                    respond(body, if (rejected) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
            })
            val config = AuthConfig("http://localhost:5173", "http://localhost:8080", issuer, issuer,
                ProviderConfig("fsp-service", "fsp-secret", "http://localhost:8080/api/auth/fsp/callback"),
                ProviderConfig("vk-service", "service-secret", "http://localhost:8080/api/auth/vk/callback"),
                "$issuer/protocol/openid-connect/auth", "$issuer/protocol/openid-connect/token", "$issuer/protocol/openid-connect/certs", false)
            application {
                dependencies {
                    provide<Database> { db }
                    provide<AuthConfig> { config }
                    provide<HttpClient> { http }
                }
                careerModule(issuer, "fsp-api", fspKeys = keys)
            }
            val browser = createClient { followRedirects = false }
            for (provider in listOf("fsp", "vk")) {
                suspend fun start(): Pair<String, String> {
                    val response = browser.get("/api/auth/$provider/start")
                    assertEquals(HttpStatusCode.Found, response.status)
                    val redirect = Url(response.headers[HttpHeaders.Location]!!)
                    assertEquals("S256", redirect.parameters["code_challenge_method"])
                    assertEquals("http://localhost:8080/api/auth/$provider/callback", redirect.parameters["redirect_uri"])
                    nonce = redirect.parameters["nonce"].orEmpty()
                    return redirect.parameters["state"]!! to response.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                }
                val (state, _) = start()
                val callback = "/api/auth/$provider/callback?code=test-code&state=$state" + if (provider == "vk") "&device_id=device" else ""
                assertEquals("http://localhost:5173/app?authError=failed", browser.get(callback) {
                    header(HttpHeaders.Cookie, "career_session=invalid")
                }.headers[HttpHeaders.Location])
                assertEquals(0, exchanges)
                // An invalid browser consumes the state too: retry must start a fresh transaction.
                val (validState, validCookie) = start()
                val validCallback = callback.replace(state, validState)
                val logged = browser.get(validCallback) { header(HttpHeaders.Cookie, validCookie) }
                assertEquals("http://localhost:5173/app", logged.headers[HttpHeaders.Location], "provider=$provider exchanges=$exchanges")
                val currentCookie = logged.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                val session = Json.parseToJsonElement(browser.get("/api/auth/session") {
                    header(HttpHeaders.Cookie, currentCookie)
                }.bodyAsText()).jsonObject
                assertTrue(session["authenticated"]!!.jsonPrimitive.boolean)
                assertEquals(if (provider == "fsp") "verified@example.com" else "", session["email"]!!.jsonPrimitive.content)
                assertEquals("http://localhost:5173/app?authError=failed", browser.get(validCallback) {
                    header(HttpHeaders.Cookie, validCookie)
                }.headers[HttpHeaders.Location])
                assertEquals(1, exchanges)
                assertEquals("http://localhost:5173/app?authError=failed", browser.get("/api/auth/$provider/start") {
                    header(HttpHeaders.Cookie, currentCookie)
                }.headers[HttpHeaders.Location])
                rejected = true
                val (failedState, failedCookie) = start()
                val failed = browser.get(callback.replace(state, failedState)) { header(HttpHeaders.Cookie, failedCookie) }
                assertEquals("http://localhost:5173/app?authError=failed", failed.headers[HttpHeaders.Location])
                rejected = false
                val csrf = session["csrfToken"]!!.jsonPrimitive.content
                assertEquals(HttpStatusCode.OK, browser.post("/api/me/role") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, config.frontendOrigin)
                    header("X-CSRF-Token", csrf); contentType(ContentType.Application.Json)
                    setBody("""{"role":"candidate"}""")
                }.status)
                val meBefore = browser.get("/api/me") { header(HttpHeaders.Cookie, currentCookie) }.bodyAsText()
                val contactEmail = "contact-$provider@example.org"
                val saved = browser.put("/api/candidates/me") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, config.frontendOrigin)
                    header("X-CSRF-Token", csrf); contentType(ContentType.Application.Json)
                    setBody("""{"email":"$contactEmail","processingConsent":true}""")
                }
                assertEquals(HttpStatusCode.OK, saved.status)
                assertEquals(contactEmail, Json.parseToJsonElement(saved.bodyAsText()).jsonObject["email"]!!.jsonPrimitive.content)
                val profile = Json.parseToJsonElement(browser.get("/api/candidates/me") {
                    header(HttpHeaders.Cookie, currentCookie)
                }.bodyAsText()).jsonObject
                assertEquals(contactEmail, profile["email"]!!.jsonPrimitive.content)
                assertEquals(meBefore, browser.get("/api/me") { header(HttpHeaders.Cookie, currentCookie) }.bodyAsText())
                val otherProvider = if (provider == "fsp") "vk" else "fsp"
                assertEquals(HttpStatusCode.Unauthorized, browser.post("/api/auth/$otherProvider/link") {
                    header(HttpHeaders.Origin, config.frontendOrigin)
                    contentType(ContentType.Application.FormUrlEncoded); setBody("csrfToken=$csrf")
                }.status)
                assertEquals(HttpStatusCode.Forbidden, browser.post("/api/auth/$otherProvider/link") {
                    header(HttpHeaders.Cookie, currentCookie)
                    contentType(ContentType.Application.FormUrlEncoded); setBody("csrfToken=$csrf")
                }.status)
                assertEquals(HttpStatusCode.Forbidden, browser.post("/api/auth/$otherProvider/link") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, config.frontendOrigin)
                    contentType(ContentType.Application.FormUrlEncoded); setBody("csrfToken=wrong")
                }.status)
                val link = browser.post("/api/auth/$otherProvider/link") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, config.frontendOrigin)
                    contentType(ContentType.Application.FormUrlEncoded); setBody("csrfToken=$csrf")
                }
                assertEquals(HttpStatusCode.Found, link.status)
                val linkRedirect = Url(link.headers[HttpHeaders.Location]!!)
                nonce = linkRedirect.parameters["nonce"].orEmpty()
                val linkCallback = "/api/auth/$otherProvider/callback?code=test-code&state=${linkRedirect.parameters["state"]}" +
                    if (otherProvider == "vk") "&device_id=device" else ""
                val linked = browser.get(linkCallback) { header(HttpHeaders.Cookie, currentCookie) }
                assertEquals("http://localhost:5173/app?section=profile", linked.headers[HttpHeaders.Location])
                val linkedCookie = linked.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                val linkedSession = Json.parseToJsonElement(browser.get("/api/auth/session") {
                    header(HttpHeaders.Cookie, linkedCookie)
                }.bodyAsText()).jsonObject
                assertEquals(session["email"], linkedSession["email"])
                assertTrue(linkedSession["linkedProviders"]!!.jsonObject["fsp"]!!.jsonPrimitive.boolean)
                assertTrue(linkedSession["linkedProviders"]!!.jsonObject["vk"]!!.jsonPrimitive.boolean)
                assertEquals(meBefore, browser.get("/api/me") { header(HttpHeaders.Cookie, linkedCookie) }.bodyAsText())
                val beforeReplay = exchanges
                assertEquals("http://localhost:5173/app?authError=failed", browser.get(linkCallback) {
                    header(HttpHeaders.Cookie, currentCookie)
                }.headers[HttpHeaders.Location])
                assertEquals(beforeReplay, exchanges)
                val revokedLink = browser.post("/api/auth/$otherProvider/link") {
                    header(HttpHeaders.Cookie, linkedCookie); header(HttpHeaders.Origin, config.frontendOrigin)
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody("csrfToken=${linkedSession["csrfToken"]!!.jsonPrimitive.content}")
                }
                val revokedRedirect = Url(revokedLink.headers[HttpHeaders.Location]!!)
                nonce = revokedRedirect.parameters["nonce"].orEmpty()
                revokeDuringExchange = Json.parseToJsonElement(linkedCookie.substringAfter('=').decodeURLQueryComponent()).jsonObject["value"]!!.jsonPrimitive.content
                val revokedCallback = linkCallback.replace(linkRedirect.parameters["state"]!!, revokedRedirect.parameters["state"]!!)
                val revoked = browser.get(revokedCallback) { header(HttpHeaders.Cookie, linkedCookie) }
                assertEquals("http://localhost:5173/app?authError=failed", revoked.headers[HttpHeaders.Location])
                assertNull(revoked.headers[HttpHeaders.SetCookie])
                exchanges = 0
            }
        }
    }
}
