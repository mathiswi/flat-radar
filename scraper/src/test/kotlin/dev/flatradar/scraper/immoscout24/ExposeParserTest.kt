package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.loadResource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExposeParserTest {

    private val now = 1_750_000_000_000L

    @Test
    fun normal_expose_maps_every_field() {
        val json = loadResource("/mock/immoscout24/expose_normal.json")
        val url = ExposeParser.exposeUrl("106403560")

        val ad = ExposeParser.parse(json, url, "Bramfeld", now)

        assertNotNull(ad)
        assertEquals("106403560", ad.id)
        assertEquals("Gepflegte 3-Zimmer-EG-Wohnung mit Terasse und Einbauküche in Bramfeld, Hamburg", ad.title)
        assertEquals(72.0, ad.size)
        assertEquals(3.0, ad.rooms)
        assertEquals(1, ad.bathrooms)
        assertEquals("0 von 5", ad.floor)
        assertEquals("Erdgeschosswohnung", ad.apartmentType)
        assertEquals(LocalDate(2026, 8, 7), ad.availableFrom)
        assertEquals(2800, ad.deposit)
        assertEquals(950, ad.baseRent)
        assertEquals(150, ad.sideCosts)
        assertEquals(80, ad.heatingCosts)
        assertEquals(1180, ad.totalRent)
        assertEquals("22177 Bramfeld, Hamburg", ad.location)
        assertEquals("https://www.immobilienscout24.de/expose/106403560", ad.url)
        assertEquals("immoscout24", ad.source)
        assertEquals("Bramfeld", ad.district)
        assertEquals(now, ad.timestamp)
    }

    @Test
    fun expose_response_carries_no_geo_data_on_its_own() {
        // Confirms the premise behind AdRef.lat/lon/distanceMeters: the MAP section
        // only ever has addressLine1/addressLine2 (see ExposeSection), never
        // coordinates, so ExposeParser.parse can't populate these fields itself -
        // ImmoscoutParser.parseDetail merges them in from the search-list AdRef instead.
        val json = loadResource("/mock/immoscout24/expose_normal.json")
        val ad = ExposeParser.parse(json, ExposeParser.exposeUrl("106403560"), "Bramfeld", now)

        assertNotNull(ad)
        assertEquals(null, ad.lat)
        assertEquals(null, ad.lon)
        assertEquals(null, ad.distanceMeters)
    }

    @Test
    fun swap_expose_is_filtered_to_null() {
        val json = loadResource("/mock/immoscout24/expose_swap.json")
        val ad = ExposeParser.parse(json, ExposeParser.exposeUrl("169193126"), "Barmbek-Nord", now)
        assertNull(ad)
    }

    @Test
    fun swap_expose_would_otherwise_have_no_total_rent() {
        // "Warmmiete: keine Angabe" on the swap fixture - confirms parseEuros treats
        // non-numeric text as "field absent", not zero, independent of the swap filter.
        val json = loadResource("/mock/immoscout24/expose_swap.json")
        val response = ExposeParser.decode(json)
        val warmmiete = response?.sections
            ?.firstOrNull { it.type == "TOP_ATTRIBUTES" }
            ?.attributes
            ?.firstOrNull { it.label == "Warmmiete" }
            ?.text
        assertEquals("keine Angabe", warmmiete)
        assertEquals(null, ImmoscoutFormats.parseEuros(warmmiete!!))
    }

    @Test
    fun decode_returns_null_for_non_json_blocked_response() {
        assertEquals(null, ExposeParser.decode("<html>captcha</html>"))
    }

    @Test
    fun missing_header_id_falls_back_to_url() {
        val json = """
            {"header":{},"sections":[{"type":"TITLE","title":"Schoene Wohnung"}]}
        """.trimIndent()
        val ad = ExposeParser.parse(json, "https://api.mobile.immobilienscout24.de/expose/999888", "X", now)
        assertNotNull(ad)
        assertEquals("999888", ad.id)
        assertEquals("https://www.immobilienscout24.de/expose/999888", ad.url)
    }
}
