package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.loadResource
import dev.flatradar.shared.ApartmentAd
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailPageParserTest {

    private val now = 1_750_000_000_000L

    @Test
    fun normal_ad_with_no_structured_rents_still_parses() {
        // No Kaltmiete/Warmmiete/Nebenkosten/Heizkosten rows: totalRent falls back to the
        // #viewad-price headline (the LLM rent fallback was removed). The ad still parses.
        val html = loadResource("/mock/kleinanzeigen/detail_normal.html")
        val ad = DetailPageParser.parse(html, NORMAL_URL, "Barmbek", now)

        assertNotNull(ad)
        assertEquals("Barmbek", ad?.district)
        assertEquals("kleinanzeigen", ad?.source)
        assertEquals(now, ad?.timestamp)
    }

    @Test
    fun swap_ad_is_filtered_to_null() {
        val html = loadResource("/mock/kleinanzeigen/detail_swap.html")
        val ad = DetailPageParser.parse(html, SWAP_URL, "Barmbek", now)
        assertNull(ad)
    }

    @Test
    fun full_structured_attrs_parse() {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now)

        assertNotNull(ad)
        // detail_full has Warmmiete/Nebenkosten/Heizkosten but no Kaltmiete.
        assertEquals(1087, ad?.totalRent)
        assertEquals(110, ad?.sideCosts)
        assertEquals(110, ad?.heatingCosts)
        assertNull(ad?.baseRent)
    }

    @Test
    fun parsed_ad_serializes_to_json() {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now)
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
