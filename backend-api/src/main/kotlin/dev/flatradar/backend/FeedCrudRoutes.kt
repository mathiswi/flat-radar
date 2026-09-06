package dev.flatradar.backend

import dev.flatradar.shared.FeedConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sources the scraper knows how to parse. A feed for any other source would be silently skipped. */
private val KNOWN_SOURCES = setOf("kleinanzeigen", "immoscout24")

/**
 * Feed CRUD - the DB-backed replacement for the VPS-local `feeds.json`. The
 * dashboard editor drives these; the scraper reads `GET /feeds`. Sits alongside
 * the delisting-reconcile `POST /feeds/{feedId}/seen` in [feedRoutes] (same path
 * prefix, different leaves).
 *
 * Writes are validated so the junk-listings incident can't recur: a Kleinanzeigen
 * URL that isn't category-locked (`c203`) pulls every category and is rejected.
 */
fun Route.feedCrudRoutes(repository: FeedRepository) {
    route("$API_V1/feeds") {
        get {
            val feeds = withContext(Dispatchers.IO) { repository.all() }
            call.respond(feeds)
        }

        post {
            val feed = call.receive<FeedConfig>()
            validate(feed)?.let { return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it)) }
            val created = withContext(Dispatchers.IO) { repository.upsert(feed) }
            call.respond(
                if (created) HttpStatusCode.Created else HttpStatusCode.OK,
                StatusResponse(if (created) "created" else "updated"),
            )
        }

        route("{feedId}") {
            put {
                val feedId = call.parameters["feedId"]!!
                // The path is authoritative for the id, so a body id is ignored (or absent).
                val feed = call.receive<FeedConfig>().copy(id = feedId)
                validate(feed)?.let { return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it)) }
                withContext(Dispatchers.IO) { repository.upsert(feed) }
                call.respond(HttpStatusCode.OK, StatusResponse("updated"))
            }

            delete {
                val feedId = call.parameters["feedId"]!!
                val deleted = withContext(Dispatchers.IO) { repository.delete(feedId) }
                if (deleted) {
                    call.respond(HttpStatusCode.OK, StatusResponse("deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("no feed with id '$feedId'"))
                }
            }
        }
    }
}

/** Returns an error message if [feed] is invalid, or null if it's fine to persist. */
internal fun validate(feed: FeedConfig): String? {
    if (feed.id.isBlank()) return "id must not be blank"
    if (feed.displayName.isBlank()) return "displayName must not be blank"
    if (feed.district.isBlank()) return "district must not be blank"
    if (!feed.url.startsWith("http://") && !feed.url.startsWith("https://")) {
        return "url must be an http(s) URL"
    }
    if (feed.source !in KNOWN_SOURCES) {
        return "unknown source '${feed.source}'; must be one of ${KNOWN_SOURCES.joinToString()}"
    }
    // The junk-listings guard: a loose Kleinanzeigen search (no c203 category lock)
    // returns every category, not just apartments.
    if (feed.source == "kleinanzeigen" && !feed.url.contains("c203")) {
        return "Kleinanzeigen url must be category-locked (contain 'c203'); a loose search pulls cross-category junk"
    }
    return null
}
