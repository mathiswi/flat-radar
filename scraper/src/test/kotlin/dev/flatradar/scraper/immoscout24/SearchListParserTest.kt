package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.loadResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchListParserTest {

    @Test
    fun parser_returns_all_listings_including_swap_ones() {
        // Pure-parse contract, mirroring kleinanzeigen.SearchPageParserTest: the parser
        // must NOT filter swaps. That's ImmoscoutParser.parseSearch's job.
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val refs = SearchListParser.parse(json)

        assertTrue(refs.isNotEmpty(), "expected at least one ref from real-search snapshot")
        assertTrue(refs.all { it.adId.isNotBlank() })
        assertTrue(refs.all { it.url.startsWith("https://api.mobile.immobilienscout24.de/expose/") })
        assertTrue(refs.any { it.title.startsWith("Tauschwohnung:", ignoreCase = true) }, "fixture should contain swap-titled listings")
    }

    @Test
    fun parser_carries_geo_data_for_listings_with_a_precise_address() {
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val refs = SearchListParser.parse(json)

        val ref = refs.first { it.adId == "106403560" }
        assertEquals(53.60532, ref.lat)
        assertEquals(10.06318, ref.lon)
        assertEquals(1600, ref.distanceMeters, "\"1,6 km\" should parse to 1600 metres")
    }

    @Test
    fun parser_leaves_geo_data_null_for_listings_with_only_an_incomplete_address() {
        // id 169101611's address only has {line, postcode} - no lat/lon/distance,
        // because ImmoScout24 doesn't expose a precise address for that listing.
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val refs = SearchListParser.parse(json)

        val ref = refs.first { it.adId == "169101611" }
        assertEquals(null, ref.lat)
        assertEquals(null, ref.lon)
        assertEquals(null, ref.distanceMeters)
    }

    @Test
    fun decode_exposes_pagination_fields() {
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val response = SearchListParser.decode(json)
        assertEquals(1, response?.pageNumber)
        assertEquals(2, response?.numberOfPages)
    }

    @Test
    fun decode_returns_null_for_non_json_blocked_response() {
        // A challenge/blocked-page body isn't JSON - decode() must fail closed, not throw.
        assertEquals(null, SearchListParser.decode("<html>captcha</html>"))
    }

    @Test
    fun source_parser_filters_swap_titled_listings() {
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val refs = ImmoscoutParser.parseSearch(json)

        assertTrue(refs.isNotEmpty())
        assertTrue(refs.none { it.title.trim().uppercase().startsWith("TAUSCHWOHNUNG") })
    }

    @Test
    fun source_parser_preserves_geo_data_through_swap_filtering() {
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")
        val refs = ImmoscoutParser.parseSearch(json)

        val ref = refs.first { it.adId == "106403560" }
        assertEquals(53.60532, ref.lat)
        assertEquals(1600, ref.distanceMeters)
    }

    @Test
    fun pagination_produces_remaining_page_urls() {
        val firstPageUrl = ImmoscoutParser.searchUrl(
            "https://www.immobilienscout24.de/Suche/radius/wohnung-mieten?geocoordinates=53.59425%3B10.04675%3B2.0"
        )
        val json = loadResource("/mock/immoscout24/search_barmbek_p1.json")

        val pages = ImmoscoutParser.nextSearchPageUrls(firstPageUrl, json)

        assertEquals(1, pages.size, "fixture declares numberOfPages=2, so exactly one extra page is expected")
        assertTrue(pages[0].endsWith("&pagenumber=2"))
        assertTrue(pages[0].startsWith(firstPageUrl.removeSuffix("&pagenumber=1")))
    }

    @Test
    fun single_page_response_needs_no_extra_pages() {
        val firstPageUrl = ImmoscoutParser.searchUrl(
            "https://www.immobilienscout24.de/Suche/radius/wohnung-mieten?geocoordinates=53.59425%3B10.04675%3B2.0"
        )
        val singlePageJson = """{"totalResults":3,"pageNumber":1,"numberOfPages":1,"resultListItems":[]}"""
        assertTrue(ImmoscoutParser.nextSearchPageUrls(firstPageUrl, singlePageJson).isEmpty())
    }
}
