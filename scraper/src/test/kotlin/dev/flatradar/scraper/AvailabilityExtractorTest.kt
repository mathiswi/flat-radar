package dev.flatradar.scraper

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure-function tests for [AvailabilityExtractor]; the network call is never exercised. */
class AvailabilityExtractorTest {

    @Test
    fun parseResponse_reads_iso_date() {
        assertEquals(
            LocalDate(2026, 10, 1),
            AvailabilityExtractor.parseResponse("""{"availableFrom": "2026-10-01"}"""),
        )
    }

    @Test
    fun parseResponse_null_or_empty_is_null() {
        assertNull(AvailabilityExtractor.parseResponse("""{"availableFrom": null}"""))
        assertNull(AvailabilityExtractor.parseResponse("""{"availableFrom": ""}"""))
        assertNull(AvailabilityExtractor.parseResponse("""{}"""))
    }

    @Test
    fun parseResponse_unparseable_is_null() {
        assertNull(AvailabilityExtractor.parseResponse("not json"))
        assertNull(AvailabilityExtractor.parseResponse("""{"availableFrom": "someday"}"""))
    }

    @Test
    fun buildPrompt_includes_today_and_schema() {
        val prompt = AvailabilityExtractor.buildPrompt("frei ab Dezember", LocalDate(2026, 9, 11))
        assertTrue(prompt.contains("2026-09-11"))
        assertTrue(prompt.contains("\"availableFrom\""))
        assertTrue(prompt.contains("frei ab Dezember"))
    }
}
