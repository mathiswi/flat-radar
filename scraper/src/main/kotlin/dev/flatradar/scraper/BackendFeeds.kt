package dev.flatradar.scraper

import dev.flatradar.shared.FeedConfig

/**
 * Feeds sourced from the backend (`GET /api/v1/feeds`) - the production [Feeds]
 * implementation. The backend owns the `feeds` table; the dashboard edits it.
 *
 * A backend that's unreachable or errors throws (via [BackendClient]); [main]
 * treats a feed-load failure as fatal, so a broken backend fails the run loudly
 * rather than silently scraping nothing.
 */
class BackendFeeds(private val client: BackendClient) : Feeds {
    override suspend fun all(): List<FeedConfig> = client.getFeeds()
}
