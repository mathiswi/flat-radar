package dev.flatradar.backend

import dev.flatradar.shared.FeedConfig
import dev.flatradar.shared.KnownSources
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
private val KNOWN_SOURCES = KnownSources.all.toSet()

/**
 * A Kleinanzeigen search URL is category-locked when a path segment is the
 * apartments-for-rent category code `c203` (optionally with a `l<locationId>`
 * suffix, e.g. `c203l26487`). Anchored to a `/` or the string bounds so an
 * incidental `c203` elsewhere in the URL (a district slug, a query value, a
 * fragment) doesn't count as category-locked.
 */
private val KLEINANZEIGEN_CATEGORY = Regex("""(?:^|/)c203(?:l\d+)?(?=[/?#]|$)""")

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
    // The valid source names, so the dashboard dropdown reads them from the one
    // shared list rather than hardcoding its own copy.
    get("$API_V1/sources") {
        call.respond(KnownSources.all)
    }

    route("$API_V1/feeds") {
        get {
            val feeds = withContext(Dispatchers.IO) { repository.all() }
            call.respond(feeds)
        }

        post {
            val feed = call.receive<FeedConfig>()
            validate(feed)?.let { return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(it)) }
            val created = withContext(Dispatchers.IO) { repository.create(feed) }
            if (created) {
                call.respond(HttpStatusCode.Created, StatusResponse("created"))
            } else {
                // Create is not overwrite: a duplicate id is a conflict, not a silent clobber.
                call.respond(HttpStatusCode.Conflict, ErrorResponse("feed with id '${feed.id}' already exists"))
            }
        }

        route("{feedId}") {
            put {
                val feedId = call.parameters["feedId"]!!
                // The path is authoritative for the id, so a body id is ignored (or absent).
                val feed = call.receive<FeedConfig>().copy(id = feedId)
                validate(feed)?.let { return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(it)) }
                val updated = withContext(Dispatchers.IO) { repository.update(feed) }
                if (updated) {
                    call.respond(HttpStatusCode.OK, StatusResponse("updated"))
                } else {
                    // Update never creates: a PUT to an unknown id is a 404, not a new feed.
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("no feed with id '$feedId'"))
                }
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
    if (feed.source == "kleinanzeigen" && !KLEINANZEIGEN_CATEGORY.containsMatchIn(feed.url)) {
        return "Kleinanzeigen url must be category-locked (a 'c203' category segment); a loose search pulls cross-category junk"
    }
    return null
}
