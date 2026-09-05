package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.shared.ApartmentAd
import org.jsoup.Jsoup

object DetailPageParser {

    /**
     * Parses a Kleinanzeigen detail page into an [ApartmentAd], or returns `null`
     * when the ad is a Tauschwohnung / swap offer (or when essential fields are missing).
     *
     * Confirmed markup (real page, July 2026):
     *   - Title:                 #viewad-title                              .text()
     *   - Price headline:        #viewad-price                              .text()        ("1.100 €" | "VB" | "Auf Anfrage")
     *   - Attributes list:       #viewad-details .addetailslist--detail
     *       - label  = li.ownText().trim()
     *       - value  = li.selectFirst("span.addetailslist--detail--value").text().trim()
     *   - Location:              #viewad-locality                           .text()
     *   - Ad id (hidden):        input[name=adId]                          .attr("value")
     *   - Canonical URL:         link[rel=canonical]                        .attr("href")
     *   - Seller/account name:   #viewad-contact .userprofile-vip           .text()
     *   - Description:           #viewad-description-text                   .text()
     *
     * Price strategy (see [resolveRents]):
     *   1. Explicitly-named attribute rows (Kaltmiete / Warmmiete / Nebenkosten / Heizkosten).
     *   2. #viewad-price headline fills totalRent only as last resort (its meaning is
     *      ambiguous - Kalt vs Warm vs VB). "Auf Anfrage" -> stays null.
     *
     * [timestamp] is supplied by the caller so the parser is a pure function of its
     * inputs (no System.currentTimeMillis inside, no surprise in tests).
     */
    fun parse(
        html: String,
        url: String,
        district: String,
        timestamp: Long,
    ): ApartmentAd? {
        val doc = Jsoup.parse(html)

        // --- Tausch filter (return null on ANY strong signal) ---
        val title = doc.selectFirst(Selectors.TITLE)?.text()?.trim()
        val canonical = doc.selectFirst(Selectors.CANONICAL)?.attr("href")
        val seller = doc.selectFirst(Selectors.SELLER)?.text()
        val description = doc.selectFirst(Selectors.DESCRIPTION)?.text()
        if (SwapDetector.isSwap(title, canonical, seller, description)) return null

        // --- Identity ---
        val adId = doc.selectFirst(Selectors.AD_ID_INPUT)?.attr("value")?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast("/").substringBefore("-")
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null

        // --- Attribute map ---
        val attrs: Map<String, String> = doc
            .select(Selectors.DETAIL_LIST)
            .associate { li ->
                val label = li.ownText().trim()
                val value = li.selectFirst(Selectors.DETAIL_VALUE)?.text()?.trim() ?: ""
                label to value
            }

        val headlinePrice = doc.selectFirst(Selectors.PRICE_HEADLINE)?.text()?.trim()?.let { KleinanzeigenFormats.parseEuros(it) }
        val rents = resolveRents(attrs, headlinePrice)

        // --- Other attributes ---
        val size = attrs[AttrKeys.WOHNFLAECHE]?.let { KleinanzeigenFormats.parseSize(it) }
        val rooms = attrs[AttrKeys.ZIMMER]?.let { KleinanzeigenFormats.parseRooms(it) }
        val bedrooms = attrs[AttrKeys.SCHLAFZIMMER]?.toIntOrNull()
        val bathrooms = attrs[AttrKeys.BADEZIMMER]?.toIntOrNull()
        val floor = attrs[AttrKeys.ETAGE]?.takeIf { it.isNotBlank() }
        val apartmentType = attrs[AttrKeys.WOHNUNGSTYP]?.takeIf { it.isNotBlank() }
        val deposit = listOf(AttrKeys.KAUTION_GENOSS, AttrKeys.KAUTION).firstNotNullOfOrNull { attrs[it] }
            ?.let { KleinanzeigenFormats.parseEuros(it) }
        val availableFrom = attrs[AttrKeys.VERFUEGBAR_AB]?.takeIf { it.isNotBlank() }
            ?.let { KleinanzeigenFormats.parseAvailableFrom(it) }

        // --- Location ---
        val location = doc.selectFirst(Selectors.LOCALITY)?.text()?.trim() ?: ""

        // --- Images ---
        val thumbnailUrl = doc.selectFirst(Selectors.OG_IMAGE)
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
        val imageUrls = doc.select(Selectors.GALLERY_IMAGE)
            .mapNotNull { el -> el.attr("data-imgsrc").takeIf { it.isNotBlank() } }
            .distinct()

        return ApartmentAd(
            id = adId,
            title = safeTitle,
            size = size,
            rooms = rooms,
            bedrooms = bedrooms,
            bathrooms = bathrooms,
            floor = floor,
            apartmentType = apartmentType,
            availableFrom = availableFrom,
            deposit = deposit,
            baseRent = rents.baseRent,
            sideCosts = rents.sideCosts,
            heatingCosts = rents.heatingCosts,
            totalRent = rents.totalRent,
            location = location,
            url = url,
            source = "kleinanzeigen",
            district = district,
            thumbnailUrl = thumbnailUrl,
            imageUrls = imageUrls,
            timestamp = timestamp
        )
    }

    private data class Rents(
        val baseRent: Int?, val sideCosts: Int?, val heatingCosts: Int?, val totalRent: Int?
    )

    /**
     * Resolution order for the four rent slots:
     *   1. Explicit structured attrs (Kaltmiete / Warmmiete / Nebenkosten / Heizkosten).
     *   2. Headline price (#viewad-price) fills totalRent only as last resort,
     *      because its meaning is ambiguous (Kaltmiete vs Warmmiete vs VB).
     */
    private fun resolveRents(
        attrs: Map<String, String>,
        headlinePrice: Int?,
    ): Rents {
        val base = attrs[AttrKeys.KALTMIETE]?.let { KleinanzeigenFormats.parseEuros(it) }
        val total = attrs[AttrKeys.WARMMIETE]?.let { KleinanzeigenFormats.parseEuros(it) }
        val side = attrs[AttrKeys.NEBENKOSTEN]?.let { KleinanzeigenFormats.parseEuros(it) }
        val heat = attrs[AttrKeys.HEIZKOSTEN]?.let { KleinanzeigenFormats.parseEuros(it) }

        return Rents(
            baseRent = base,
            sideCosts = side,
            heatingCosts = heat,
            totalRent = total ?: headlinePrice
        )
    }

    /**
     * German attribute labels emitted by Kleinanzeigen's detail-page DOM.
     * Centralised so a typo in one place cannot drift from another, and so a future
     * locale/source variant can swap this object out wholesale.
     */
    private object AttrKeys {
        const val WOHNFLAECHE = "Wohnfläche"
        const val ZIMMER = "Zimmer"
        const val SCHLAFZIMMER = "Schlafzimmer"
        const val BADEZIMMER = "Badezimmer"
        const val ETAGE = "Etage"
        const val WOHNUNGSTYP = "Wohnungstyp"
        const val KAUTION = "Kaution"
        const val KAUTION_GENOSS = "Kaution / Genoss.-Anteile"
        const val VERFUEGBAR_AB = "Verfügbar ab"
        const val KALTMIETE = "Kaltmiete"
        const val WARMMIETE = "Warmmiete"
        const val NEBENKOSTEN = "Nebenkosten"
        const val HEIZKOSTEN = "Heizkosten"
    }

    /**
     * CSS selectors. Centralised so the markup coupling lives in one place -
     * the one file that has to change when Kleinanzeigen updates its DOM.
     */
    private object Selectors {
        const val TITLE = "#viewad-title"
        const val PRICE_HEADLINE = "#viewad-price"
        const val DETAIL_LIST = "#viewad-details .addetailslist--detail"
        const val DETAIL_VALUE = "span.addetailslist--detail--value"
        const val LOCALITY = "#viewad-locality"
        const val AD_ID_INPUT = "input[name=adId]"
        const val CANONICAL = "link[rel=canonical]"
        const val SELLER = "#viewad-contact .userprofile-vip"
        const val DESCRIPTION = "#viewad-description-text"
        const val OG_IMAGE = "meta[property=og:image]"
        const val GALLERY_IMAGE = ".galleryimage-element[data-imgsrc]"
    }
}