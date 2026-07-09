package dev.flatradar.scraper

import dev.flatradar.shared.ApartmentAd
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class IngestResponse(val status: String, val count: Int? = null)

class BackendClient(
    baseUrl: String = System.getenv("BACKEND_URL") ?: "http://localhost:8080",
) {
    private val apiUrl = baseUrl.trimEnd('/')
    private val client = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun preFilter(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val response = client.post("$apiUrl/api/v1/listings/ids") {
            contentType(ContentType.Application.Json)
            setBody(ids)
        }
        response.ensureSuccess()
        return response.body<List<String>>().toSet()
    }

    suspend fun ingestBatch(ads: List<ApartmentAd>): IngestResponse {
        if (ads.isEmpty()) return IngestResponse(status = "no-op", count = 0)
        val response = client.post("$apiUrl/api/v1/listings/batch") {
            contentType(ContentType.Application.Json)
            setBody(ads)
        }
        response.ensureSuccess()
        return response.body()
    }

    fun close() {
        client.close()
    }

    private fun HttpResponse.ensureSuccess() {
        if (!status.isSuccess()) {
            throw BackendException("backend returned ${status.value}")
        }
    }
}

class BackendException(message: String) : RuntimeException(message)