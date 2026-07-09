package dev.flatradar.backend

import dev.flatradar.backend.db.ListingsTable
import dev.flatradar.shared.ApartmentAd
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.sql.DataSource

enum class InsertResult { NEW, EXISTING }

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
            it[this.firstSeen] = firstSeen
            it[this.lastSeen] = now
        }

        if (exists) InsertResult.EXISTING else InsertResult.NEW
    }

    fun findAll(): List<ApartmentAd> = transaction(db) {
        ListingsTable.selectAll().map { row ->
            ApartmentAd(
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
                timestamp = row[ListingsTable.firstSeen].toInstant().toEpochMilli()
            )
        }
    }
}