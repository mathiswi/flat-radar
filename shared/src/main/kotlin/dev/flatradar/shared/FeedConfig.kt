package dev.flatradar.shared

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * One configured search feed the scraper polls. Owned by the backend (a `feeds`
 * DB table) and served over `GET /api/v1/feeds`; the scraper fetches these
 * instead of reading a local `feeds.json`.
 *
 * Shared between backend and scraper (like [ApartmentAd]) so both agree on the
 * wire shape. New fields must keep a default so an older scraper binary decoding
 * a newer backend's JSON never breaks.
 */
@Serializable
data class FeedConfig(
    val id: String,
    val displayName: String,
    val url: String,
    val district: String,
    // @EncodeDefault: these two carry defaults, and kotlinx-serialization omits
    // fields equal to their default. The scraper re-applies them on decode, but a
    // JSON/TS consumer (the dashboard) would see `undefined` and read enabled as
    // false / source as blank - so force them to always serialize.
    @EncodeDefault val source: String = "kleinanzeigen",
    @EncodeDefault val enabled: Boolean = true,
)
