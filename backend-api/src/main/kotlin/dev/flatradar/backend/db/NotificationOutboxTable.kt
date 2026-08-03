package dev.flatradar.backend.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

/**
 * Transactional outbox for new-listing notifications. A row is inserted in the
 * same transaction as the [ListingsTable] upsert (see `ListingRepository.upsert`)
 * whenever the listing is genuinely new, so a Discord notification can never be
 * silently lost even if the notifier is down when the listing is ingested.
 *
 * [OutboxWorker] polls unsent rows (`sentAt IS NULL`), so `attempts`/`lastError`
 * exist purely for observability/dead-lettering, not for query correctness.
 */
object NotificationOutboxTable : Table("notification_outbox") {
    val id = long("id").autoIncrement()
    val listingId = text("listing_id").references(ListingsTable.id)
    val createdAt = timestampWithTimeZone("created_at")
    val sentAt = timestampWithTimeZone("sent_at").nullable()
    val attempts = integer("attempts").default(0)
    val lastError = text("last_error").nullable()

    override val primaryKey = PrimaryKey(id)
}
