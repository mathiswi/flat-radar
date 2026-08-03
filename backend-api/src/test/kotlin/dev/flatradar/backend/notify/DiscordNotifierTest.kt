package dev.flatradar.backend.notify

import dev.flatradar.shared.ApartmentAd
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DiscordNotifierTest {

    private fun sampleAd() = ApartmentAd(
        id = "ad-1",
        title = "Schöne 2-Zimmer-Wohnung",
        size = 57.0,
        rooms = 2.0,
        bedrooms = null,
        bathrooms = null,
        floor = null,
        apartmentType = null,
        availableFrom = null,
        deposit = null,
        baseRent = 900,
        sideCosts = null,
        heatingCosts = null,
        totalRent = 1050,
        location = "22880 Niendorf",
        url = "https://example.de/ad-1",
        source = "kleinanzeigen",
        district = "Niendorf",
        lat = 53.6,
        lon = 10.0,
        distanceMeters = 1600,
        timestamp = 1_750_000_000_000L,
    )

    private fun notifierWith(engine: MockEngine) = DiscordNotifier(HttpClient(engine), "https://discord.example/webhook")

    @Test
    fun buildPayload_includes_rent_size_rooms_location_and_distance() {
        val notifier = notifierWith(MockEngine { respond("") })

        val embed = notifier.buildPayload(sampleAd()).embeds.single()

        assertEquals("Schöne 2-Zimmer-Wohnung", embed.title)
        assertEquals("https://example.de/ad-1", embed.url)
        val fieldsByName = embed.fields.associate { it.name to it.value }
        assertEquals("1050 €", fieldsByName["Warmmiete"])
        assertEquals("57 m²", fieldsByName["Größe"])
        assertEquals("2", fieldsByName["Zimmer"])
        assertEquals("Niendorf", fieldsByName["Ort"])
        assertEquals("1.6 km", fieldsByName["Entfernung"])
    }

    @Test
    fun buildPayload_falls_back_to_Kaltmiete_when_no_total_rent_is_known() {
        val notifier = notifierWith(MockEngine { respond("") })

        val embed = notifier.buildPayload(sampleAd().copy(totalRent = null)).embeds.single()

        val fieldsByName = embed.fields.associate { it.name to it.value }
        assertEquals("900 €", fieldsByName["Kaltmiete"])
        assertFalse("Warmmiete" in fieldsByName)
    }

    @Test
    fun buildPayload_omits_optional_fields_that_are_null() {
        val notifier = notifierWith(MockEngine { respond("") })
        val minimalAd = sampleAd().copy(
            totalRent = null,
            baseRent = null,
            size = null,
            rooms = null,
            district = null,
            location = "",
            distanceMeters = null,
        )

        val embed = notifier.buildPayload(minimalAd).embeds.single()

        val fieldNames = embed.fields.map { it.name }
        assertFalse("Warmmiete" in fieldNames)
        assertFalse("Kaltmiete" in fieldNames)
        assertFalse("Größe" in fieldNames)
        assertFalse("Zimmer" in fieldNames)
        assertFalse("Ort" in fieldNames)
        assertFalse("Entfernung" in fieldNames)
    }

    @Test
    fun notify_succeeds_on_a_plain_200_response() = runTest {
        val engine = MockEngine { respond("ok", status = HttpStatusCode.OK) }

        notifierWith(engine).notify(sampleAd())

        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun notify_retries_once_after_honoring_a_429_retry_after_header() = runTest {
        val callCount = AtomicInteger(0)
        val engine = MockEngine {
            if (callCount.getAndIncrement() == 0) {
                respond(content = "", status = HttpStatusCode.TooManyRequests, headers = headersOf(HttpHeaders.RetryAfter, listOf("0")))
            } else {
                respond(content = "ok", status = HttpStatusCode.OK)
            }
        }

        notifierWith(engine).notify(sampleAd())

        assertEquals(2, callCount.get())
    }

    @Test
    fun notify_throws_when_a_second_429_follows_the_retry() = runTest {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.TooManyRequests, headers = headersOf(HttpHeaders.RetryAfter, listOf("0")))
        }

        assertFailsWith<IllegalStateException> { notifierWith(engine).notify(sampleAd()) }
    }
}
