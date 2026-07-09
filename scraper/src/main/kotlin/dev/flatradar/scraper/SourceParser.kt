package dev.flatradar.scraper

import dev.flatradar.shared.ApartmentAd

/**
 * Parses search-results and detail pages for one listing source (e.g.
 * "kleinanzeigen"). Each source registers one implementation in
 * [SourceParsers.all], keyed by [FeedConfig.source].
 *
 * `parseSearch` is non-suspend: a pure jsoup transformation with no I/O.
 * `parseDetail` is suspend: it may invoke the [RentFallback] LLM call.
 */
interface SourceParser {
    fun parseSearch(html: String): List<AdRef>

    suspend fun parseDetail(
        html: String,
        url: String,
        district: String,
        timestamp: Long,
        rentFallback: RentFallback? = null,
    ): ApartmentAd?
}

/**
 * Registry of every [SourceParser], keyed by [FeedConfig.source]. [get]
 * returns `null` for an unknown source; the runner logs and skips that feed
 * rather than crashing the whole run.
 */
object SourceParsers {
    val all: Map<String, SourceParser> = mapOf(
        "kleinanzeigen" to dev.flatradar.scraper.kleinanzeigen.KleinanzeigenParser,
    )

    fun get(source: String): SourceParser? = all[source]
}