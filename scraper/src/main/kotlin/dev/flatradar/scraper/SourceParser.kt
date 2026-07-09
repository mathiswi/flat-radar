package dev.flatradar.scraper

import dev.flatradar.shared.ApartmentAd

/**
 * Parses search-results and detail pages for one listing source
 * (e.g. "kleinanzeigen", a future "imoscout", ...).
 *
 * Each source ships one [SourceParser] implementation and registers it in
 * [SourceParsers.all]. The runner then handles every feed agnostically via
 * [SourceParsers.get], keyed by [FeedConfig.source]. Adding a new source is a
 * new implementation class plus one line in [SourceParsers.all].
 *
 * `parseSearch` is non-suspend because it is a pure jsoup transformation with no
 * I/O. `parseDetail` is suspend because it may invoke the [RentFallback] LLM call.
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
 * Registry of every [SourceParser] the scraper knows about, keyed by the
 * [FeedConfig.source] string. The runner looks up a feed's parser with
 * [get]; an unknown source returns `null` and the runner logs + skips
 * that feed without crashing the whole loop.
 *
 * Future sources: add the implementation class plus a `"source-key" to Parser`
 * entry here. Config in feeds.json supplies the same key.
 */
object SourceParsers {
    val all: Map<String, SourceParser> = mapOf(
        "kleinanzeigen" to dev.flatradar.scraper.kleinanzeigen.KleinanzeigenParser,
    )

    fun get(source: String): SourceParser? = all[source]
}