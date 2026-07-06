package dev.flatradar.scraper

/**
 * Lightweight reference to a listing discovered on a search-results page.
 * Carries just enough data to fetch the detail page and dedupe the ad.
 * Source-agnostic so it works for any future [SourceParser] implementation.
 */
data class AdRef(
    val adId: String,
    val url: String,
    val title: String,
    val district: String,
)