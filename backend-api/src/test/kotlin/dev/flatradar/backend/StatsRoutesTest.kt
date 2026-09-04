package dev.flatradar.backend

import dev.flatradar.shared.ApartmentAd
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StatsRoutesTest {

    private fun freshDataSource(): DataSource {
        // Class-unique prefix: H2 in-memory DBs live for the whole JVM (DB_CLOSE_DELAY=-1),
        // so a bare "test${n}" would collide with another test class's identically-named DB
        // and race on migrations.
        val name = "statstest${dbCounter.incrementAndGet()}"
        return JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleAd(id: String = "ad-1") = ApartmentAd(
        id = id,
        title = "Nice flat",
        size = 57.0,
        rooms = 2.0,
        bedrooms = null,
        bathrooms = null,
        floor = null,
        apartmentType = null,
        availableFrom = null,
        deposit = 1000,
        baseRent = 900,
        sideCosts = 100,
        heatingCosts = 50,
        totalRent = 1050,
        location = "22880 Niendorf",
        url = "https://example.de/$id",
        source = "kleinanzeigen",
        district = "Niendorf",
        lat = 53.59425,
        lon = 10.04675,
        distanceMeters = 1600,
        timestamp = 1_750_000_000_000L,
    )

    @Test
    fun stats_endpoint_returns_counts_after_ingest() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd()))
        }

        val response = client.get("/api/v1/stats")
        assertEquals(HttpStatusCode.OK, response.status)
        val stats = json.decodeFromString<StatsResponse>(response.bodyAsText())
        assertEquals(1, stats.totalListings)
        assertEquals(1, stats.pendingOutbox)
        assertEquals(0, stats.deadLettered)
        assertNotNull(stats.lastScrape)
    }

    @Test
    fun stats_returns_zeroes_when_no_listings() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val response = client.get("/api/v1/stats")
        assertEquals(HttpStatusCode.OK, response.status)
        val stats = json.decodeFromString<StatsResponse>(response.bodyAsText())
        assertEquals(0, stats.totalListings)
        assertEquals(0, stats.pendingOutbox)
        assertEquals(0, stats.deadLettered)
        assertEquals(null, stats.lastScrape)
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
        const val TEST_CHANGELOG = "db/changelog/test-changelog.yaml"
    }
}
