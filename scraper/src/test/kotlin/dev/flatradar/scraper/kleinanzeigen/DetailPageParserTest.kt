package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.ParsedRents
import dev.flatradar.scraper.RentFallback
import dev.flatradar.scraper.loadResource
import dev.flatradar.shared.ApartmentAd
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailPageParserTest {

    private val now = 1_750_000_000_000L

    @Test
    fun normal_ad_with_no_structured_rents_fires_llm_fallback() = runTest {
        val html = loadResource("/mock/kleinanzeigen/detail_normal.html")
        val fallback = RecordingFallback(
            ParsedRents(baseRent = 1100, sideCosts = 180, heatingCosts = 120, totalRent = 1400)
        )

        val ad = DetailPageParser.parse(html, NORMAL_URL, "Barmbek", now, fallback)

        assertNotNull(ad)
        assertEquals("Barmbek", ad?.district)
        assertEquals("kleinanzeigen", ad?.source)
        assertEquals(now, ad?.timestamp)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun swap_ad_is_filtered_to_null() = runTest {
        val html = loadResource("/mock/kleinanzeigen/detail_swap.html")
        val fallback = RecordingFallback()

        val ad = DetailPageParser.parse(html, SWAP_URL, "Barmbek", now, fallback)

        assertNull(ad)
        assertEquals(0, fallback.calls, "LLM must not fire for filtered swap ad")
    }

    @Test
    fun full_structured_attrs_parse_without_firing_llm() = runTest {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val fallback = RecordingFallback()

        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now, fallback)

        assertNotNull(ad)
        // detail_full has Warmmiete/Nebenkosten/Heizkosten but no Kaltmiete - missing
        // Kaltmiete alone does NOT trigger the LLM (it's not a slot-filler).
        assertEquals(1087, ad?.totalRent)
        assertEquals(110, ad?.sideCosts)
        assertEquals(110, ad?.heatingCosts)
        assertNull(ad?.baseRent)
        assertEquals(0, fallback.calls, "LLM must not fire when structured rents are present")
    }

    @Test
    fun null_fallback_is_accepted() = runTest {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now, null)
        assertNotNull(ad)
        assertEquals(1087, ad?.totalRent)
    }

    @Test
    fun parsed_ad_serializes_to_json() = runTest {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now, null)
        assertNotNull(ad)
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(ApartmentAd.serializer(), ad!!)
        assertTrue(encoded.contains("\"totalRent\":1087"))
    }

    private companion object {
        const val NORMAL_URL =
            "https://www.kleinanzeigen.de/s-anzeige/erstbezug-nach-hochwertiger-kernsanierung-3-zimmer-wohnung-/3450160395-203-9449"
        const val SWAP_URL =
            "https://www.kleinanzeigen.de/s-anzeige/tauschwohnung-gemuetliche-wohnung-in-hamburg-nord-naehe-planetarium-/3425649112-203-9480"
        const val FULL_URL =
            "https://www.kleinanzeigen.de/s-anzeige/2-zi-wohnung-mit-balkon-in-niendorf-nahe-u-bahn-ab-august/3452480994-203-16475"
    }
}

private class RecordingFallback(initial: ParsedRents? = null) : RentFallback {
    var calls = 0
    private val initial = initial

    override suspend fun extract(description: String): ParsedRents? {
        calls++
        return initial
    }
}