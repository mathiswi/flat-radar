package dev.flatradar.backend

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import javax.sql.DataSource

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

fun Application.module(
    dataSource: DataSource = createDataSource(),
    changelogPath: String = "db/changelog/db.changelog-master.yaml",
) {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val repository = ListingRepository(dataSource)

    runMigrations(dataSource, changelogPath)

    install(StatusPages) {
        // Malformed/missing JSON bodies surface as BadRequestException, with the
        // underlying serialization failure attached as `cause` (see Ktor's
        // ContentNegotiation RequestConverter).
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "malformed request"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal server error"))
        }
    }

    install(CallLogging) { level = Level.INFO }

    install(ContentNegotiation) { json(json) }

    routing {
        healthRoutes(dataSource)
        listingRoutes(repository)
    }
}
