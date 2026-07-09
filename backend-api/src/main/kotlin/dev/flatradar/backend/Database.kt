package dev.flatradar.backend

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * Creates a HikariCP connection pool configured from environment variables.
 *
 * Env vars:
 *   - JDBC_URL      e.g. jdbc:postgresql://postgres:5432/flatradar
 *   - JDBC_USER     defaults to "postgres"
 *   - JDBC_PASSWORD required
 *
 * The returned [DataSource] should be created once at startup and reused for
 * migrations and database queries (e.g. Exposed).
 */
fun createDataSource(): DataSource {
    val config = HikariConfig().apply {
        jdbcUrl = System.getenv("JDBC_URL")
            ?: error("JDBC_URL must be set (e.g. jdbc:postgresql://postgres:5432/flatradar)")
        username = System.getenv("JDBC_USER") ?: "postgres"
        password = System.getenv("JDBC_PASSWORD") ?: error("JDBC_PASSWORD must be set")
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 300_000
        connectionTimeout = 30_000
    }
    return HikariDataSource(config)
}
