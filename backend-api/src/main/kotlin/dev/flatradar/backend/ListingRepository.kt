package dev.flatradar.backend

import dev.flatradar.backend.db.ListingsTable
import dev.flatradar.backend.db.NotificationOutboxTable
import dev.flatradar.shared.ApartmentAd
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

enum class InsertResult { NEW, EXISTING }

/** One undelivered (or previously failed) notification, joined with the listing it's about. */
data class OutboxItem(val outboxId: Long, val ad: ApartmentAd, val attempts: Int)

class ListingRepository(dataSource: DataSource) {
    private val db = Database.connect(dataSource)

    fun existingIds(ids: List<String>): Set<String> = transaction(db) {
        if (ids.isEmpty()) return@transaction emptySet()
        ListingsTable.select(ListingsTable.id)
            .where { ListingsTable.id inList ids }
            .map { it[ListingsTable.id] }
            .toSet()
    }

    fun upsert(ad: ApartmentAd): InsertResult = transaction(db) {
        val exists = ListingsTable.select(ListingsTable.id)
            .where { ListingsTable.id eq ad.id }
            .limit(1)
            .any()

        val firstSeen = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ad.timestamp), ZoneOffset.UTC)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        ListingsTable.upsert(
            onUpdate = { it[ListingsTable.lastSeen] = OffsetDateTime.now(ZoneOffset.UTC) },
            onUpdateExclude = listOf(ListingsTable.firstSeen),
        ) {
            it[id] = ad.id
            it[title] = ad.title
            it[size] = ad.size
            it[rooms] = ad.rooms
            it[bedrooms] = ad.bedrooms
            it[bathrooms] = ad.bathrooms
            it[floor] = ad.floor
            it[apartmentType] = ad.apartmentType
            it[availableFrom] = ad.availableFrom
            it[deposit] = ad.deposit
            it[baseRent] = ad.baseRent
            it[sideCosts] = ad.sideCosts
            it[heatingCosts] = ad.heatingCosts
            it[totalRent] = ad.totalRent
            it[location] = ad.location
            it[url] = ad.url
            it[listingSource] = ad.source
            it[district] = ad.district
            it[lat] = ad.lat
            it[lon] = ad.lon
            it[distanceMeters] = ad.distanceMeters
            it[thumbnailUrl] = ad.thumbnailUrl
            it[imageUrls] = imageUrlsJson.encodeToString(ListSerializer(serializer<String>()), ad.imageUrls)
            it[this.firstSeen] = firstSeen
            it[this.lastSeen] = now
        }

        // Same transaction as the upsert above: either both the listing and its
        // outbox row land, or neither does - a notification can never be owed
        // without a durable record of that fact.
        if (!exists) {
            NotificationOutboxTable.insert {
                it[listingId] = ad.id
                it[createdAt] = now
            }
        }

        if (exists) InsertResult.EXISTING else InsertResult.NEW
    }

    fun findAll(): List<ApartmentAd> = transaction(db) {
        ListingsTable.selectAll().map(::rowToAd)
    }

    fun countAll(): Int = transaction(db) {
        ListingsTable.selectAll().count().toInt()
    }

    /** Rows still in the outbox that the worker will keep retrying (attempts < [maxAttempts]). */
    fun countPendingOutbox(maxAttempts: Int): Int = transaction(db) {
        NotificationOutboxTable.selectAll()
            .andWhere { NotificationOutboxTable.sentAt.isNull() }
            .andWhere { NotificationOutboxTable.attempts less maxAttempts }
            .count()
            .toInt()
    }

    /** Rows in the outbox that have exhausted retries (attempts >= [maxAttempts]) and need manual attention. */
    fun countDeadLetteredOutbox(maxAttempts: Int): Int = transaction(db) {
        NotificationOutboxTable.selectAll()
            .andWhere { NotificationOutboxTable.sentAt.isNull() }
            .andWhere { NotificationOutboxTable.attempts greaterEq maxAttempts }
            .count()
            .toInt()
    }

    /** Most recent [ListingsTable.lastSeen] across all listings, or null if the table is empty. */
    fun lastScrapeTimestamp(): OffsetDateTime? = transaction(db) {
        val maxCol = ListingsTable.lastSeen.max()
        ListingsTable.select(maxCol).firstOrNull()?.get(maxCol)
    }

    /**
     * Unsent (or previously-failed-but-under-the-retry-cap) outbox rows, oldest
     * first, joined with the listing data [Notifier] needs to build a message.
     *
     * No `SELECT ... FOR UPDATE`: today there is exactly one [OutboxWorker]
     * polling in-process. If `backend-api` ever runs multiple replicas, add
     * `FOR UPDATE SKIP LOCKED` here so pollers don't double-send.
     */
    fun fetchUnsentOutbox(limit: Int, maxAttempts: Int): List<OutboxItem> = transaction(db) {
        (NotificationOutboxTable innerJoin ListingsTable)
            .selectAll()
            .andWhere { NotificationOutboxTable.sentAt.isNull() }
            .andWhere { NotificationOutboxTable.attempts less maxAttempts }
            .orderBy(NotificationOutboxTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row -> OutboxItem(row[NotificationOutboxTable.id], rowToAd(row), row[NotificationOutboxTable.attempts]) }
    }

    fun markOutboxSent(outboxId: Long): Unit = transaction(db) {
        NotificationOutboxTable.update({ NotificationOutboxTable.id eq outboxId }) {
            it[sentAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    /** [newAttemptCount] is computed by the caller (typically `previousAttempts + 1`) to avoid a read-modify-write race. */
    fun markOutboxFailed(outboxId: Long, newAttemptCount: Int, error: String): Unit = transaction(db) {
        NotificationOutboxTable.update({ NotificationOutboxTable.id eq outboxId }) {
            it[attempts] = newAttemptCount
            it[lastError] = error.take(MAX_ERROR_LENGTH)
        }
    }

    private fun rowToAd(row: ResultRow): ApartmentAd = ApartmentAd(
        id = row[ListingsTable.id],
        title = row[ListingsTable.title],
        size = row[ListingsTable.size],
        rooms = row[ListingsTable.rooms],
        bedrooms = row[ListingsTable.bedrooms],
        bathrooms = row[ListingsTable.bathrooms],
        floor = row[ListingsTable.floor],
        apartmentType = row[ListingsTable.apartmentType],
        availableFrom = row[ListingsTable.availableFrom],
        deposit = row[ListingsTable.deposit],
        baseRent = row[ListingsTable.baseRent],
        sideCosts = row[ListingsTable.sideCosts],
        heatingCosts = row[ListingsTable.heatingCosts],
        totalRent = row[ListingsTable.totalRent],
        location = row[ListingsTable.location],
        url = row[ListingsTable.url],
        source = row[ListingsTable.listingSource],
        district = row[ListingsTable.district],
        lat = row[ListingsTable.lat],
        lon = row[ListingsTable.lon],
        distanceMeters = row[ListingsTable.distanceMeters],
        thumbnailUrl = row[ListingsTable.thumbnailUrl],
        imageUrls = imageUrlsJson.decodeFromString(ListSerializer(serializer<String>()), row[ListingsTable.imageUrls]),
        timestamp = row[ListingsTable.firstSeen].toInstant().toEpochMilli()
    )

    private companion object {
        const val MAX_ERROR_LENGTH = 2000
        private val imageUrlsJson = Json { ignoreUnknownKeys = true }
    }
}