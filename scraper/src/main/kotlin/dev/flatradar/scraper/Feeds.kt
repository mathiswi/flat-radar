package dev.flatradar.scraper

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Source of [FeedConfig]s for the scraper's polling loop.
 *
 * Today: a JSON file next to `.env` (see [JsonFileFeeds]).
 * Tomorrow: the backend API or a DB lookup. The interface is `suspend` from day
 * one so swapping the implementation never changes call-site signatures.
 *
 * A missing file is a valid "nothing configured yet" state and returns an empty
 * list. A file that exists but fails to parse is a broken deployment and throws -
 * the caller ([main]) treats that as fatal.
 */
interface Feeds {
    suspend fun all(): List<FeedConfig>
}

/**
 * Loads feeds from a JSON array file on disk. Resolves [path] if given
 * (tests point this at an explicit file), otherwise the first `feeds.json`
 * found walking up from the cwd - covers `./gradlew :scraper:run` setting cwd
 * to the subproject while the user's `feeds.json` lives at the repo root.
 *
 * JSON schema (see `feeds.json.example`):
 *   [{ "id": "...", "displayName": "...", "source": "kleinanzeigen",
 *      "url": "https://...", "district": "Barmbek", "enabled": true }]
 * Unknown keys are ignored so the file can grow new fields without breaking older scrapers.
 */
class JsonFileFeeds(
    private val path: String? = null,
    private val read: suspend (String) -> String = ::defaultReadFile,
    private val locate: () -> String? = { findUpward("feeds.json") },
) : Feeds {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun all(): List<FeedConfig> {
        val resolved = path ?: locate()
        if (resolved == null) {
            System.err.println("[JsonFileFeeds] no feeds.json found; returning empty list")
            return emptyList()
        }
        val text = read(resolved)
        return json.decodeFromString(ListSerializer(FeedConfig.serializer()), text)
    }

    private companion object {
        private suspend fun defaultReadFile(filePath: String): String =
            File(filePath).readText()
    }
}