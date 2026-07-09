package dev.flatradar.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import javax.sql.DataSource

/**
 * Health + readiness endpoints.
 *
 * - GET /api/v1/health: liveness. Always 200 {"status":"up"}.
 *   No DB check. Used by Docker / Compose healthcheck to confirm the
 *   process is alive.
 *
 * - GET /api/v1/ready: readiness. 200 {"status":"ready"} when the
 *   Postgres connection is reachable, 503 {"status":"not_ready",
 *   "error":"..."} otherwise. Used by Compose depends_on so the scraper
 *   doesn't fire until the backend can actually serve traffic.
 */
fun Route.healthRoutes(dataSource: DataSource) {
    route("/api/v1") {
        get("/health") {
            call.respond(mapOf("status" to "up"))
        }

        get("/ready") {
            val ok = try {
                dataSource.connection.use { it.isValid(2) }
            } catch (e: Exception) {
                System.err.println("[ready] DB check failed: ${e.message}")
                false
            }
            if (ok) {
                call.respond(mapOf("status" to "ready"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "not_ready"))
            }
        }
    }
}