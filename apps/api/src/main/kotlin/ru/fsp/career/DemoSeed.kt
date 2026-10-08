package ru.fsp.career

import kotlinx.coroutines.runBlocking
import ru.fsp.career.shared.infrastructure.Database
import ru.fsp.career.shared.infrastructure.DemoSeed

/** Keeps the deployment CLI entry point stable while the fixture adapter lives in infrastructure. */
fun main() = runBlocking {
    fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    Database(
        env("DATABASE_URL")!!,
        env("DATABASE_USER")!!,
        env("DATABASE_PASSWORD")!!
    ).use { db ->
        DemoSeed(db).run(
            env("DEMO_EMPLOYER_SUBJECT"),
            env("DEMO_ASSESSED_SUBJECT")
        )
    }
}
