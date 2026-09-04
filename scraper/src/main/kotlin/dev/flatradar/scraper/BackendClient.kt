package dev.flatradar.scraper

import dev.flatradar.shared.ApartmentAd
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class BackendClient(
    private val client: HttpClient,
    baseUrl: String = Env.get("BACKEND_URL") ?: "http://localhost:8080",
) {
    private val apiUrl = baseUrl.trimEnd('/')

    suspend fun preFilter(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val response = client.post("$apiUrl/api/v1/listings/ids") {
            contentType(ContentType.Application.Json)
            setBody(ids)
        }
        response.ensureSuccess()
        return response.body<List<String>>().toSet()
    }

    /**
     * Ingests a single ad. Returns `true` when the backend reports it as newly
     * inserted (`201 Created`), `false` when it already existed (`200 OK`).
     * Any other status is treated as a hard failure, not "already exists".
     */
    suspend fun ingest(ad: ApartmentAd): Boolean {
        val response = client.post("$apiUrl/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(ad)
        }
        return when (response.status) {
            HttpStatusCode.Created -> true
            HttpStatusCode.OK -> false
            else -> throw BackendException(
                "unexpected response ingesting ${ad.id}: ${response.status.value} ${response.bodyAsText()}"
            )
        }
    }

    /**
     * Reports the full set of ad IDs currently visible for [feedId] this run, so the
     * backend can mark still-alive listings and delist ones that have disappeared. Call
     * only on a successful run: an aborted/failed feed must not report a partial set (it
     * would falsely delist the missing ads). An empty [ids] is a no-op on the backend.
     */
    suspend fun reportSeen(feedId: String, ids: List<String>): Int {
        val response = client.post("$apiUrl/api/v1/feeds/$feedId/seen") {
            contentType(ContentType.Application.Json)
            setBody(ids)
        }
        response.ensureSuccess()
        return response.body<SeenResult>().delisted
    }

    private suspend fun HttpResponse.ensureSuccess() {
        if (!status.isSuccess()) {
            throw BackendException("backend returned ${status.value}: ${bodyAsText()}")
        }
    }
}

@kotlinx.serialization.Serializable
private data class SeenResult(val delisted: Int)

class BackendException(message: String) : RuntimeException(message)
