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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Route-level tests backed by H2 in PostgreSQL-compatibility mode instead of a real
 * Postgres. Liquibase's Postgres-flavoured changeset (TIMESTAMPTZ, defaultValueComputed:
 * now()) applies cleanly against H2 in this mode, so a full Testcontainers/Docker
 * dependency isn't needed for these tests to be meaningful.
 */
class ListingRoutesTest {

    private fun freshDataSource(): DataSource {
        // Class-unique prefix: H2 in-memory DBs live for the whole JVM (DB_CLOSE_DELAY=-1),
        // so a bare "test${n}" would collide with another test class's identically-named DB
        // and race on migrations.
        val name = "routestest${dbCounter.incrementAndGet()}"
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
    fun posting_the_same_ad_twice_returns_created_then_ok() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        val first = client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd()))
        }
        assertEquals(HttpStatusCode.Created, first.status)
        assertEquals("inserted", json.decodeFromString<StatusResponse>(first.bodyAsText()).status)

        val second = client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd()))
        }
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals("already_exists", json.decodeFromString<StatusResponse>(second.bodyAsText()).status)
    }

    @Test
    fun ids_endpoint_returns_only_existing_ids() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd("existing")))
        }

        val response = client.post("/api/v1/listings/ids") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(listOf("existing", "missing")))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("existing"), json.decodeFromString<List<String>>(response.bodyAsText()))
    }

    @Test
    fun get_listings_returns_all_stored_ads() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd("a")))
        }
        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd("b")))
        }

        val response = client.get("/api/v1/listings")
        assertEquals(HttpStatusCode.OK, response.status)
        val listings = json.decodeFromString(ListSerializer(ApartmentAd.serializer()), response.bodyAsText())
        assertEquals(2, listings.size)
        assertTrue(listings.any { it.id == "a" })
        assertTrue(listings.any { it.id == "b" })
    }

    @Test
    fun geo_fields_round_trip_through_ingest_and_read() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ApartmentAd.serializer(), sampleAd("geo")))
        }

        val response = client.get("/api/v1/listings")
        val listings = json.decodeFromString(ListSerializer(ApartmentAd.serializer()), response.bodyAsText())
        val stored = listings.first { it.id == "geo" }

        assertEquals(53.59425, stored.lat)
        assertEquals(10.04675, stored.lon)
        assertEquals(1600, stored.distanceMeters)
    }

    @Test
    fun geo_fields_default_to_null_when_absent() = testApplication {
        application { module(freshDataSource(), TEST_CHANGELOG) }

        client.post("/api/v1/listings") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ApartmentAd.serializer(),
                    sampleAd("no-geo").copy(lat = null, lon = null, distanceMeters = null),
                )
            )
        }

        val response = client.get("/api/v1/listings")
        val listings = json.decodeFromString(ListSerializer(ApartmentAd.serializer()), response.bodyAsText())
        val stored = listings.first { it.id == "no-geo" }

        assertEquals(null, stored.lat)
        assertEquals(null, stored.lon)
        assertEquals(null, stored.distanceMeters)
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
        const val TEST_CHANGELOG = "db/changelog/test-changelog.yaml"
    }
}
