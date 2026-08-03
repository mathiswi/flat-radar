package dev.flatradar.scraper.immoscout24

import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UrlTranslatorTest {

    @Test
    fun radius_search_translates_to_expected_mobile_params() {
        // The exact search the user asked to support (Hamburg / Barmbek-Nord, 2km radius).
        val webUrl = "https://www.immobilienscout24.de/Suche/radius/wohnung-mieten" +
            "?centerofsearchaddress=Hamburg%3B%3B%3B%3B%3BBarmbek-Nord%3B" +
            "&numberofrooms=2.0-3.0&price=-1500.0&livingspace=55.0-80.0" +
            "&pricetype=calculatedtotalrent&geocoordinates=53.59425%3B10.04675%3B2.0" +
            "&enteredFrom=result_list"

        val mobileUrl = UrlTranslator.webToMobile(webUrl)
        val params = queryParams(mobileUrl)

        assertTrue(mobileUrl.startsWith("https://api.mobile.immobilienscout24.de/search/list?"))
        assertEquals(
            mapOf(
                "searchType" to "radius",
                "realestatetype" to "apartmentrent",
                "pricetype" to "calculatedtotalrent",
                "geocoordinates" to "53.59425;10.04675;2.0",
                "numberofrooms" to "2.0-3.0",
                "price" to "-1500.0",
                "livingspace" to "55.0-80.0",
            ),
            params,
        )
        // centerofsearchaddress / enteredFrom are web-UI-only concerns and must be dropped.
        assertTrue("centerofsearchaddress" !in params)
        assertTrue("enteredFrom" !in params)
    }

    @Test
    fun region_search_builds_geocodes_from_path_segments() {
        // Real example from ImmoScout24's mobile-API reverse-engineering notes (fredy project).
        val webUrl = "https://www.immobilienscout24.de/Suche/de/nordrhein-westfalen/duesseldorf/wohnung-mieten" +
            "?numberofrooms=1.0-10000.0&price=1.0-10000.0&livingspace=10.0-10000.0" +
            "&pricetype=rentpermonth&enteredFrom=result_list"

        val mobileUrl = UrlTranslator.webToMobile(webUrl)
        val params = queryParams(mobileUrl)

        assertEquals(
            mapOf(
                "searchType" to "region",
                "realestatetype" to "apartmentrent",
                "geocodes" to "/de/nordrhein-westfalen/duesseldorf",
                "pricetype" to "rentpermonth",
                "numberofrooms" to "1.0-10000.0",
                "price" to "1.0-10000.0",
                "livingspace" to "10.0-10000.0",
            ),
            params,
        )
    }

    @Test
    fun house_rent_path_maps_to_houserent_type() {
        val webUrl = "https://www.immobilienscout24.de/Suche/radius/haus-mieten" +
            "?geocoordinates=51.22496%3B6.77567%3B5.0"
        val params = queryParams(UrlTranslator.webToMobile(webUrl))
        assertEquals("houserent", params["realestatetype"])
        assertEquals("radius", params["searchType"])
    }

    @Test
    fun exclusioncriteria_swapflat_is_renamed_to_swap_flat() {
        // The web UI's "swapflat" is meaningless to the mobile API - only "swap_flat" filters anything.
        val webUrl = "https://www.immobilienscout24.de/Suche/radius/wohnung-mieten" +
            "?geocoordinates=53.59425%3B10.04675%3B2.0&exclusioncriteria=swapflat"
        val params = queryParams(UrlTranslator.webToMobile(webUrl))
        assertEquals("swap_flat", params["exclusioncriteria"])
    }

    @Test
    fun non_suche_path_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            UrlTranslator.webToMobile("https://www.immobilienscout24.de/expose/12345")
        }
    }

    @Test
    fun unsupported_real_estate_type_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            UrlTranslator.webToMobile("https://www.immobilienscout24.de/Suche/radius/wohnung-kaufen?geocoordinates=1%3B1%3B1")
        }
    }

    /** Parses a URL's query string into a plain map, decoded, for order-independent assertions. */
    private fun queryParams(url: String): Map<String, String> {
        val query = URI(url).rawQuery
        return query.split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }
    }
}
