package ru.fsp.career

import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DatabaseIntegrationTest {
    @Test fun migrationsAndDatabaseConstraints() {
        val url = System.getenv("TEST_DATABASE_URL")
        assumeTrue(!url.isNullOrBlank(), "TEST_DATABASE_URL not configured")
        Database(url!!, System.getenv("TEST_DATABASE_USER") ?: "career", System.getenv("TEST_DATABASE_PASSWORD") ?: "career").use { db ->
            kotlinx.coroutines.runBlocking {
                db.tx { connection ->
                    assertEquals(25, connection.rows("SELECT count(*) FROM questions") { it.getInt(1) }.single())
                }
                assertFails {
                    db.tx { connection -> connection.exec("INSERT INTO accounts(subject,role) VALUES ('invalid-role-test','admin')") }
                }
            }
        }
    }
}
