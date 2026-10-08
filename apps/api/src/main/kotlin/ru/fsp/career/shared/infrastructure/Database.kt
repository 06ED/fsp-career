package ru.fsp.career.shared.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database as ExposedDatabase
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction

class Database(url: String, user: String, password: String) : AutoCloseable {
    private val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        username = user
        this.password = password
        maximumPoolSize = 8
        minimumIdle = 1
    })

    private val exposed = try {
        Flyway.configure().dataSource(pool).load().migrate()
        ExposedDatabase.connect(pool)
    } catch (error: Throwable) {
        pool.close()
        throw error
    }

    suspend fun <T> tx(block: Transaction.() -> T): T = withContext(Dispatchers.IO) {
        transaction(exposed) { block() }
    }

    override fun close() = pool.close()
}
