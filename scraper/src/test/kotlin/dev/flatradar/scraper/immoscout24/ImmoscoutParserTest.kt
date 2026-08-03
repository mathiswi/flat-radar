package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.AdRef
import dev.flatradar.scraper.loadResource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImmoscoutParserTest {

    @Test
    fun searchUrl_translates_and_appends_max_page_size() {
        val feedUrl = "https://www.immobilienscout24.de/Suche/radius/wohnung-mieten" +
            "?geocoordinates=53.59425%3B10.04675%3B2.0&numberofrooms=2.0-3.0"

        val searchUrl = ImmoscoutParser.searchUrl(feedUrl)

        assertTrue(searchUrl.startsWith("https://api.mobile.immobilienscout24.de/search/list?"))
        assertTrue(searchUrl.contains("pagesize=100"))
        assertTrue(searchUrl.endsWith("&pagenumber=1"))
    }

    @Test
    fun parseDetail_delegates_to_expose_parser() = runTest {
        val json = loadResource("/mock/immoscout24/expose_normal.json")
        val ad = ImmoscoutParser.parseDetail(json, ExposeParser.exposeUrl("106403560"), "Bramfeld", 42L)

        assertNotNull(ad)
        assertEquals("immoscout24", ad.source)
        assertEquals(950, ad.baseRent)
    }

    @Test
    fun parseDetail_filters_swap_listing_to_null() = runTest {
        val json = loadResource("/mock/immoscout24/expose_swap.json")
        val ad = ImmoscoutParser.parseDetail(json, ExposeParser.exposeUrl("169193126"), "Barmbek-Nord", 42L)
        assertNull(ad)
    }

    @Test
    fun parseDetail_merges_geo_data_from_the_search_list_ref() = runTest {
        // The expose response itself carries no lat/lon/distance (see ExposeParserTest) -
        // this data has to come from the AdRef the search-list parse produced.
        val json = loadResource("/mock/immoscout24/expose_normal.json")
        val ref = AdRef(
            adId = "106403560",
            url = ExposeParser.exposeUrl("106403560"),
            title = "irrelevant here",
            lat = 53.60532,
            lon = 10.06318,
            distanceMeters = 1600,
        )

        val ad = ImmoscoutParser.parseDetail(json, ref.url, "Bramfeld", 42L, ref = ref)

        assertNotNull(ad)
        assertEquals(53.60532, ad.lat)
        assertEquals(10.06318, ad.lon)
        assertEquals(1600, ad.distanceMeters)
    }

    @Test
    fun parseDetail_leaves_geo_data_null_when_no_ref_is_given() = runTest {
        val json = loadResource("/mock/immoscout24/expose_normal.json")
        val ad = ImmoscoutParser.parseDetail(json, ExposeParser.exposeUrl("106403560"), "Bramfeld", 42L)

        assertNotNull(ad)
        assertNull(ad.lat)
        assertNull(ad.lon)
        assertNull(ad.distanceMeters)
    }
}
