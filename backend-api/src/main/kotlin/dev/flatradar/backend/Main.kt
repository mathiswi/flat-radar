package dev.flatradar.backend

import dev.flatradar.backend.notify.DiscordNotifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
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
import kotlinx.coroutines.launch
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

    install(IpWhitelist) {
        excludePaths = listOf("/api/v1/health", "/api/v1/ready")
    }

    install(ContentNegotiation) { json(json) }

    startOutboxWorker(repository)

    routing {
        healthRoutes(dataSource)
        listingRoutes(repository)
    }
}

/**
 * Launches [OutboxWorker] in the application's own coroutine scope (cancelled
 * automatically on shutdown - see [Application]'s KDoc) when `DISCORD_WEBHOOK_URL`
 * is configured. Mirrors the scraper's optional `GEMINI_API_KEY` pattern: no
 * webhook configured means no worker, logged once, not an error.
 */
private fun Application.startOutboxWorker(repository: ListingRepository) {
    val webhookUrl = System.getenv("DISCORD_WEBHOOK_URL")?.takeIf { it.isNotBlank() }
    if (webhookUrl == null) {
        log.info("[outbox] DISCORD_WEBHOOK_URL not set; notifications disabled")
        return
    }

    val httpClient = HttpClient(Java)
    monitor.subscribe(ApplicationStopping) { httpClient.close() }

    val worker = OutboxWorker(repository, DiscordNotifier(httpClient, webhookUrl))
    launch { worker.run() }
}
