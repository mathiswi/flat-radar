package dev.flatradar.scraper

import dev.flatradar.shared.ApartmentAd

/**
 * Parses search-results and detail pages for one listing source (e.g.
 * "kleinanzeigen"). Each source registers one implementation in
 * [SourceParsers.all], keyed by [FeedConfig.source].
 *
 * `parseSearch` is non-suspend: a pure transformation (jsoup or JSON) with no I/O.
 */
interface SourceParser {

    /**
     * Translates [feedUrl] (the URL configured in `feeds.json`) into the URL that
     * should actually be fetched for the first search-results page. Identity for
     * sources where the feed URL is already fetchable as-is (e.g. kleinanzeigen);
     * immoscout24 overrides this to translate a web search URL into a mobile-API
     * request URL (see `immoscout24.UrlTranslator`).
     */
    fun searchUrl(feedUrl: String): String = feedUrl

    fun parseSearch(html: String): List<AdRef>

    /**
     * Additional search-result page URLs beyond the first, derived from the first
     * page's request URL and its raw response body. Default: single page (most
     * sources return every result on one page). A paginated source overrides this
     * to read a total-page-count field from its own response and build the
     * remaining page URLs; [Main] fetches them through the existing rate limiter
     * and feeds each response back through [parseSearch].
     */
    fun nextSearchPageUrls(firstPageUrl: String, firstPageResponse: String): List<String> = emptyList()

    /**
     * [ref] is the [AdRef] this detail page was fetched for - `null` by default so
     * existing/simple call sites don't need it. immoscout24 uses it to carry
     * `lat`/`lon`/`distanceMeters` from the search-list response into the final
     * [ApartmentAd], since the detail response doesn't reliably repeat that data.
     */
    suspend fun parseDetail(
        html: String,
        url: String,
        district: String,
        timestamp: Long,
        ref: AdRef? = null,
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
        "immoscout24" to dev.flatradar.scraper.immoscout24.ImmoscoutParser,
    )

    fun get(source: String): SourceParser? = all[source]
}