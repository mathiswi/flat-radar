package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.AdRef
import dev.flatradar.scraper.RentFallback
import dev.flatradar.scraper.SourceParser
import dev.flatradar.shared.ApartmentAd

/**
 * Single registration entry for the "kleinanzeigen" source.
 *
 * Coherence point for the source-specific filtering policy: applies the
 * Tauschwohnung filter on the search-page refs so no detail fetch is wasted
 * on swap offers. The underlying [SearchPageParser] / [DetailPageParser]
 * stay pure (no source-level filtering inline) and remain independently testable.
 */
object KleinanzeigenParser : SourceParser {

    override fun parseSearch(html: String): List<AdRef> =
        SearchPageParser.parse(html)
            .filterNot { ref ->
                SwapDetector.isSwapByTitle(ref.title) || SwapDetector.isSwapBySlug(ref.url)
            }

    override suspend fun parseDetail(
        html: String,
        url: String,
        district: String,
        timestamp: Long,
        rentFallback: RentFallback?,
    ): ApartmentAd? =
        DetailPageParser.parse(html, url, district, timestamp, rentFallback)
}