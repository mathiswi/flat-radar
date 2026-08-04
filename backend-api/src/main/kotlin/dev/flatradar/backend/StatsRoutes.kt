package dev.flatradar.backend

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.statsRoutes(repository: ListingRepository) {
    route(API_V1) {
        get("/stats") {
            val maxAttempts = 5 // matches OutboxWorker default
            val stats = withContext(Dispatchers.IO) {
                StatsResponse(
                    totalListings = repository.countAll(),
                    pendingOutbox = repository.countPendingOutbox(maxAttempts),
                    deadLettered = repository.countDeadLetteredOutbox(maxAttempts),
                    lastScrape = repository.lastScrapeTimestamp()?.toInstant()?.toEpochMilli(),
                )
            }
            call.respond(stats)
        }
    }
}
