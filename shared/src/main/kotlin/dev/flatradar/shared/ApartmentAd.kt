package dev.flatradar.shared

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ApartmentAd(
    val id: String,
    val title: String,
    val size: Double?,
    val rooms: Double?,
    val bedrooms: Int? = null,
    val bathrooms: Int? = null,
    val floor: String? = null,
    val apartmentType: String? = null,
    val availableFrom: LocalDate? = null,
    val deposit: Int?,
    val baseRent: Int?,
    val sideCosts: Int?,
    val heatingCosts: Int?,
    val totalRent: Int?,
    val location: String,
    val url: String,
    val source: String,
    val district: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceMeters: Int? = null,
    val thumbnailUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val timestamp: Long,
    /** Epoch millis when the listing was detected as removed from its feed, else null.
     *  Set by the backend's delisting reconcile; the scraper never populates it. */
    val delistedAt: Long? = null,
)