package dev.flatradar.scraper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks the source-registry contract the feed runner depends on: a feed's
 * `source` string maps to exactly one [SourceParser], and an unrecognized
 * source is `null` (the runner logs + skips it rather than crashing). Feed
 * config itself is validated server-side now (see backend FeedCrudRoutes) and
 * fetched via [BackendFeeds], so there's no file-loading to test here.
 */
class SourceParsersTest {

    @Test
    fun unknown_source_is_not_a_known_parser() {
        assertNull(SourceParsers.get("immoscout")) // typo for immoscout24
        assertNull(SourceParsers.get(""))
        assertNull(SourceParsers.get("Kleinanzeigen")) // case-sensitive
    }

    @Test
    fun kleinanzeigen_parser_is_registered_under_its_source_key() {
        assertEquals(
            dev.flatradar.scraper.kleinanzeigen.KleinanzeigenParser,
            SourceParsers.get("kleinanzeigen"),
        )
    }

    @Test
    fun immoscout24_parser_is_registered_under_its_source_key() {
        assertEquals(
            dev.flatradar.scraper.immoscout24.ImmoscoutParser,
            SourceParsers.get("immoscout24"),
        )
    }
}
