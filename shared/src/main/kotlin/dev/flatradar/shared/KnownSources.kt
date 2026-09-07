package dev.flatradar.shared

/**
 * The single source of truth for which listing-source names are valid, shared by
 * every consumer so the list can't drift:
 *  - the scraper's `SourceParsers` registry asserts its keys match [all],
 *  - the backend's feed validation rejects any [FeedConfig.source] not in [all],
 *  - the dashboard fetches [all] over `GET /api/v1/sources` for its dropdown.
 *
 * A `List` (not a `Set`) so the dashboard dropdown has a stable order; callers
 * that need set semantics use [all]`.toSet()`.
 */
object KnownSources {
    val all: List<String> = listOf("kleinanzeigen", "immoscout24")
}
