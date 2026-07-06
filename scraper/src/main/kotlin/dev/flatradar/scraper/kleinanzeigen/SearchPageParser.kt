package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.AdRef
import org.jsoup.Jsoup

object SearchPageParser {

    /**
     * Parses a Kleinanzeigen search-results page into a list of [AdRef]s.
     *
     * Pure parse: returns every card found, INCLUDING Tauschwohnung ads.
     * The caller is responsible for filtering swaps (see [KleinanzeigenParser.parseSearch]
     * which does this once the source dispatch layer is in place).
     *
     * Confirmed markup (real page, July 2026):
     *   - Card container:     article.aditem
     *     - ad id:             attribute "data-adid"
     *     - href (relative):   attribute "data-href"  (e.g. "/s-anzeige/.../3450160395-203-9449")
     *     - title:             h2.text-module-begin a.ellipsis .text()
     */
    fun parse(html: String, district: String): List<AdRef> {
        val doc = Jsoup.parse(html)
        val refs = mutableListOf<AdRef>()

        for (card in doc.select(Constants.CARD)) {
            val adId = card.attr(Constants.AD_ID_ATTR).takeIf { it.isNotBlank() } ?: continue
            val href = card.attr(Constants.HREF_ATTR).takeIf { it.isNotBlank() } ?: continue
            val title = card.selectFirst(Constants.CARD_TITLE)?.text()?.trim() ?: continue

            val fullUrl = if (href.startsWith("http")) href else Constants.BASE_URL + href
            refs.add(AdRef(adId = adId, url = fullUrl, title = title, district = district))
        }

        return refs
    }

    private object Constants {
        const val BASE_URL = "https://www.kleinanzeigen.de"
        const val CARD = "article.aditem"
        const val AD_ID_ATTR = "data-adid"
        const val HREF_ATTR = "data-href"
        const val CARD_TITLE = "h2.text-module-begin a.ellipsis"
    }
}