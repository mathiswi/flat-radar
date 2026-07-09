package dev.flatradar.backend

import dev.flatradar.backend.db.ListingsTable
import dev.flatradar.shared.ApartmentAd
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.batchUpsert
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

    fun upsertBatch(ads: List<ApartmentAd>): Int = transaction(db) {
        if (ads.isEmpty()) return@transaction 0
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        ListingsTable.batchUpsert(
            data = ads,
            onUpdate = { it[ListingsTable.lastSeen] = OffsetDateTime.now(ZoneOffset.UTC) },
            onUpdateExclude = listOf(ListingsTable.firstSeen),
        ) { ad ->
            val firstSeen = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ad.timestamp), ZoneOffset.UTC)
            this[ListingsTable.id] = ad.id
            this[ListingsTable.title] = ad.title
            this[ListingsTable.size] = ad.size
            this[ListingsTable.rooms] = ad.rooms
            this[ListingsTable.bedrooms] = ad.bedrooms
            this[ListingsTable.bathrooms] = ad.bathrooms
            this[ListingsTable.floor] = ad.floor
            this[ListingsTable.apartmentType] = ad.apartmentType
            this[ListingsTable.availableFrom] = ad.availableFrom
            this[ListingsTable.deposit] = ad.deposit
            this[ListingsTable.baseRent] = ad.baseRent
            this[ListingsTable.sideCosts] = ad.sideCosts
            this[ListingsTable.heatingCosts] = ad.heatingCosts
            this[ListingsTable.totalRent] = ad.totalRent
            this[ListingsTable.location] = ad.location
            this[ListingsTable.url] = ad.url
            this[ListingsTable.listingSource] = ad.source
            this[ListingsTable.district] = ad.district
            this[ListingsTable.firstSeen] = firstSeen
            this[ListingsTable.lastSeen] = now
        }
        ads.size
    }

    fun findAll(): List<ApartmentAd> = transaction(db) {
        ListingsTable.selectAll().map { row ->
            val firstSeenOdt = row[ListingsTable.firstSeen]
            ApartmentAd(
                id = row[ListingsTable.id] as String,
                title = row[ListingsTable.title] as String,
                size = row[ListingsTable.size] as Double?,
                rooms = row[ListingsTable.rooms] as Double?,
                bedrooms = row[ListingsTable.bedrooms] as Int?,
                bathrooms = row[ListingsTable.bathrooms] as Int?,
                floor = row[ListingsTable.floor] as String?,
                apartmentType = row[ListingsTable.apartmentType] as String?,
                availableFrom = row[ListingsTable.availableFrom] as kotlinx.datetime.LocalDate?,
                deposit = row[ListingsTable.deposit] as Int?,
                baseRent = row[ListingsTable.baseRent] as Int?,
                sideCosts = row[ListingsTable.sideCosts] as Int?,
                heatingCosts = row[ListingsTable.heatingCosts] as Int?,
                totalRent = row[ListingsTable.totalRent] as Int?,
                location = row[ListingsTable.location] as String,
                url = row[ListingsTable.url] as String,
                source = row[ListingsTable.listingSource] as String,
                district = row[ListingsTable.district] as String?,
                timestamp = firstSeenOdt.toInstant().toEpochMilli()
            )
        }
    }
}