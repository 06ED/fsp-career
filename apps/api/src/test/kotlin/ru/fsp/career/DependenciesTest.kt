package ru.fsp.career

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import ru.fsp.career.identity.http.authHttpClient
import ru.fsp.career.identity.infrastructure.AuthConfig
import ru.fsp.career.shared.infrastructure.Database
import kotlin.test.*

class DependenciesTest {
    @Test fun factoriesAreLazySingletonsAndResourcesCloseWithApplication() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        val issuer = "http://issuer.invalid"
        lateinit var db: Database
        lateinit var http: HttpClient
        var databases = 0
        var clients = 0
        var configs = 0
        testApplication {
            application {
                dependencies {
                    provide<Database> {
                        databases++
                        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").also { db = it }
                    }
                    provide<HttpClient> { clients++; authHttpClient().also { http = it } }
                    provide<AuthConfig> { configs++; AuthConfig.fromEnv(issuer) }
                }
                assertEquals(0, databases + clients + configs)
                careerModule(issuer, "career")
                val first: Database by dependencies
                val second: Database by dependencies
                assertSame(first, second)
            }
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
            assertEquals(1, databases)
            assertEquals(1, clients)
            assertEquals(1, configs)
        }
        runBlocking {
            withTimeout(5_000) { http.coroutineContext[Job]!!.join() }
            assertFails { db.tx { exec("SELECT 1") } }
        }
    }
}
