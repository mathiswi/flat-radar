package dev.flatradar.backend

import dev.flatradar.backend.db.FeedsTable
import dev.flatradar.shared.FeedConfig
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import javax.sql.DataSource

/** CRUD for the `feeds` table. Mirrors [ListingRepository]'s transaction-per-call style. */
class FeedRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    fun all(): List<FeedConfig> = transaction(db) {
        FeedsTable.selectAll().map(::rowToFeed)
    }

    /**
     * Inserts the feed. Returns true if created, false if a feed with this id
     * already exists (in which case nothing is written) - so a create request
     * never silently clobbers an existing feed.
     */
    fun create(feed: FeedConfig): Boolean = transaction(db) {
        val exists = FeedsTable.select(FeedsTable.id)
            .where { FeedsTable.id eq feed.id }
            .limit(1)
            .any()
        if (exists) return@transaction false

        FeedsTable.insert {
            it[id] = feed.id
            it[displayName] = feed.displayName
            it[url] = feed.url
            it[district] = feed.district
            it[feedSource] = feed.source
            it[enabled] = feed.enabled
        }
        true
    }

    /**
     * Updates the mutable fields of an existing feed (the id is the key and is
     * never changed). Returns true if a row was updated, false if no feed had
     * this id - so an update to a non-existent id never silently creates one.
     */
    fun update(feed: FeedConfig): Boolean = transaction(db) {
        FeedsTable.update({ FeedsTable.id eq feed.id }) {
            it[displayName] = feed.displayName
            it[url] = feed.url
            it[district] = feed.district
            it[feedSource] = feed.source
            it[enabled] = feed.enabled
        } > 0
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
