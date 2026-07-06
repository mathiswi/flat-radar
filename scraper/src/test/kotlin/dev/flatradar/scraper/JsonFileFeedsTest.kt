package dev.flatradar.scraper

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonFileFeedsTest {

    @Test
    fun loads_feeds_from_injected_reader() = runTest {
        val json = """
            [
              { "id": "barmbek", "displayName": "Barmbek", "source": "kleinanzeigen",
                "url": "https://example.de", "district": "Barmbek", "enabled": true },
              { "id": "imo-typo", "displayName": "Typo", "source": "immoscout",
                "url": "https://example.de", "district": "X" }
            ]
        """.trimIndent()

        // path provided so findUpward is bypassed; read supplies the JSON literally.
        val feeds = JsonFileFeeds(path = "memory:test", read = { json })

        val all = feeds.all()
        assertEquals(2, all.size)
        assertEquals("barmbek", all[0].id)
        assertEquals("kleinanzeigen", all[0].source)
        assertEquals(true, all[0].enabled)
        assertEquals("immoscout", all[1].source)
    }

    @Test
    fun malformed_json_returns_empty_list_loudly() = runTest {
        val feeds = JsonFileFeeds(path = "memory:bad", read = { "not json at all" })
        assertTrue(feeds.all().isEmpty())
    }

    @Test
    fun unknown_source_in_feeds_is_not_a_known_parser() {
        // Locks the "log + skip that feed, continue" contract: bad config values
        // are normal data, not crashes. The runner does SourceParsers.get(source)
        // and treats `null` as "skip with a warning".
        assertNull(SourceParsers.get("immoscout"))
        assertNull(SourceParsers.get(""))
        assertNull(SourceParsers.get("Kleinanzeigen"))
    }

    @Test
    fun kleinanzeigen_parser_is_registered_under_its_source_key() {
        assertEquals(
            dev.flatradar.scraper.kleinanzeigen.KleinanzeigenParser,
            SourceParsers.get("kleinanzeigen"),
        )
    }

    @Test
    fun feedconfig_unknown_keys_are_tolerated() = runTest {
        // Forward-compat: when the future backend/DB adds fields like lastFetched
        // or ownerUserId, existing scraper binaries must not break on the older
        // JSON shape. JsonFileFeeds uses ignoreUnknownKeys = true for this.
        val json = """
            [
              { "id": "barmbek", "displayName": "Barmbek", "source": "kleinanzeigen",
                "url": "https://example.de", "district": "Barmbek",
                "lastFetched": "2026-07-07T10:00:00Z", "ownerUserId": 42 }
            ]
        """.trimIndent()

        val feeds = JsonFileFeeds(path = "memory:test", read = { json })
        val all = feeds.all()
        assertEquals(1, all.size)
        assertEquals("barmbek", all[0].id)
    }
}