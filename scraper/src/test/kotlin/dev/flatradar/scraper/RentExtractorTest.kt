package dev.flatradar.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pure helpers of [RentExtractor]. The network call itself is
 * exercised by [RentExtractorIntegrationTest] (gated on GEMINI_API_KEY).
 */
class RentExtractorTest {

    @Test
    fun buildPrompt_includes_description_and_german_terms() {
        val prompt = RentExtractor.buildPrompt("Miete liegt bei 1100 Euro kalt zzgl. Nebenkosten.")
        assertTrue(prompt.contains("Miete liegt bei 1100 Euro kalt zzgl. Nebenkosten."))
        assertTrue(prompt.contains("Kaltmiete"))
        assertTrue(prompt.contains("Warmmiete"))
        assertTrue(prompt.contains("Nebenkosten"))
        assertTrue(prompt.contains("Heizkosten"))
    }

    @Test
    fun parseResponse_valid_json() {
        val parsed = RentExtractor.parseResponse(
            """{"baseRent":1100,"sideCosts":180,"heatingCosts":120,"totalRent":1400}"""
        )
        assertNotNull(parsed)
        assertEquals(1100, parsed?.baseRent)
        assertEquals(180, parsed?.sideCosts)
        assertEquals(120, parsed?.heatingCosts)
        assertEquals(1400, parsed?.totalRent)
    }

    @Test
    fun parseResponse_partial_json() {
        val parsed = RentExtractor.parseResponse("""{"totalRent":1087}""")
        assertNotNull(parsed)
        assertEquals(1087, parsed?.totalRent)
        assertNull(parsed?.baseRent)
        assertNull(parsed?.sideCosts)
        assertNull(parsed?.heatingCosts)
    }

    @Test
    fun parseResponse_all_null_returns_null() {
        assertNull(RentExtractor.parseResponse("""{"baseRent":null,"sideCosts":null,"heatingCosts":null,"totalRent":null}"""))
    }

    @Test
    fun parseResponse_empty_object_returns_null() {
        assertNull(RentExtractor.parseResponse("""{}"""))
    }

    @Test
    fun parseResponse_malformed_returns_null() {
        assertNull(RentExtractor.parseResponse("not json at all"))
    }

    @Test
    fun parseResponse_tolerates_unknown_keys() {
        val parsed = RentExtractor.parseResponse("""{"baseRent":1100,"confidence":0.9}""")
        assertNotNull(parsed)
        assertEquals(1100, parsed?.baseRent)
    }
}