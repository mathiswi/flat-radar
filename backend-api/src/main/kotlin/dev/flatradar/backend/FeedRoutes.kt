package dev.flatradar.backend

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delisting reconcile endpoint. The scraper POSTs the full set of ad IDs it saw
 * for one feed on a successful run; [ListingRepository.reconcileSeen] bumps those
 * as still-alive and advances the miss counter for that feed's absent listings,
 * marking them delisted once [threshold] consecutive misses is reached.
 */
fun Route.feedRoutes(repository: ListingRepository, threshold: Int) {
    route("$API_V1/feeds/{feedId}/seen") {
        post {
            val feedId = call.parameters["feedId"]!!
            val ids = call.receive<List<String>>()
            val delisted = withContext(Dispatchers.IO) {
                repository.reconcileSeen(feedId, ids, threshold)
            }
            call.respond(SeenResponse(delisted))
        }
    }
}
