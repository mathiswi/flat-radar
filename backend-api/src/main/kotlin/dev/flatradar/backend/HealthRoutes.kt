package dev.flatradar.backend

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            call.respond(StatusResponse("up"))
        }

        get("/ready") {
            val error = withContext(Dispatchers.IO) {
                try {
                    dataSource.connection.use { if (it.isValid(2)) null else "connection not valid" }
                } catch (e: Exception) {
                    System.err.println("[ready] DB check failed: ${e.message}")
                    e.message ?: "unknown error"
                }
            }
            if (error == null) {
                call.respond(ReadyResponse("ready"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, ReadyResponse("not_ready", error))
            }
        }
    }
}
