package dev.flatradar.scraper

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackendFeedsTest {

    private fun feedsClient(status: HttpStatusCode, body: String): BackendClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val http = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return BackendClient(http, baseUrl = "http://backend")
    }

    @Test
    fun parses_feeds_from_backend_response() = runTest {
        val json = """
            [
              { "id": "barmbek", "displayName": "Barmbek", "source": "kleinanzeigen",
                "url": "https://example.de/c203", "district": "Barmbek", "enabled": true },
              { "id": "is24", "displayName": "IS24", "source": "immoscout24",
                "url": "https://example.de", "district": "Barmbek-Nord", "enabled": false }
            ]
        """.trimIndent()

        val feeds = BackendFeeds(feedsClient(HttpStatusCode.OK, json)).all()

        assertEquals(2, feeds.size)
        assertEquals("barmbek", feeds[0].id)
        assertEquals("immoscout24", feeds[1].source)
        assertEquals(false, feeds[1].enabled)
    }

    @Test
    fun defaults_apply_when_optional_fields_missing() = runTest {
        // source/enabled omitted: the shared FeedConfig defaults must fill them in.
        val json = """
            [{ "id": "x", "displayName": "X", "url": "https://example.de/c203", "district": "Barmbek" }]
        """.trimIndent()

        val feeds = BackendFeeds(feedsClient(HttpStatusCode.OK, json)).all()

        assertEquals("kleinanzeigen", feeds[0].source)
        assertEquals(true, feeds[0].enabled)
    }

    @Test
    fun unknown_fields_from_a_newer_backend_are_tolerated() = runTest {
        // Forward-compat: an older scraper binary decoding a newer backend's feed
        // JSON (extra fields) must not break. Relies on ignoreUnknownKeys.
        val json = """
            [{ "id": "x", "displayName": "X", "url": "https://example.de/c203",
               "district": "Barmbek", "source": "kleinanzeigen", "enabled": true,
               "lastFetched": "2026-07-07T10:00:00Z", "ownerUserId": 42 }]
        """.trimIndent()

        val feeds = BackendFeeds(feedsClient(HttpStatusCode.OK, json)).all()
        assertEquals(1, feeds.size)
        assertEquals("x", feeds[0].id)
    }

    @Test
    fun backend_error_throws_so_the_run_fails_loudly() = runTest {
        val client = feedsClient(HttpStatusCode.InternalServerError, "boom")
        assertFailsWith<BackendException> { BackendFeeds(client).all() }
    }
}
