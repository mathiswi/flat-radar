package dev.flatradar.scraper.immoscout24

import java.net.URI
import java.net.URLDecoder

/**
 * Translates an ImmoScout24 **web** search URL (the kind a user copies out of their
 * browser after configuring a search) into a request URL for the unauthenticated
 * **mobile-app** search API (`api.mobile.immobilienscout24.de/search/list`).
 *
 * This is a scoped-down Kotlin port of the fredy project's reverse-engineered
 * translator (`lib/services/immoscout/immoscout-web-translator.js`), limited to what
 * this scraper needs: `radius` and `region` **rental** searches. Buy searches, `shape`
 * (polygon) searches, and the SEO "wohnung-bis-N-euro-warm" path variant are out of
 * scope and throw a clear [IllegalArgumentException] rather than silently mistranslating.
 *
 * Web URL shapes handled:
 *   - Radius:  `/Suche/radius/wohnung-mieten?...&geocoordinates=LAT;LON;RADIUS_KM&...`
 *   - Region:  `/Suche/de/<state>/<city>[/<district>]/wohnung-mieten?...`
 *
 * Query parameter exact order in the output has no functional meaning to the API
 * (it's an unordered map of query params); it's kept deterministic here purely for
 * readable/diffable fixtures and tests.
 */
object UrlTranslator {

    private const val MOBILE_SEARCH_LIST_URL = "https://api.mobile.immobilienscout24.de/search/list"

    /** Web path's last segment -> mobile `realestatetype`. Scoped to rentals. */
    private val REAL_ESTATE_TYPES = mapOf(
        "wohnung-mieten" to "apartmentrent",
        "haus-mieten" to "houserent",
    )

    /**
     * Web query param -> mobile query param, in the order they're emitted. A
     * straight rename table (fredy's `PARAM_NAME_MAP`), scoped to params relevant
     * for rental searches. Every one of these happens to keep its web name as-is
     * on the mobile side.
     */
    private val PASSTHROUGH_PARAMS = listOf(
        "pricetype",
        "numberofrooms",
        "price",
        "livingspace",
        "exclusioncriteria",
        "petsallowedtypes",
        "constructionyear",
        "energyefficiencyclasses",
        "floor",
        "newbuilding",
        "fulltext",
        "haspromotion",
        "heatingtypes",
        "sorting",
    )

    /**
     * The web UI sends "swapflat"; the mobile API only understands "swap_flat" (per
     * fredy's reverse-engineering notes). An unmapped value isn't ignored - the API
     * silently returns zero results for the whole search - so this rename matters.
     */
    private val EXCLUSION_CRITERIA_MAP = mapOf("swapflat" to "swap_flat")

    fun webToMobile(webUrl: String): String {
        val uri = try {
            URI(webUrl)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid ImmoScout24 URL: $webUrl", e)
        }

        val segments = uri.path.split("/").filter { it.isNotEmpty() }
        require(segments.firstOrNull() == "Suche") {
            "Unexpected ImmoScout24 URL path '${uri.path}' - expected it to start with \"/Suche\""
        }

        val realTypeKey = segments.lastOrNull().orEmpty()
        val realEstateType = REAL_ESTATE_TYPES[realTypeKey]
            ?: throw IllegalArgumentException(
                "Unsupported/unmapped ImmoScout24 real estate type '$realTypeKey' (only rentals are supported)"
            )

        val isRadius = segments.contains("radius")
        val webParams = parseQuery(uri.query)

        val mobileParams = linkedMapOf(
            "searchType" to if (isRadius) "radius" else "region",
            "realestatetype" to realEstateType,
        )
        if (!isRadius) {
            // e.g. segments = [Suche, de, hamburg, hamburg, wohnung-mieten] -> "/de/hamburg/hamburg"
            mobileParams["geocodes"] = "/" + segments.subList(1, segments.size - 1).joinToString("/")
        }
        webParams["geocoordinates"]?.let { mobileParams["geocoordinates"] = it }

        for (key in PASSTHROUGH_PARAMS) {
            val value = webParams[key] ?: continue
            mobileParams[key] = if (key == "exclusioncriteria") {
                value.split(",").joinToString(",") { item -> EXCLUSION_CRITERIA_MAP[item.lowercase()] ?: item }
            } else {
                value
            }
        }

        val queryPart = mobileParams.entries.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "$MOBILE_SEARCH_LIST_URL?$queryPart"
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val separatorIndex = pair.indexOf('=')
            if (separatorIndex < 0) return@mapNotNull null
            val key = URLDecoder.decode(pair.substring(0, separatorIndex), "UTF-8")
            val value = URLDecoder.decode(pair.substring(separatorIndex + 1), "UTF-8")
            key to value
        }.toMap()
    }

    /**
     * Percent-encodes [value] for use in a query string, leaving characters that
     * appear in our own parameter values (digits, `. - ; , /`) unescaped so URLs
     * stay human-readable - matching the shape ImmoScout's mobile API expects
     * (`geocoordinates=53.59425;10.04675;2.0`, not `%3B`-escaped).
     */
    private fun encode(value: String): String = buildString {
        for (c in value) {
            if ((c.isLetterOrDigit() && c.code < 128) || c in SAFE_CHARS) append(c) else append("%%%02X".format(c.code))
        }
    }

    private val SAFE_CHARS = setOf('-', '.', '_', '~', ';', ',', '/', ':')
}
