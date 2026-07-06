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
        val refs = SearchPageParser.parse(html, "Barmbek")

        assertTrue(refs.isNotEmpty(), "expected at least one ref from real-search snapshot")
        assertTrue(refs.all { it.url.startsWith("https://www.kleinanzeigen.de") })
        assertTrue(refs.all { it.district == "Barmbek" })
        assertTrue(refs.all { it.adId.isNotBlank() })
    }

    @Test
    fun source_parser_filters_swap_cards() {
        // The source-parser layer keeps the "no detail fetch wasted on swaps" optimisation
        // that previously lived inline in SearchPageParser.parse.
        val html = loadResource("/mock/kleinanzeigen/search_barmbek.html")
        val refs = KleinanzeigenParser.parseSearch(html, "Barmbek")

        assertTrue(refs.isNotEmpty())
        assertTrue(refs.none { it.title.trim().uppercase().startsWith("TAUSCHWOHNUNG") })
    }

    @Test
    fun source_parser_filters_swap_url_slug() {
        // Belt-and-braces: a card with a swap slug but a non-swap title should also
        // be filtered out (the slug check is the secondary signal).
        val html = """
            <article class="aditem" data-adid="111" data-href="/s-anzeige/tauschwohnung-foo/111-203-1">
                <h2 class="text-module-begin"><a class="ellipsis" href="#">Nice flat</a></h2>
            </article>
        """.trimIndent()
        val refs = KleinanzeigenParser.parseSearch(html, "X")
        assertEquals(0, refs.size)
    }
}