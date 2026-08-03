package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.AdRef
import dev.flatradar.scraper.RentFallback
import dev.flatradar.scraper.SourceParser
import dev.flatradar.scraper.SwapDetector
import dev.flatradar.shared.ApartmentAd

/**
 * Single registration entry for the "immoscout24" source.
 *
 * Coherence point for this source's policy, mirroring [dev.flatradar.scraper.kleinanzeigen.KleinanzeigenParser]:
 *  - translates the feed's web search URL into a mobile-API request URL ([searchUrl])
 *  - filters Tauschwohnung/swap listings by title so no detail fetch is wasted on them
 *  - exposes the remaining search-result pages ([nextSearchPageUrls]) so [Main] can
 *    fetch them through the existing rate limiter before the backend pre-filter
 *
 * [SearchListParser] / [ExposeParser] stay pure JSON transformations with no
 * source-level filtering inline, so they remain independently testable.
 */
object ImmoscoutParser : SourceParser {

    /** Always request the maximum page size so pagination needs as few requests as possible. */
    private const val PAGE_SIZE = 100

    override fun searchUrl(feedUrl: String): String =
        "${UrlTranslator.webToMobile(feedUrl)}&pagesize=$PAGE_SIZE&pagenumber=1"

    override fun parseSearch(html: String): List<AdRef> =
        SearchListParser.parse(html).filterNot { SwapDetector.isSwapByTitle(it.title) }

    override fun nextSearchPageUrls(firstPageUrl: String, firstPageResponse: String): List<String> {
        val totalPages = SearchListParser.decode(firstPageResponse)?.numberOfPages ?: return emptyList()
        if (totalPages <= 1) return emptyList()
        val base = firstPageUrl.removeSuffix("&pagenumber=1")
        return (2..totalPages).map { page -> "$base&pagenumber=$page" }
    }

    override suspend fun parseDetail(
        html: String,
        url: String,
        district: String,
        timestamp: Long,
        rentFallback: RentFallback?,
        ref: AdRef?,
    ): ApartmentAd? =
        // Costs come structured from the mobile API for this source, so the LLM
        // fallback (used by kleinanzeigen for free-text-only listings) is unused here.
        // lat/lon/distanceMeters come from the search-list ref, not the expose response
        // itself (see SearchListParser's KDoc), so they're merged in here.
        ExposeParser.parse(html, url, district, timestamp)?.let { ad ->
            ad.copy(lat = ref?.lat, lon = ref?.lon, distanceMeters = ref?.distanceMeters)
        }
}
