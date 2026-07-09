package dev.flatradar.backend

import dev.flatradar.shared.ApartmentAd
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.listingRoutes(repository: ListingRepository) {
    route("/api/v1/listings") {
        post("/ids") {
            val ids = call.receive<List<String>>()
            val existing = repository.existingIds(ids)
            call.respond(existing.toList())
        }

        post {
            val ad = call.receive<ApartmentAd>()
            val result = repository.upsert(ad)
            if (result == InsertResult.NEW) {
                call.respond(HttpStatusCode.Created, mapOf("status" to "inserted"))
            } else {
                call.respond(HttpStatusCode.OK, mapOf("status" to "already_exists"))
            }
        }

        post("/batch") {
            val ads = call.receive<List<ApartmentAd>>()
            val count = repository.upsertBatch(ads)
            call.respond(HttpStatusCode.Created, mapOf("status" to "inserted", "count" to count))
        }

        get {
            val listings = repository.findAll()
            call.respond(listings)
        }
    }
}