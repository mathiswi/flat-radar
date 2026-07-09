package dev.flatradar.backend

import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val logger = LoggerFactory.getLogger("Migrations")

/**
 * Runs every pending Liquibase changeset in [changelogPath] using the provided
 * [DataSource]. Defaults to the real `db/changelog/db.changelog-master.yaml`;
 * tests may point this at an H2-compatible changelog instead (see
 * `ListingRoutesTest`).
 *
 * Call once at application startup, before the server starts accepting
 * requests, so the schema is guaranteed to exist when routes fire.
 */
fun runMigrations(dataSource: DataSource, changelogPath: String = "db/changelog/db.changelog-master.yaml") {
    dataSource.connection.use { conn ->
        val database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(JdbcConnection(conn))
        Liquibase(
            changelogPath,
            ClassLoaderResourceAccessor(),
            database,
        ).use { liquibase ->
            liquibase.update("")
            logger.info("[migrations] schema up to date")
        }
    }
}
