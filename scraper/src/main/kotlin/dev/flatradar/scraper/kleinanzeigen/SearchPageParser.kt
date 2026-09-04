package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.AdRef
import org.jsoup.Jsoup

object SearchPageParser {

    /** Only follow the first few result pages: at a 5-10 min cadence with
     *  newest-first ordering, listings past this are stale, and a tight cap keeps
     *  the per-run fetch count (and the delisting "seen" set) bounded. */
    const val PAGE_CAP = 3

    /**
     * Parses a Kleinanzeigen search-results page into a list of [AdRef]s.
     *
     * Pure parse: returns every card found, INCLUDING Tauschwohnung ads.
     * The caller is responsible for filtering swaps (see [KleinanzeigenParser.parseSearch]
     * which does this once the source dispatch layer is in place).
     *
     * Confirmed markup (live Astro page, September 2026):
     *   - Card container:     article[data-adid]
     *     - ad id:             attribute "data-adid"
     *     - href (relative):   attribute "data-href"  (e.g. "/s-anzeige/.../3503537335-203-9448")
     *     - title:             h3 a .text()
     */
    fun parse(html: String): List<AdRef> {
        val doc = Jsoup.parse(html)
        val refs = mutableListOf<AdRef>()

        for (card in doc.select(Constants.CARD)) {
            val adId = card.attr(Constants.AD_ID_ATTR).takeIf { it.isNotBlank() } ?: continue
            val href = card.attr(Constants.HREF_ATTR).takeIf { it.isNotBlank() } ?: continue
            val title = card.selectFirst(Constants.CARD_TITLE)?.text()?.trim() ?: continue

            val fullUrl = if (href.startsWith("http")) href else Constants.BASE_URL + href
            refs.add(AdRef(adId = adId, url = fullUrl, title = title))
        }

        return refs
    }

    /**
     * Absolute URLs for result pages 2..[PAGE_CAP] linked from the first page's
     * pagination widget, in ascending page order. Empty when there is no widget
     * (single page of results).
     *
     * The widget is windowed - it only renders anchors for a handful of low page
     * numbers (2-5) plus a jump-to-last - but that window always covers our cap,
     * so we can just read its hrefs (path segment `seite:N`) rather than parsing a
     * max and reconstructing URLs.
     */
    fun nextPageUrls(html: String): List<String> {
        val pagination = Jsoup.parse(html).selectFirst(Constants.PAGINATION) ?: return emptyList()
        return pagination.select("a[href]")
            .mapNotNull { a ->
                val href = a.attr("href")
                val page = Constants.SEITE_REGEX.find(href)?.groupValues?.get(1)?.toIntOrNull()
                if (page != null && page in 2..PAGE_CAP) page to href else null
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .map { (_, href) -> if (href.startsWith("http")) href else Constants.BASE_URL + href }
    }

    private object Constants {
        const val BASE_URL = "https://www.kleinanzeigen.de"
        const val CARD = "article[data-adid]"
        const val AD_ID_ATTR = "data-adid"
        const val HREF_ATTR = "data-href"
        const val CARD_TITLE = "h3 a"
        const val PAGINATION = "#srchrslt-pagination"
        val SEITE_REGEX = Regex("""seite:(\d+)""")
    }
}
