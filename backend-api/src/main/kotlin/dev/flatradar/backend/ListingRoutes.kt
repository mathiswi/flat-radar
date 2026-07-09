package dev.flatradar.backend

import dev.flatradar.shared.ApartmentAd
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.listingRoutes(repository: ListingRepository) {
    route("/api/v1/listings") {
        post("/ids") {
            val ids = call.receive<List<String>>()
            val existing = withContext(Dispatchers.IO) { repository.existingIds(ids) }
            call.respond(existing.toList())
        }

        post {
            val ad = call.receive<ApartmentAd>()
            val result = withContext(Dispatchers.IO) { repository.upsert(ad) }
            if (result == InsertResult.NEW) {
                call.respond(HttpStatusCode.Created, StatusResponse("inserted"))
            } else {
                call.respond(HttpStatusCode.OK, StatusResponse("already_exists"))
            }
        }

        post("/batch") {
            val ads = call.receive<List<ApartmentAd>>()
            val count = withContext(Dispatchers.IO) { repository.upsertBatch(ads) }
            call.respond(HttpStatusCode.Created, BatchResponse("inserted", count))
        }

        get {
            val listings = withContext(Dispatchers.IO) { repository.findAll() }
            call.respond(listings)
        }
    }
}
