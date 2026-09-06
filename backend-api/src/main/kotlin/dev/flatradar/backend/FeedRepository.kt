package dev.flatradar.backend

import dev.flatradar.backend.db.FeedsTable
import dev.flatradar.shared.FeedConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import javax.sql.DataSource

/** CRUD for the `feeds` table. Mirrors [ListingRepository]'s transaction-per-call style. */
class FeedRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    fun all(): List<FeedConfig> = transaction(db) {
        FeedsTable.selectAll().map(::rowToFeed)
    }

    fun count(): Int = transaction(db) {
        FeedsTable.selectAll().count().toInt()
    }

    /** Inserts or replaces the feed. Returns true if it was newly created (no prior row with this id). */
    fun upsert(feed: FeedConfig): Boolean = transaction(db) {
        val existed = FeedsTable.select(FeedsTable.id)
            .where { FeedsTable.id eq feed.id }
            .limit(1)
            .any()

        FeedsTable.upsert {
            it[id] = feed.id
            it[displayName] = feed.displayName
            it[url] = feed.url
            it[district] = feed.district
            it[feedSource] = feed.source
            it[enabled] = feed.enabled
        }

        !existed
    }

    /** Returns true if a row was deleted, false if no feed had this id. */
    fun delete(id: String): Boolean = transaction(db) {
        FeedsTable.deleteWhere { FeedsTable.id eq id } > 0
    }

    private fun rowToFeed(row: ResultRow): FeedConfig = FeedConfig(
        id = row[FeedsTable.id],
        displayName = row[FeedsTable.displayName],
        url = row[FeedsTable.url],
        district = row[FeedsTable.district],
        source = row[FeedsTable.feedSource],
        enabled = row[FeedsTable.enabled],
    )
}
