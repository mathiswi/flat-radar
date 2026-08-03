package dev.flatradar.scraper

/**
 * Lightweight reference to a listing discovered on a search-results page.
 * Carries just enough data to fetch the detail page and dedupe the ad.
 * Source-agnostic so it works for any future [SourceParser] implementation.
 *
 * [lat]/[lon]/[distanceMeters] are optional geo data some sources' search
 * results carry alongside a listing (e.g. immoscout24's mobile-API search-list
 * response) but their detail pages don't reliably repeat; `null` for sources
 * with no such data (e.g. kleinanzeigen's HTML search pages).
 */
data class AdRef(
    val adId: String,
    val url: String,
    val title: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceMeters: Int? = null,
)