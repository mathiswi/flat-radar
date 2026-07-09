package dev.flatradar.backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import javax.sql.DataSource

val DataSourceKey = AttributeKey<DataSource>("DataSource")

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val dataSource = createDataSource()
    attributes.put(DataSourceKey, dataSource)

    val repository = ListingRepository(dataSource)

    runMigrations(dataSource)

    install(ContentNegotiation) { json(json) }

    routing {
        healthRoutes(dataSource)
        listingRoutes(repository)
    }
}