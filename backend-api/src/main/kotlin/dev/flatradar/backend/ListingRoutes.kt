package dev.flatradar.backend

import dev.flatradar.shared.ApartmentAd
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Body of PATCH /listings/{id}: the fields an operator can toggle on a listing. */
@Serializable
data class ListingPatch(val fake: Boolean)

fun Route.listingRoutes(repository: ListingRepository) {
    route("$API_V1/listings") {
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

        get {
            val listings = withContext(Dispatchers.IO) { repository.findAll() }
            call.respond(listings)
        }

        patch("{id}") {
            val id = call.parameters["id"]!!
            val patch = call.receive<ListingPatch>()
            val updated = withContext(Dispatchers.IO) { repository.setFake(id, patch.fake) }
            if (updated) {
                call.respond(HttpStatusCode.OK, StatusResponse("updated"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("no listing with id '$id'"))
            }
        }
    }
}
