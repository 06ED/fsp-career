package ru.fsp.career

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

class Database(url: String, user: String, password: String) : AutoCloseable {
    private val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url; username = user; this.password = password
        maximumPoolSize = 8; minimumIdle = 1
    })
    init { Flyway.configure().dataSource(pool).load().migrate() }
    suspend fun <T> tx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        pool.connection.use { connection ->
            connection.autoCommit = false
            try { block(connection).also { connection.commit() } }
            catch (error: Exception) { connection.rollback(); throw error }
        }
    }
    override fun close() = pool.close()
}

fun Connection.query(sql: String, vararg args: Any?, map: (ResultSet) -> Unit) {
    prepareStatement(sql).use { statement -> statement.bind(args); statement.executeQuery().use { result -> while(result.next()) map(result) } }
}
fun <T> Connection.rows(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> {
    val values = mutableListOf<T>(); query(sql, *args) { values.add(map(it)) }; return values
}
fun Connection.exec(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { it.bind(args); it.executeUpdate() }
private fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { index, value ->
        when(value) {
            is java.util.UUID -> setObject(index + 1, value)
            is LongArray -> setArray(index + 1, connection.createArrayOf("bigint", value.toTypedArray()))
            else -> setObject(index + 1, value)
        }
    }
}
