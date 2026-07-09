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
    val firstSeen = timestampWithTimeZone("first_seen")
    val lastSeen = timestampWithTimeZone("last_seen")

    override val primaryKey = PrimaryKey(id)
}
