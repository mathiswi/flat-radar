package dev.flatradar.backend.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import java.time.OffsetDateTime

object ListingsTable : Table("listings") {
    val id = text("id")
    val title = text("title")
    val size = double("size").nullable()
    val rooms = double("rooms").nullable()
    val bedrooms = integer("bedrooms").nullable()
    val bathrooms = integer("bathrooms").nullable()
    val floor = text("floor").nullable()
    val apartmentType = text("apartment_type").nullable()
    val availableFrom = date("available_from").nullable()
    val deposit = integer("deposit").nullable()
    val baseRent = integer("base_rent").nullable()
    val sideCosts = integer("side_costs").nullable()
    val heatingCosts = integer("heating_costs").nullable()
    val totalRent = integer("total_rent").nullable()
    val location = text("location")
    val url = text("url")
    val listingSource = text("source")
    val district = text("district").nullable()
    val lat = double("lat").nullable()
    val lon = double("lon").nullable()
    val distanceMeters = integer("distance_meters").nullable()
    val thumbnailUrl = text("thumbnail_url").nullable()
    val imageUrls = text("image_urls")
    val firstSeen = timestampWithTimeZone("first_seen")
    val lastSeen = timestampWithTimeZone("last_seen")

    // Delisting detection (V7). feedId is backfilled by the "seen" reconcile, missedRuns
    // counts consecutive successful runs of that feed the listing was absent from, and
    // delistedAt is set once missedRuns crosses the threshold (cleared if it reappears).
    val feedId = text("feed_id").nullable()
    val missedRuns = integer("missed_runs").default(0)
    val delistedAt = timestampWithTimeZone("delisted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
