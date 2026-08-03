package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.AdRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses a `POST /search/list` response body from ImmoScout24's mobile API into
 * [AdRef]s, and exposes the pagination fields needed to fetch the remaining pages.
 *
 * Pure parse: returns every listing found, INCLUDING Tauschwohnung ("swap
 * apartment") ads. The caller ([ImmoscoutParser.parseSearch]) is responsible for
 * filtering swaps, mirroring the Kleinanzeigen split (see
 * `kleinanzeigen.SearchPageParser`).
 *
 * Confirmed response shape (live API, July 2026):
 *   `{ pageNumber, numberOfPages, resultListItems: [{ type, item: { id, title, address } }] }`
 * `type == "EXPOSE_RESULT"` is a real listing; other types (ads, groupings) are
 * skipped. `ignoreUnknownKeys` means the many fields we don't need (pictures,
 * shapes, reporting, ...) are simply dropped.
 *
 * `item.address` carries `lat`/`lon`/`distance` for listings with a precise
 * address, but only a `postcode` (no coordinates) for listings whose address is
 * "unvollständig" (incomplete) - so all three are optional, and absent on some
 * listings within the very same response. This geo data doesn't reliably survive
 * to the detail (`/expose/{id}`) response, so [ImmoscoutParser] carries it via
 * [AdRef] instead - see [AdRef.lat]/[AdRef.lon]/[AdRef.distanceMeters].
 */
object SearchListParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(responseJson: String): List<AdRef> {
        val response = decode(responseJson) ?: return emptyList()
        return response.resultListItems
            .filter { it.type == "EXPOSE_RESULT" }
            .mapNotNull { it.item }
            .mapNotNull { item ->
                val id = item.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = item.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                AdRef(
                    adId = id,
                    url = ExposeParser.exposeUrl(id),
                    title = title,
                    lat = item.address?.lat,
                    lon = item.address?.lon,
                    distanceMeters = item.address?.distance?.let(ImmoscoutFormats::parseDistanceMeters),
                )
            }
    }

    /** `null` when [responseJson] isn't valid JSON in the expected shape (e.g. a block/challenge page). */
    fun decode(responseJson: String): SearchListResponse? =
        runCatching { json.decodeFromString(SearchListResponse.serializer(), responseJson) }.getOrNull()
}

@Serializable
data class SearchListResponse(
    val pageNumber: Int? = null,
    val numberOfPages: Int? = null,
    val resultListItems: List<ResultListItem> = emptyList(),
)

@Serializable
data class ResultListItem(
    val type: String? = null,
    val item: ExposeResultItem? = null,
)

@Serializable
data class ExposeResultItem(
    val id: String? = null,
    val title: String? = null,
    val address: ExposeResultAddress? = null,
)

@Serializable
data class ExposeResultAddress(
    val lat: Double? = null,
    val lon: Double? = null,
    /** German-formatted, e.g. "710 m" or "1,6 km" - see [ImmoscoutFormats.parseDistanceMeters]. */
    val distance: String? = null,
)
