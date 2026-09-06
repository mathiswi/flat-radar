package dev.flatradar.backend.db

import org.jetbrains.exposed.sql.Table

/**
 * Configured search feeds the scraper polls (migration V8). Replaces the
 * VPS-local `feeds.json`: DB-owned, edited from the dashboard, served to the
 * scraper over `GET /api/v1/feeds`.
 */
object FeedsTable : Table("feeds") {
    val id = text("id")
    val displayName = text("display_name")
    val url = text("url")
    val district = text("district")
    // Named feedSource, not source: `source` collides with a ColumnSet member
    // (same reason ListingsTable uses listingSource). Column stays "source".
    val feedSource = text("source")
    val enabled = bool("enabled").default(true)

    override val primaryKey = PrimaryKey(id)
}
