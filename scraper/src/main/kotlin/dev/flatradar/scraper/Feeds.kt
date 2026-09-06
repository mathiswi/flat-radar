package dev.flatradar.scraper

import dev.flatradar.shared.FeedConfig

/**
 * Source of [FeedConfig]s for the scraper's polling loop. The sole production
 * implementation is [BackendFeeds] (feeds are DB-owned and served over
 * `GET /api/v1/feeds`); the interface is `suspend` so the fetch can do I/O.
 *
 * A backend that's unreachable or errors throws - [main] treats a feed-load
 * failure as fatal, so a broken backend fails the run loudly rather than
 * silently scraping nothing.
 */
interface Feeds {
    suspend fun all(): List<FeedConfig>
}
