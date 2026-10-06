package ru.fsp.career

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sun.net.httpserver.HttpServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end HTTP check of the matching / bank / card / privacy / FSP endpoints against the real Ktor module
 * and PostgreSQL. Tokens are signed by a throwaway RSA key served as JWKS — the same verification path as
 * Keycloak, without Keycloak. Runs only when TEST_DATABASE_URL is configured.
 * Tokens carry no application role: each user binds it once via POST /api/me/role, like the web app does.
 */
class MatchingHttpTest {
    private val http = HttpClient.newHttpClient()
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val audience = "fsp-api"
    private lateinit var issuer: String
    private lateinit var base: String

    private fun token(subject: String, @Suppress("UNUSED_PARAMETER") role: String, email: String) = JWT.create()
        .withKeyId("test-key").withIssuer(issuer).withAudience(audience).withSubject(subject)
        .withClaim("realm_access", mapOf("roles" to listOf("default-roles-test")))
        .withClaim("email", email).withClaim("email_verified", true)
        .withExpiresAt(Date(System.currentTimeMillis() + 600_000))
        .sign(Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey))

    private fun call(method: String, path: String, bearer: String, body: String? = null, expected: Int = 200): JsonElement? {
        val request = HttpRequest.newBuilder(URI.create(base + path)).header("Authorization", "Bearer $bearer")
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .apply { if (body != null) header("Content-Type", "application/json") }.build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expected, response.statusCode(), "$method $path → ${response.body().take(300)}")
        return response.body().takeIf { it.isNotBlank() }?.let(Json::parseToJsonElement)
    }

    private val JsonElement?.obj get() = this!!.jsonObject
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.names() = getValue("matches").jsonArray.map { it.jsonObject.str("fullName") }

    @Test fun matchingEndpointsEndToEnd() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        val publicKey = keys.public as RSAPublicKey
        val b64 = Base64.getUrlEncoder().withoutPadding()
        fun unsigned(bytes: ByteArray) = if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        val jwks = """{"keys":[{"kty":"RSA","kid":"test-key","use":"sig","alg":"RS256","n":"${b64.encodeToString(unsigned(publicKey.modulus.toByteArray()))}","e":"${b64.encodeToString(unsigned(publicKey.publicExponent.toByteArray()))}"}]}"""
        val jwksServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/certs") { exchange ->
                val bytes = jwks.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        issuer = "http://127.0.0.1:${jwksServer.address.port}/realms/test"
        val db = Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career")
        val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            careerModule(db, issuer, audience, "http://127.0.0.1:${jwksServer.address.port}/certs")
        }.start(wait = false)
        val subjects = List(4) { UUID.randomUUID().toString() }
        try {
            base = "http://127.0.0.1:" + runBlocking { server.engine.resolvedConnectors().first().port }
            val ivan = token(subjects[0], "candidate", "ivan.petrov@demo.fsp.local")
            val other = token(subjects[1], "candidate", "other.person@example.com")
            val employer = token(subjects[2], "employer", "hr@example.com")
            for ((bearer, role) in listOf(ivan to "candidate", other to "candidate", employer to "employer"))
                call("POST", "/api/me/role", bearer, """{"role":"$role"}""")

            fun profile(bearer: String, name: String, stack: String, experience: String) {
                val current = call("GET", "/api/candidates/me", bearer).obj
                val body = JsonObject(current + mapOf(
                    "fullName" to Json.parseToJsonElement("\"$name\""), "email" to Json.parseToJsonElement("\"\""),
                    "phone" to Json.parseToJsonElement("\"+70000000000\""), "specialization" to Json.parseToJsonElement("\"backend\""),
                    "industry" to Json.parseToJsonElement("\"финтех\""), "processingConsent" to Json.parseToJsonElement("true"),
                    "consentPublication" to Json.parseToJsonElement("true"), "techStack" to Json.parseToJsonElement(stack),
                    "experience" to Json.parseToJsonElement("\"$experience\""),
                ))
                call("PUT", "/api/candidates/me", bearer, body.toString())
            }
            profile(ivan, "Иван Петров", """["Kotlin","PostgreSQL","Kafka"]""", "Платёжные микросервисы на Kotlin, PostgreSQL и Kafka")
            profile(other, "Пётр Сидоров", """["Kotlin","PostgreSQL"]""", "Сервисы на Kotlin и PostgreSQL")
            runBlocking {
                db.tx {
                    subjects.take(2).forEach { subject ->
                        val id = Accounts.selectAll().where { Accounts.subject eq subject }.single()[Accounts.id]
                        AssessmentAttempts.insert {
                            it[AssessmentAttempts.id] = UUID.randomUUID()
                            it[candidateId] = id
                            it[specialization] = "backend"
                            it[questionIds] = listOf(1L, 2L, 3L, 4L, 5L)
                            it[submittedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                            it[score] = 70
                            it[grade] = "middle"
                        }
                    }
                }
            }

            // FSP link: wrong format 400, someone else's ID 404 (no enumeration), own ID imports results.
            call("POST", "/api/candidates/me/fsp", ivan, """{"fspId":"bad"}""", 400)
            call("POST", "/api/candidates/me/fsp", other, """{"fspId":"FSP-100001"}""", 404)
            val noHistory = call("GET", "/api/candidates/me/fsp", other).obj
            assertFalse(noHistory.getValue("linked").jsonPrimitive.boolean)
            assertTrue("без истории ФСП" in noHistory.str("message"))
            val linked = call("POST", "/api/candidates/me/fsp", ivan, """{"fspId":"fsp-100001"}""").obj
            assertEquals("FSP-100001", linked.str("fspId"))
            assertEquals(4, linked.getValue("achievements").jsonArray.size)
            call("POST", "/api/candidates/me/fsp", other, """{"fspId":"FSP-100001"}""", 404)
            call("GET", "/api/candidates/me/fsp", employer, expected = 403)

            // Employer profile and need.
            call("PUT", "/api/employers/company", employer, """{"name":"Тест Банк","industry":"финтех","contactTelegram":"@test_bank_hr","contactEmail":"bad"}""", 400)
            val company = call("PUT", "/api/employers/company", employer, """{"name":"Тест Банк","industry":"финтех","contactTelegram":"t.me/test_bank_hr"}""").obj
            assertEquals("@test_bank_hr", company.str("contactTelegram"))
            call("PUT", "/api/employers/needs", employer, """{"specialization":"backend","industry":"финтех","minGrade":"middle","description":"Платёжные сервисы на Kotlin","techStack":["Kotlin","Kafka","kotlin"]}""")
            assertEquals(listOf("Kotlin", "Kafka"), call("GET", "/api/employers/needs", employer).obj.getValue("techStack").jsonArray.map { it.jsonPrimitive.content })

            // Need-based selection with explanations and recommended categories; never contacts.
            val selection = call("GET", "/api/matches", employer).obj
            assertEquals("need", selection.str("mode"))
            val mine = selection.getValue("matches").jsonArray.map { it.jsonObject }.filter { it.str("fullName") in setOf("Иван Петров", "Пётр Сидоров") }
            assertEquals(listOf("Иван Петров", "Пётр Сидоров"), mine.map { it.str("fullName") })
            assertTrue(mine.first().getValue("factors").jsonArray.any { it.jsonObject.str("code") == "fsp" && it.jsonObject.str("text").startsWith("Достижения ФСП") })
            assertTrue(mine.last().getValue("flags").jsonArray.any { it.jsonPrimitive.content == "Нет истории ФСП" })
            assertTrue(mine.all { "email" !in it && "phone" !in it && "contactEmail" !in it })
            assertTrue(selection.getValue("categories").jsonArray.any { it.jsonObject.str("key") == "backend:middle" && it.jsonObject.getValue("recommended").jsonPrimitive.boolean })
            assertTrue(selection.getValue("formula").jsonArray.isNotEmpty())
            assertEquals(listOf("Иван Петров"), call("GET", "/api/matches?verifiedAchievements=true", employer).obj.names().filter { it in setOf("Иван Петров", "Пётр Сидоров") })
            assertTrue("Пётр Сидоров" !in call("GET", "/api/matches?techStack=kafka", employer).obj.names())
            call("GET", "/api/matches?grade=lead", employer, expected = 400)
            call("GET", "/api/matches?limit=0", employer, expected = 400)
            call("GET", "/api/matches", ivan, expected = 403)

            // Candidate bank + card.
            val bank = call("GET", "/api/bank/candidates?category=backend:middle&q=Kafka", employer).obj
            assertEquals("bank", bank.str("mode"))
            assertTrue("Иван Петров" in bank.names())
            call("GET", "/api/bank/candidates?category=backend", employer, expected = 400)
            val ivanId = mine.first().str("candidateId")
            val card = call("GET", "/api/bank/candidates/$ivanId", employer).obj
            assertFalse(card.getValue("contactsDisclosed").jsonPrimitive.boolean)
            assertNull(card["contactEmail"]?.takeUnless { it.toString() == "null" })
            assertEquals(4, card.getValue("achievements").jsonArray.size)
            call("GET", "/api/bank/candidates/${UUID.randomUUID()}", employer, expected = 404)
            call("GET", "/api/bank/candidates/not-a-uuid", employer, expected = 400)

            // Privacy: hide name and achievements → anonymous, no FSP points, card hides the section.
            call("PUT", "/api/candidates/me/privacy", ivan, """{"fullName":false,"techStack":true,"experience":true,"cvHistory":true,"softSkills":true,"fspAchievements":false}""")
            val hiddenCard = call("GET", "/api/bank/candidates/$ivanId", employer).obj
            assertTrue(hiddenCard.str("fullName").startsWith("Кандидат №"))
            assertTrue(hiddenCard["achievements"].toString() == "null")
            assertTrue(hiddenCard.getValue("hiddenSections").jsonArray.map { it.jsonPrimitive.content }.containsAll(listOf("ФИО", "Достижения ФСП")))
            assertTrue(call("GET", "/api/matches?verifiedAchievements=true", employer).obj.names().none { it.startsWith("Кандидат №" + ivanId.take(6).uppercase()) })

            // Invitation carries company name + contact method; contacts open only after acceptance.
            val invitation = call("POST", "/api/invitations", employer, """{"candidateId":"$ivanId","description":"Оффер","salaryMinRub":200000,"salaryMaxRub":250000}""", 201).obj
            assertEquals("Telegram: @test_bank_hr", invitation.str("contactMethod"))
            val inbox = call("GET", "/api/invitations", ivan).obj.getValue("invitations").jsonArray.single().jsonObject
            assertEquals("Тест Банк", inbox.str("companyName"))
            assertEquals("Telegram: @test_bank_hr", inbox.str("contactMethod"))
            assertEquals("sent", call("GET", "/api/bank/candidates/$ivanId", employer).obj.getValue("invitations").jsonArray.single().jsonObject.str("status"))
            call("POST", "/api/invitations/${invitation.str("id")}/accept", ivan)
            val opened = call("GET", "/api/bank/candidates/$ivanId", employer).obj
            assertTrue(opened.getValue("contactsDisclosed").jsonPrimitive.boolean)
            assertEquals("ivan.petrov@demo.fsp.local", opened.str("contactEmail"))
            assertEquals("Иван Петров", opened.str("fullName"))

            // Unlink removes imported results; consent revocation clears the binding.
            assertEquals(0, call("DELETE", "/api/candidates/me/fsp", ivan).obj.getValue("totalPoints").jsonPrimitive.int)
            call("POST", "/api/candidates/me/fsp", ivan, """{"fspId":"FSP-100001"}""")
            call("DELETE", "/api/candidates/me/consent", ivan)
            assertFalse(call("GET", "/api/candidates/me/fsp", ivan).obj.getValue("linked").jsonPrimitive.boolean)
            assertTrue(call("GET", "/api/candidates/me/privacy", ivan).obj.getValue("fullName").jsonPrimitive.boolean)
            assertTrue((call("GET", "/api/bank/candidates", employer).obj.getValue("matches") as JsonArray).none { it.jsonObject.str("candidateId") == ivanId })
        } finally {
            server.stop(100, 1000)
            jwksServer.stop(0)
            runBlocking {
                db.tx {
                    val ids = Accounts.selectAll().where { Accounts.subject inList subjects }.map { it[Accounts.id] }
                    ids.forEach { id ->
                        InvitationsTable.deleteWhere { (InvitationsTable.candidateId eq id) }
                        InvitationsTable.deleteWhere { (InvitationsTable.employerId eq id) }
                        AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId eq id }
                        Accounts.deleteWhere { Accounts.id eq id }
                    }
                }
            }
            db.close()
        }
    }
}
