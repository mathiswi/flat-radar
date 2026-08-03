package dev.flatradar.scraper.immoscout24

import dev.flatradar.scraper.SwapDetector
import dev.flatradar.shared.ApartmentAd
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses a `GET /expose/{id}` response body from ImmoScout24's mobile API into an
 * [ApartmentAd], or returns `null` for a Tauschwohnung / swap offer (or when the
 * response can't even be decoded, e.g. a block/challenge page).
 *
 * Confirmed response shape (live API, July 2026): a flat `sections` list, matched
 * by `type`:
 *   - `TITLE`                       -> `title` (the actual ad headline; `header.title`
 *                                      is always the generic literal "Beschreibung")
 *   - `TOP_ATTRIBUTES`              -> `attributes[].{label,text}`, e.g. "Zimmer" / "2,5",
 *                                      "Kaltmiete 14,76 €/m²" / "930 €", "Warmmiete" / "1.190 €"
 *                                      (may be "keine Angabe" - no structured rent at all)
 *   - `ATTRIBUTE_LIST` title="Hauptkriterien" -> Wohnfläche ca., Zimmer, Schlafzimmer,
 *                                      Badezimmer, Etage, Wohnungstyp, Bezugsfrei ab
 *   - `ATTRIBUTE_LIST` title="Kosten"        -> Kaltmiete (zzgl. Nebenkosten), Nebenkosten,
 *                                      Heizkosten, Gesamtmiete, Kaution oder Genossenschaftsanteile
 *   - `MAP`                         -> `addressLine2`, e.g. "22297 Alsterdorf, Hamburg"
 *
 * Rent/size/rooms resolution order: the "Kosten"/"Hauptkriterien" `ATTRIBUTE_LIST`
 * values first (more precise labels, e.g. "Wohnfläche ca."), falling back to
 * `TOP_ATTRIBUTES` (always present, but "Kaltmiete"'s label carries a dynamic
 * "€/m²" suffix so it's matched by prefix, not exact label).
 */
object ExposeParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun exposeUrl(id: String): String = "https://api.mobile.immobilienscout24.de/expose/$id"

    /** Canonical web URL - stored in [ApartmentAd.url] so links in notifications open the normal website. */
    fun webUrl(id: String): String = "https://www.immobilienscout24.de/expose/$id"

    /**
     * [url] is the URL the detail page was *fetched* from (the mobile-API expose
     * endpoint) - used only as a fallback to recover the id if `header.id` is ever
     * missing. [ApartmentAd.url] is always set to the canonical **web** URL derived
     * from the id, so links in notifications open the normal website, not the API.
     */
    fun parse(
        responseJson: String,
        url: String,
        district: String,
        timestamp: Long,
    ): ApartmentAd? {
        val response = decode(responseJson) ?: return null

        val title = response.sections.firstOrNull { it.type == "TITLE" }?.title?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        if (SwapDetector.isSwapByTitle(title)) return null

        val id = response.header?.id?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast("/expose/").substringBefore("?")

        val topAttrs: List<Pair<String, String>> = response.sections
            .firstOrNull { it.type == "TOP_ATTRIBUTES" }
            ?.attributes
            ?.map { (it.label ?: "") to (it.text ?: "") }
            ?: emptyList()

        val attrs: Map<String, String> = response.sections
            .filter { it.type == "ATTRIBUTE_LIST" && it.title in RELEVANT_ATTRIBUTE_LISTS }
            .flatMap { it.attributes }
            .filter { !it.label.isNullOrBlank() }
            .associate { it.label!!.removeSuffix(":") to (it.text ?: "") }

        val location = response.sections.firstOrNull { it.type == "MAP" }?.addressLine2?.trim() ?: ""

        val size = attrs[AttrKeys.WOHNFLAECHE_CA]?.let(ImmoscoutFormats::parseSize)
            ?: topAttr(topAttrs, AttrKeys.WOHNFLAECHE)?.let(ImmoscoutFormats::parseSize)
        val rooms = topAttr(topAttrs, AttrKeys.ZIMMER)?.let(ImmoscoutFormats::parseRooms)
        val bedrooms = attrs[AttrKeys.SCHLAFZIMMER]?.toIntOrNull()
        val bathrooms = attrs[AttrKeys.BADEZIMMER]?.toIntOrNull()
        val floor = attrs[AttrKeys.ETAGE]?.takeIf { it.isNotBlank() }
        val apartmentType = attrs[AttrKeys.WOHNUNGSTYP]?.takeIf { it.isNotBlank() }
        val availableFrom = attrs[AttrKeys.BEZUGSFREI_AB]?.takeIf { it.isNotBlank() }
            ?.let(ImmoscoutFormats::parseAvailableFrom)
        val deposit = attrs[AttrKeys.KAUTION]?.let(ImmoscoutFormats::parseEuros)

        val baseRent = attrs[AttrKeys.KALTMIETE_ZZGL]?.let(ImmoscoutFormats::parseEuros)
            ?: topAttr(topAttrs, AttrKeys.KALTMIETE)?.let(ImmoscoutFormats::parseEuros)
        val sideCosts = attrs[AttrKeys.NEBENKOSTEN]?.let(ImmoscoutFormats::parseEuros)
        val heatingCosts = attrs[AttrKeys.HEIZKOSTEN]?.let(ImmoscoutFormats::parseEuros)
        val totalRent = attrs[AttrKeys.GESAMTMIETE]?.let(ImmoscoutFormats::parseEuros)
            ?: topAttr(topAttrs, AttrKeys.WARMMIETE)?.let(ImmoscoutFormats::parseEuros)

        return ApartmentAd(
            id = id,
            title = title,
            size = size,
            rooms = rooms,
            bedrooms = bedrooms,
            bathrooms = bathrooms,
            floor = floor,
            apartmentType = apartmentType,
            availableFrom = availableFrom,
            deposit = deposit,
            baseRent = baseRent,
            sideCosts = sideCosts,
            heatingCosts = heatingCosts,
            totalRent = totalRent,
            location = location,
            url = webUrl(id),
            source = "immoscout24",
            district = district,
            timestamp = timestamp,
        )
    }

    /** `null` when [responseJson] isn't valid JSON in the expected shape (e.g. a block/challenge page). */
    fun decode(responseJson: String): ExposeResponse? =
        runCatching { json.decodeFromString(ExposeResponse.serializer(), responseJson) }.getOrNull()

    /** `TOP_ATTRIBUTES` labels can carry a dynamic suffix (e.g. "Kaltmiete 14,76 €/m²"), so match by prefix. */
    private fun topAttr(topAttrs: List<Pair<String, String>>, labelPrefix: String): String? =
        topAttrs.firstOrNull { it.first.startsWith(labelPrefix) }?.second

    private val RELEVANT_ATTRIBUTE_LISTS = setOf("Hauptkriterien", "Kosten")

    /**
     * German attribute labels emitted by ImmoScout24's mobile API. Centralised so a
     * typo in one place cannot drift from another (mirrors kleinanzeigen.DetailPageParser.AttrKeys).
     */
    private object AttrKeys {
        const val WOHNFLAECHE_CA = "Wohnfläche ca."
        const val WOHNFLAECHE = "Wohnfläche"
        const val ZIMMER = "Zimmer"
        const val SCHLAFZIMMER = "Schlafzimmer"
        const val BADEZIMMER = "Badezimmer"
        const val ETAGE = "Etage"
        const val WOHNUNGSTYP = "Wohnungstyp"
        const val BEZUGSFREI_AB = "Bezugsfrei ab"
        const val KALTMIETE = "Kaltmiete"
        const val KALTMIETE_ZZGL = "Kaltmiete (zzgl. Nebenkosten)"
        const val WARMMIETE = "Warmmiete"
        const val NEBENKOSTEN = "Nebenkosten"
        const val HEIZKOSTEN = "Heizkosten"
        const val GESAMTMIETE = "Gesamtmiete"
        const val KAUTION = "Kaution oder Genossenschaftsanteile"
    }
}

@Serializable
data class ExposeResponse(
    val header: ExposeHeader? = null,
    val sections: List<ExposeSection> = emptyList(),
)

@Serializable
data class ExposeHeader(
    val id: String? = null,
)

@Serializable
data class ExposeSection(
    val type: String? = null,
    val title: String? = null,
    val addressLine2: String? = null,
    val attributes: List<ExposeAttribute> = emptyList(),
)

@Serializable
data class ExposeAttribute(
    val type: String? = null,
    val label: String? = null,
    val text: String? = null,
)
