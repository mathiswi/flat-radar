package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.AvailabilityFallback
import dev.flatradar.scraper.loadResource
import dev.flatradar.shared.ApartmentAd
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailPageParserTest {

    private val now = 1_750_000_000_000L

    @Test
    fun normal_ad_with_no_structured_rents_still_parses() = runBlocking {
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
    fun swap_ad_is_filtered_to_null() = runBlocking {
        val html = loadResource("/mock/kleinanzeigen/detail_swap.html")
        val ad = DetailPageParser.parse(html, SWAP_URL, "Barmbek", now)
        assertNull(ad)
    }

    @Test
    fun full_structured_attrs_parse() = runBlocking {
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
    fun parsed_ad_serializes_to_json() = runBlocking {
        val html = loadResource("/mock/kleinanzeigen/detail_full.html")
        val ad = DetailPageParser.parse(html, FULL_URL, "Niendorf", now)
        assertNotNull(ad)
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(ApartmentAd.serializer(), ad!!)
        assertTrue(encoded.contains("\"totalRent\":1087"))
    }

    // --- availableFrom fallback chain (structured -> text scan -> LLM) ---

    private class SpyFallback(private val date: LocalDate?) : AvailabilityFallback {
        var calls = 0
        override suspend fun extract(description: String): LocalDate? {
            calls++
            return date
        }
    }

    private fun html(description: String) = """
        <html><body>
          <h1 id="viewad-title">Helle 2-Zimmer-Wohnung</h1>
          <div id="viewad-price">1.200 €</div>
          <input name="adId" value="123456789"/>
          <div id="viewad-description-text">$description</div>
        </body></html>
    """.trimIndent()

    @Test
    fun text_scan_fills_date_and_llm_is_not_called() = runBlocking {
        // Concrete date in the description prose (no structured "Verfügbar ab" row):
        // the cheap deterministic scan resolves it, so the LLM must not be invoked.
        val spy = SpyFallback(LocalDate(2099, 1, 1))
        val ad = DetailPageParser.parse(
            html("Tolle Wohnung. Verfügbar ab: 01.10.2026. Frisch saniert."),
            SOME_URL, "Barmbek", now, spy,
        )
        assertNotNull(ad)
        assertEquals(LocalDate(2026, 10, 1), ad?.availableFrom)
        assertEquals(0, spy.calls)
    }

    @Test
    fun llm_fallback_fills_date_when_text_scan_cannot() = runBlocking {
        // An availability *signal* ("frei ab") but no parseable date -> LLM last resort runs.
        val spy = SpyFallback(LocalDate(2026, 12, 1))
        val ad = DetailPageParser.parse(
            html("Schöne Wohnung, frei ab nach Vereinbarung."),
            SOME_URL, "Barmbek", now, spy,
        )
        assertNotNull(ad)
        assertEquals(LocalDate(2026, 12, 1), ad?.availableFrom)
        assertEquals(1, spy.calls)
    }

    @Test
    fun llm_is_not_called_without_any_availability_signal() = runBlocking {
        // No availability wording at all -> don't spend an LLM call; availableFrom stays null.
        val spy = SpyFallback(LocalDate(2026, 12, 1))
        val ad = DetailPageParser.parse(
            html("Schöne Wohnung mit Balkon und Einbauküche."),
            SOME_URL, "Barmbek", now, spy,
        )
        assertNotNull(ad)
        assertNull(ad?.availableFrom)
        assertEquals(0, spy.calls)
    }

    @Test
    fun no_fallback_configured_leaves_date_null() = runBlocking {
        val ad = DetailPageParser.parse(
            html("Schöne Wohnung, frei ab nach Vereinbarung."),
            SOME_URL, "Barmbek", now, null,
        )
        assertNotNull(ad)
        assertNull(ad?.availableFrom)
    }

    private companion object {
        const val NORMAL_URL =
            "https://www.kleinanzeigen.de/s-anzeige/erstbezug-nach-hochwertiger-kernsanierung-3-zimmer-wohnung-/3450160395-203-9449"
        const val SWAP_URL =
            "https://www.kleinanzeigen.de/s-anzeige/tauschwohnung-gemuetliche-wohnung-in-hamburg-nord-naehe-planetarium-/3425649112-203-9480"
        const val FULL_URL =
            "https://www.kleinanzeigen.de/s-anzeige/2-zi-wohnung-mit-balkon-in-niendorf-nahe-u-bahn-ab-august/3452480994-203-16475"
        const val SOME_URL =
            "https://www.kleinanzeigen.de/s-anzeige/helle-2-zimmer-wohnung/123456789-203-9482"
    }
}
