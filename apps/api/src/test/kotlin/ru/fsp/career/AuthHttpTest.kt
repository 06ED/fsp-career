package ru.fsp.career

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.*
import java.util.UUID

class AuthHttpTest {
    @Test fun registerRoleAndLogoutRequireCsrf() = testApplication {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            application { careerModule(db, "http://issuer.invalid", "career") }
            val bootstrap = client.get("/api/auth/session")
            assertEquals(HttpStatusCode.OK, bootstrap.status)
            val initial = Json.parseToJsonElement(bootstrap.bodyAsText()).jsonObject
            assertEquals(false, initial["authenticated"]!!.jsonPrimitive.boolean)
            val cookie = bootstrap.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
            val csrf = initial["csrfToken"]!!.jsonPrimitive.content
            val email = "auth-${UUID.randomUUID()}@example.com"
            try {
                val rejected = client.post("/api/auth/register") {
                    header(HttpHeaders.Cookie, cookie)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"$email","password":"safe password 123"}""")
                }
                assertEquals(HttpStatusCode.Forbidden, rejected.status)
                val registered = client.post("/api/auth/register") {
                    header(HttpHeaders.Cookie, cookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", csrf)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"$email","password":"safe password 123"}""")
                }
                assertEquals(HttpStatusCode.Created, registered.status)
                val currentCookie = registered.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                val logged = Json.parseToJsonElement(registered.bodyAsText()).jsonObject
                assertEquals(true, logged["authenticated"]!!.jsonPrimitive.boolean)
                assertEquals(email, logged["email"]!!.jsonPrimitive.content)
                val currentCsrf = logged["csrfToken"]!!.jsonPrimitive.content
                assertNotEquals(csrf, currentCsrf)
                assertEquals(HttpStatusCode.Forbidden, client.post("/api/me/role") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", csrf)
                    contentType(ContentType.Application.Json); setBody("""{"role":"candidate"}""")
                }.status)
                assertEquals(HttpStatusCode.OK, client.post("/api/me/role") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", currentCsrf)
                    contentType(ContentType.Application.Json); setBody("""{"role":"candidate"}""")
                }.status)
                val me = Json.parseToJsonElement(client.get("/api/me") {
                    header(HttpHeaders.Cookie, currentCookie)
                }.bodyAsText()).jsonObject
                assertEquals("candidate", me["role"]!!.jsonPrimitive.content)
                assertFalse(me["emailVerified"]!!.jsonPrimitive.boolean)
                assertEquals(HttpStatusCode.OK, client.put("/api/candidates/me") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", currentCsrf)
                    contentType(ContentType.Application.Json)
                    setBody("""{"fullName":"Local candidate","processingConsent":true}""")
                }.status)
                assertEquals(HttpStatusCode.BadRequest, client.put("/api/candidates/me") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", currentCsrf)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"$email","processingConsent":true}""")
                }.status)
                val logout = client.post("/api/auth/logout") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", currentCsrf)
                }
                assertEquals(HttpStatusCode.OK, logout.status)
                assertEquals(false, Json.parseToJsonElement(logout.bodyAsText()).jsonObject["authenticated"]!!.jsonPrimitive.boolean)
                assertEquals(false, Json.parseToJsonElement(client.get("/api/auth/session") {
                    header(HttpHeaders.Cookie, currentCookie)
                }.bodyAsText()).jsonObject["authenticated"]!!.jsonPrimitive.boolean)
                assertEquals(HttpStatusCode.Forbidden, client.post("/api/me/role") {
                    header(HttpHeaders.Cookie, currentCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", currentCsrf)
                    contentType(ContentType.Application.Json); setBody("""{"role":"candidate"}""")
                }.status)
                val anonymousCookie = logout.headers[HttpHeaders.SetCookie]!!.substringBefore(';')
                val anonymousCsrf = Json.parseToJsonElement(logout.bodyAsText()).jsonObject["csrfToken"]!!.jsonPrimitive.content
                val incorrect = client.post("/api/auth/login") {
                    header(HttpHeaders.Cookie, anonymousCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", anonymousCsrf)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"$email","password":"wrong password 123"}""")
                }
                assertEquals(HttpStatusCode.Unauthorized, incorrect.status)
                val again = client.post("/api/auth/login") {
                    header(HttpHeaders.Cookie, anonymousCookie); header(HttpHeaders.Origin, "http://localhost:5173")
                    header("X-CSRF-Token", anonymousCsrf)
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":"$email","password":"safe password 123"}""")
                }
                assertEquals(HttpStatusCode.OK, again.status)
                assertEquals(true, Json.parseToJsonElement(again.bodyAsText()).jsonObject["authenticated"]!!.jsonPrimitive.boolean)
            } finally {
                db.tx {
                    val identity = AuthIdentities.selectAll().where { AuthIdentities.email eq email }.singleOrNull()
                    identity?.let { row ->
                        AuthIdentities.deleteWhere { AuthIdentities.id eq row[AuthIdentities.id] }
                        row[AuthIdentities.accountId]?.let { account -> Accounts.deleteWhere { Accounts.id eq account } }
                    }
                }
            }
        }
    }
}
