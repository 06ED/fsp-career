package ru.fsp.career

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.sun.net.httpserver.HttpServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDate
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
 * End-to-end HTTP test of vacancies, applications and short tasks against a real PostgreSQL
 * (TEST_DATABASE_URL, all Flyway migrations) and the real Ktor module. Keycloak is replaced by a
 * local JWKS endpoint and RS256 tokens signed with a throwaway key, so issuer/audience/signature
 * verification and the role checks run unchanged. Skipped when TEST_DATABASE_URL is not set.
 */
class VacancyHttpTest {
    private val run = UUID.randomUUID().toString().take(8)
    private val http = HttpClient.newHttpClient()
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val audience = "fsp-api"
    private lateinit var issuer: String
    private lateinit var base: String

    private fun b64(value: BigInteger): String {
        val bytes = value.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** A plain Keycloak-like token: the application role is bound in the app via POST /api/me/role, not carried here. */
    private fun token(subject: String, @Suppress("UNUSED_PARAMETER") role: String, email: String): String = JWT.create()
        .withKeyId("vacancy-test").withIssuer(issuer).withAudience(audience).withSubject(subject)
        .withClaim("realm_access", mapOf("roles" to listOf("default-roles-test")))
        .withClaim("email", email).withClaim("email_verified", true)
        .withIssuedAt(Date()).withExpiresAt(Date(System.currentTimeMillis() + 600_000))
        .sign(Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey))

    private fun call(expected: Int, method: String, path: String, bearer: String?, body: String? = null): JsonElement {
        val request = HttpRequest.newBuilder(URI(base + path))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .apply {
                if (body != null) header("Content-Type", "application/json")
                if (bearer != null) header("Authorization", "Bearer $bearer")
            }.build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(expected, response.statusCode(), "$method $path -> ${response.body()}")
        return if (response.body().isBlank()) JsonNull else Json.parseToJsonElement(response.body())
    }

    private operator fun JsonElement.get(key: String): JsonElement = jsonObject[key] ?: JsonNull
    private val JsonElement.text: String get() = jsonPrimitive.content
    private val JsonElement.items: List<JsonElement> get() = jsonArray

    private fun vacancyJson(title: String, specialization: String = "backend", grade: String = "middle", min: Int = 200000, max: Int = 280000) =
        """{"title":"$title","description":"Развиваем платёжный шлюз на Kotlin","specialization":"$specialization","grade":"$grade",
           "techStack":["Kotlin","PostgreSQL","Kafka"],"salaryMinRub":$min,"salaryMaxRub":$max,"workFormat":"hybrid","city":"Москва"}"""

    private fun profileJson(name: String, phone: String, desiredGrade: String = "middle") =
        """{"fullName":"$name","email":"","phone":"$phone","processingConsent":true,"consentPublication":true,
           "specialization":"backend","industry":"fintech","desiredGrade":"$desiredGrade","techStack":["Kotlin","SQL"],"experience":"Синтетический профиль"}"""

    @Test fun vacanciesApplicationsAndShortTasksOverHttp() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        val pub = keys.public as RSAPublicKey
        val jwks = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        jwks.createContext("/certs") { exchange ->
            val body = """{"keys":[{"kty":"RSA","kid":"vacancy-test","use":"sig","alg":"RS256","n":"${b64(pub.modulus)}","e":"${b64(pub.publicExponent)}"}]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        jwks.start()
        issuer = "http://127.0.0.1:${jwks.address.port}/realms/test"
        val subjects = List(6) { "vacancy-test-$run-$it" }
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            val port = ServerSocket(0).use { it.localPort }
            base = "http://127.0.0.1:$port"
            val server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
                careerModule(db, issuer, audience, "http://127.0.0.1:${jwks.address.port}/certs")
            }.start(wait = false)
            try {
                scenario(db, subjects)
            } finally {
                server.stop(100, 1000)
                jwks.stop(0)
                kotlinx.coroutines.runBlocking {
                    db.tx {
                        val ids = Accounts.selectAll().where { Accounts.subject inList subjects }.map { it[Accounts.id] }
                        AssessmentAttempts.deleteWhere { AssessmentAttempts.candidateId inList ids }
                        InvitationsTable.deleteWhere { (InvitationsTable.candidateId inList ids) or (InvitationsTable.employerId inList ids) }
                        // Cascades remove profiles, companies, vacancies, applications, tasks, answers and offers.
                        Accounts.deleteWhere { Accounts.id inList ids }
                    }
                }
            }
        }
    }

    private fun scenario(db: Database, subjects: List<String>) = kotlinx.coroutines.runBlocking {
        val employer = token(subjects[0], "employer", "hr-$run@example.org")
        val otherEmployer = token(subjects[1], "employer", "other-$run@example.org")
        val annaEmail = "anna-$run@example.org"
        val anna = token(subjects[2], "candidate", annaEmail) // assessed backend/middle
        val boris = token(subjects[3], "candidate", "boris-$run@example.org") // profile, no assessment
        val vera = token(subjects[4], "candidate", "vera-$run@example.org") // no consent

        // --- One-time in-app role choice ---
        val unbound = token(subjects[5], "candidate", "nobody-$run@example.org")
        assertEquals(JsonNull, call(200, "GET", "/api/me", unbound)["role"])
        call(403, "GET", "/api/vacancies", unbound)
        call(400, "POST", "/api/me/role", unbound, """{"role":"admin"}""")
        for ((bearer, role) in listOf(employer to "employer", otherEmployer to "employer",
            anna to "candidate", boris to "candidate", vera to "candidate")) {
            call(200, "POST", "/api/me/role", bearer, """{"role":"$role"}""")
        }
        call(200, "POST", "/api/me/role", anna, """{"role":"candidate"}""") // idempotent
        call(409, "POST", "/api/me/role", anna, """{"role":"employer"}""")
        assertEquals("employer", call(200, "GET", "/api/me", employer)["role"].text)

        // --- Auth & role boundaries ---
        call(401, "GET", "/api/vacancies", null)
        call(403, "GET", "/api/vacancies", employer)
        call(403, "GET", "/api/employers/vacancies", anna)

        // --- Employer publishes a vacancy (company name and RUB range required) ---
        call(400, "POST", "/api/employers/vacancies", employer, vacancyJson("Без компании"))
        call(200, "PUT", "/api/employers/company", employer, """{"name":"Тест-Финтех $run","description":"Платежи","website":""}""")
        call(200, "PUT", "/api/employers/company", otherEmployer, """{"name":"Чужая компания $run"}""")
        call(400, "POST", "/api/employers/vacancies", employer, vacancyJson("Перепутан диапазон", min = 300000, max = 200000))
        call(400, "POST", "/api/employers/vacancies", employer,
            """{"title":"Без зарплаты","description":"d","specialization":"backend","grade":"middle","workFormat":"remote"}""")
        val v1 = call(201, "POST", "/api/employers/vacancies", employer, vacancyJson("Backend-разработчик $run"))
        assertEquals("open", v1["status"].text)
        assertEquals("0", v1["applications"]["total"].text)
        val v1Id = v1["id"].text
        val v2Id = call(201, "POST", "/api/employers/vacancies", employer, vacancyJson("Senior backend $run", grade = "senior"))["id"].text
        call(201, "POST", "/api/employers/vacancies", employer, vacancyJson("Frontend $run", specialization = "frontend"))
        call(404, "PUT", "/api/employers/vacancies/$v1Id", otherEmployer, vacancyJson("Захват"))
        val edited = call(200, "PUT", "/api/employers/vacancies/$v1Id", employer, vacancyJson("Backend-разработчик $run", min = 220000, max = 300000))
        assertEquals("220000", edited["salaryMinRub"].text)
        call(400, "PUT", "/api/employers/vacancies/not-a-uuid", employer, vacancyJson("x"))

        // --- Candidates ---
        call(200, "PUT", "/api/candidates/me", anna, profileJson("Анна Тестова", "+79990000001"))
        call(200, "PUT", "/api/candidates/me", boris, profileJson("Борис Тестов", "+79990000002", "junior"))
        call(200, "GET", "/api/candidates/me", vera)
        val annaId = db.tx { account(subjects[2], "candidate") }
        db.tx { // Fixture: a confirmed backend/middle category (assessment flow is covered elsewhere).
            AssessmentAttempts.insert {
                it[id] = UUID.randomUUID(); it[candidateId] = annaId; it[specialization] = "backend"
                it[questionIds] = listOf(1L, 2L, 3L, 4L, 5L); it[startedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[submittedAt] = OffsetDateTime.now(ZoneOffset.UTC); it[score] = 70; it[grade] = "middle"
            }
        }

        // --- Browsing: conditions visible up front, filters, fit explanation ---
        val mine = { list: JsonElement -> list["vacancies"].items.filter { it["title"].text.endsWith(run) } }
        val listed = mine(call(200, "GET", "/api/vacancies", anna))
        assertEquals(3, listed.size)
        val card = listed.first()
        assertEquals(v1Id, card["id"].text, "matching category should be recommended first")
        assertEquals("true", card["categoryMatch"].text)
        assertEquals("Тест-Финтех $run", card["companyName"].text)
        assertEquals("300000", card["salaryMaxRub"].text)
        assertTrue(card["fit"].text.contains("соответствует требованию Middle"), card["fit"].text)
        assertEquals(listOf("Kotlin"), card["stackOverlap"].items.map { it.text })
        assertEquals(2, mine(call(200, "GET", "/api/vacancies?specialization=backend", anna)).size)
        assertEquals(1, mine(call(200, "GET", "/api/vacancies?specialization=backend&grade=senior", anna)).size)
        assertEquals(0, mine(call(200, "GET", "/api/vacancies?salaryFrom=300001", anna)).size)
        assertEquals(3, mine(call(200, "GET", "/api/vacancies?salaryFrom=280000&techStack=kotlin,KAFKA", anna)).size)
        assertEquals(3, mine(call(200, "GET", "/api/vacancies?q=" + "тест-финтех".let { java.net.URLEncoder.encode(it, "UTF-8") }, anna)).size)
        assertEquals(0, mine(call(200, "GET", "/api/vacancies?workFormat=remote", anna)).size)
        call(400, "GET", "/api/vacancies?workFormat=moon", anna)
        call(400, "GET", "/api/vacancies?salaryFrom=-5", anna)

        // --- Applying: preconditions, duplicates, contact disclosure ---
        call(403, "POST", "/api/vacancies/$v1Id/applications", vera, """{"coverNote":""}""")
        call(409, "POST", "/api/vacancies/$v1Id/applications", boris, """{"coverNote":"Без оценки"}""")
        call(404, "POST", "/api/vacancies/${UUID.randomUUID()}/applications", anna, "{}")
        val application = call(201, "POST", "/api/vacancies/$v1Id/applications", anna, """{"coverNote":"Делала платёжный шлюз"}""")
        assertEquals("sent", application["status"].text)
        assertEquals("Тест-Финтех $run", application["companyName"].text)
        val applicationId = application["id"].text
        call(409, "POST", "/api/vacancies/$v1Id/applications", anna, "{}")
        assertEquals("sent", mine(call(200, "GET", "/api/vacancies", anna)).first { it["id"].text == v1Id }["myApplicationStatus"].text)

        call(404, "GET", "/api/employers/vacancies/$v1Id/applications", otherEmployer)
        val incoming = call(200, "GET", "/api/employers/vacancies/$v1Id/applications", employer)
        assertEquals("1", incoming["vacancy"]["applications"]["sent"].text)
        val applicant = incoming["applications"].items.single()
        assertEquals("Анна Тестова", applicant["fullName"].text)
        assertEquals(annaEmail, applicant["contactEmail"].text, "applying discloses contacts to this employer")
        assertEquals("+79990000001", applicant["contactPhone"].text)
        assertEquals("middle", applicant["assessedGrade"].text)
        assertEquals("70", applicant["score"].text)
        assertEquals("true", applicant["categoryMatch"].text)
        // The invitation path still hides contacts: Anna applied, she was not invited/accepted.
        assertTrue(call(200, "GET", "/api/invitations", employer)["invitations"].items.isEmpty())

        // --- Status transitions (employer) and candidate view ---
        call(404, "PUT", "/api/employers/applications/$applicationId/status", otherEmployer, """{"status":"viewed"}""")
        call(400, "PUT", "/api/employers/applications/$applicationId/status", employer, """{"status":"sent"}""")
        assertEquals("viewed", call(200, "PUT", "/api/employers/applications/$applicationId/status", employer, """{"status":"viewed"}""")["status"].text)
        call(409, "PUT", "/api/employers/applications/$applicationId/status", employer, """{"status":"viewed"}""")
        call(200, "PUT", "/api/employers/applications/$applicationId/status", employer, """{"status":"accepted"}""")
        call(409, "PUT", "/api/employers/applications/$applicationId/status", employer, """{"status":"rejected"}""")
        val own = call(200, "GET", "/api/candidates/me/applications", anna)["applications"].items.single()
        assertEquals("accepted", own["status"].text)
        assertTrue(call(200, "GET", "/api/candidates/me/applications", boris)["applications"].items.isEmpty())
        call(409, "DELETE", "/api/candidates/me/applications/$applicationId", anna)
        call(404, "DELETE", "/api/candidates/me/applications/$applicationId", boris)

        // --- Withdrawal removes the application (and the contact disclosure) ---
        val second = call(201, "POST", "/api/vacancies/$v2Id/applications", anna, "{}")["id"].text
        call(204, "DELETE", "/api/candidates/me/applications/$second", anna)
        assertTrue(call(200, "GET", "/api/employers/vacancies/$v2Id/applications", employer)["applications"].items.isEmpty())

        // --- Close / reopen ---
        call(404, "POST", "/api/employers/vacancies/$v2Id/close", otherEmployer)
        assertEquals("closed", call(200, "POST", "/api/employers/vacancies/$v2Id/close", employer)["status"].text)
        call(409, "POST", "/api/employers/vacancies/$v2Id/close", employer)
        call(409, "POST", "/api/vacancies/$v2Id/applications", anna, "{}")
        assertFalse(mine(call(200, "GET", "/api/vacancies", anna)).any { it["id"].text == v2Id })
        call(200, "POST", "/api/employers/vacancies/$v2Id/reopen", employer)
        call(409, "POST", "/api/employers/vacancies/$v2Id/reopen", employer)
        val counts = call(200, "GET", "/api/employers/vacancies", employer)["vacancies"].items.first { it["id"].text == v1Id }["applications"]
        assertEquals("1", counts["total"].text)
        assertEquals("1", counts["accepted"].text)

        // --- Short tasks: weekly offer, answer, rating ---
        val taskJson = { title: String, specialization: String, grade: String? ->
            """{"title":"$title $run","body":"Как бы вы решили эту задачу?","specialization":"$specialization","grade":${grade?.let { "\"$it\"" } ?: "null"}}"""
        }
        call(403, "POST", "/api/employers/tasks", anna, taskJson("t", "backend", null))
        call(400, "POST", "/api/employers/tasks", employer, taskJson("t", "devops", null))
        val t1 = call(201, "POST", "/api/employers/tasks", employer, taskJson("Идемпотентность", "backend", null))["id"].text
        val t2 = call(201, "POST", "/api/employers/tasks", employer, taskJson("Шардирование", "backend", "senior"))["id"].text
        val t3 = call(201, "POST", "/api/employers/tasks", employer, taskJson("Вёрстка", "frontend", null))["id"].text
        call(403, "GET", "/api/candidates/me/tasks/current", vera)
        val offer = call(200, "GET", "/api/candidates/me/tasks/current", anna)
        assertEquals("offered", offer["state"].text)
        assertEquals(t1, offer["task"]["id"].text, "only the backend task without a senior-only grade is eligible")
        assertEquals("Тест-Финтех $run", offer["task"]["companyName"].text)
        assertEquals(t1, call(200, "GET", "/api/candidates/me/tasks/current", anna)["task"]["id"].text)
        assertEquals(t1, call(200, "GET", "/api/candidates/me/tasks/current", boris)["task"]["id"].text)
        // A task published mid-week does not reshuffle an existing offer.
        val t4 = call(201, "POST", "/api/employers/tasks", employer, taskJson("Ретраи", "backend", null))["id"].text
        assertEquals(t1, call(200, "GET", "/api/candidates/me/tasks/current", boris)["task"]["id"].text)

        call(409, "POST", "/api/candidates/me/task-submissions", anna, """{"taskId":"$t3","kind":"solution","answer":"не тот"}""")
        call(400, "POST", "/api/candidates/me/task-submissions", anna, """{"taskId":"$t1","kind":"essay","answer":"x"}""")
        val submission = call(201, "POST", "/api/candidates/me/task-submissions", anna,
            """{"taskId":"$t1","kind":"approach","answer":"Ключ идемпотентности + уникальный индекс"}""")
        assertNull(submission["rating"].takeIf { it != JsonNull })
        call(409, "POST", "/api/candidates/me/task-submissions", anna, """{"taskId":"$t1","kind":"solution","answer":"ещё раз"}""")
        val answered = call(200, "GET", "/api/candidates/me/tasks/current", anna)
        assertEquals("answered", answered["state"].text)
        assertEquals(submission["id"].text, answered["submission"]["id"].text)

        // Next week: answered and senior-only tasks are excluded, so the new task is offered.
        val nextWeek = LocalDate.now(ZoneOffset.UTC).plusWeeks(1)
        val (nextState, nextTask) = db.tx { currentShortTask(annaId, profile(annaId), nextWeek).let { it.state to it.task?.id } }
        assertEquals("offered" to t4, nextState to nextTask)
        assertEquals(t4, db.tx { currentShortTask(annaId, profile(annaId), nextWeek).task?.id }, "offer is persisted")

        // Deactivation re-picks a still-open offer; with no eligible task the slot is "none".
        call(200, "POST", "/api/employers/tasks/$t1/deactivate", employer)
        call(409, "POST", "/api/employers/tasks/$t1/deactivate", employer)
        call(404, "POST", "/api/employers/tasks/$t4/deactivate", otherEmployer)
        assertEquals(t4, call(200, "GET", "/api/candidates/me/tasks/current", boris)["task"]["id"].text)
        call(200, "POST", "/api/employers/tasks/$t4/deactivate", employer)
        assertEquals("none", call(200, "GET", "/api/candidates/me/tasks/current", boris)["state"].text)
        assertEquals("answered", call(200, "GET", "/api/candidates/me/tasks/current", anna)["state"].text)

        call(404, "GET", "/api/employers/tasks/$t1/submissions", otherEmployer)
        val answers = call(200, "GET", "/api/employers/tasks/$t1/submissions", employer)
        val answer = answers["submissions"].items.single()
        assertEquals("Анна Тестова", answer["candidateName"].text)
        assertEquals("approach", answer["kind"].text)
        assertFalse(answer.toString().contains(annaEmail), "task answers never disclose contacts")
        assertFalse(answer.jsonObject.keys.any { it.contains("contact", ignoreCase = true) })
        val submissionId = answer["id"].text
        call(400, "PUT", "/api/employers/task-submissions/$submissionId/rating", employer, """{"rating":6}""")
        call(404, "PUT", "/api/employers/task-submissions/$submissionId/rating", otherEmployer, """{"rating":5}""")
        assertEquals("5", call(200, "PUT", "/api/employers/task-submissions/$submissionId/rating", employer,
            """{"rating":5,"comment":"Чёткий план"}""")["rating"].text)
        val history = call(200, "GET", "/api/candidates/me/task-submissions", anna)["submissions"].items.single()
        assertEquals("5", history["rating"].text)
        assertEquals("Чёткий план", history["ratingComment"].text)
        val tasks = call(200, "GET", "/api/employers/tasks", employer)["tasks"].items
        assertEquals(4, tasks.size)
        assertEquals("1", tasks.first { it["id"].text == t1 }["submissions"].text)
        assertEquals("0", tasks.first { it["id"].text == t1 }["unrated"].text)
        assertTrue(tasks.none { it["id"].text == t2 && it["submissions"].text != "0" })

        // Fresh task activity is visible next to the application.
        val withSignal = call(200, "GET", "/api/employers/vacancies/$v1Id/applications", employer)["applications"].items.single()
        assertEquals("1", withSignal["taskSubmissions"].text)
        assertEquals("5.0", withSignal["taskAverageRating"].text)
        assertEquals(1, db.tx { lastTaskActivity(listOf(annaId)).size })

        // --- The database itself enforces the salary rule and rating range (defense in depth) ---
        val ownerId = db.tx { account(subjects[0], "employer") }
        for ((min, max) in listOf(0 to 100, 200 to 100)) {
            kotlin.test.assertFails("salary $min..$max must be rejected by CHECK") {
                db.tx {
                    Vacancies.insert {
                        it[id] = UUID.randomUUID(); it[Vacancies.employerId] = ownerId; it[title] = "x"; it[description] = "x"
                        it[specialization] = "backend"; it[grade] = "junior"; it[techStack] = emptyList()
                        it[salaryMinRub] = min; it[salaryMaxRub] = max; it[workFormat] = "remote"; it[city] = ""
                        it[status] = "open"; it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC); it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                    }
                }
            }
        }
        kotlin.test.assertFails("rating outside 1..5 must be rejected by CHECK") {
            db.tx {
                ShortTaskSubmissions.update({ ShortTaskSubmissions.id eq UUID.fromString(submissionId) }) {
                    it[rating] = 9; it[ratedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
        }

        // --- Consent revocation erases applications and task answers ---
        call(200, "DELETE", "/api/candidates/me/consent", anna)
        assertTrue(call(200, "GET", "/api/employers/vacancies/$v1Id/applications", employer)["applications"].items.isEmpty())
        assertTrue(call(200, "GET", "/api/employers/tasks/$t1/submissions", employer)["submissions"].items.isEmpty())
        assertTrue(db.tx { ShortTaskOffers.selectAll().where { ShortTaskOffers.candidateId eq annaId }.empty() })
    }
}
