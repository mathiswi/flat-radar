package dev.flatradar.scraper.kleinanzeigen

import dev.flatradar.scraper.loadResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPageParserTest {

    @Test
    fun parser_returns_all_cards_including_swap_ones() {
        // Pure-parse contract: the parser must NOT filter swaps. The filter is the
        // source-parser layer's job (KleinanzeigenParser.parseSearch) so the parser
        // itself stays a pure jsoup transformation with a single responsibility.
        val html = loadResource("/mock/kleinanzeigen/search_barmbek.html")
        val refs = SearchPageParser.parse(html)

        assertTrue(refs.isNotEmpty(), "expected at least one ref from real-search snapshot")
        assertTrue(refs.all { it.url.startsWith("https://www.kleinanzeigen.de") })
        assertTrue(refs.all { it.adId.isNotBlank() })
        assertTrue(refs.all { it.title.isNotBlank() })
    }

    @Test
    fun source_parser_filters_swap_cards() {
        // The source-parser layer keeps the "no detail fetch wasted on swaps" optimisation
        // that previously lived inline in SearchPageParser.parse. The snapshot contains
        // Tauschwohnung/Wohnungsswap cards, so filtering must strictly shrink the list.
        val html = loadResource("/mock/kleinanzeigen/search_barmbek.html")
        val all = SearchPageParser.parse(html)
        val filtered = KleinanzeigenParser.parseSearch(html)

        assertTrue(filtered.isNotEmpty())
        assertTrue(filtered.size < all.size, "snapshot has swaps that should be filtered out")
        assertTrue(filtered.none { it.title.trim().uppercase().startsWith("TAUSCHWOHNUNG") })
    }

    @Test
    fun source_parser_filters_swap_url_slug() {
        // Belt-and-braces: a card with a swap slug but a non-swap title should also
        // be filtered out (the slug check is the secondary signal).
        val html = """
            <article data-adid="111" data-href="/s-anzeige/tauschwohnung-foo/111-203-1">
                <h3><a href="#" name="111">Nice flat</a></h3>
            </article>
        """.trimIndent()
        val refs = KleinanzeigenParser.parseSearch(html)
        assertEquals(0, refs.size)
    }

    @Test
    fun next_page_urls_returns_pages_up_to_cap() {
        // The pagination widget links pages 2..5, but PAGE_CAP (3) bounds how many we
        // follow, so only seite:2 and seite:3 come back, as absolute URLs in order.
        val html = loadResource("/mock/kleinanzeigen/search_barmbek.html")
        val urls = KleinanzeigenParser.nextSearchPageUrls("https://www.kleinanzeigen.de/x", html)

        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("https://www.kleinanzeigen.de") && urls[0].contains("seite:2"))
        assertTrue(urls[1].contains("seite:3"))
        assertTrue(urls.none { it.contains("seite:4") || it.contains("seite:5") })
    }

    @Test
    fun page_two_snapshot_parses_cards() {
        val html = loadResource("/mock/kleinanzeigen/search_barmbek_p2.html")
        val refs = SearchPageParser.parse(html)
        assertTrue(refs.isNotEmpty(), "expected cards on the page-2 snapshot")
        assertTrue(refs.all { it.adId.isNotBlank() && it.url.startsWith("https://www.kleinanzeigen.de") })
    }
}
