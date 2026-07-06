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
 * Implementations should return an empty list on failure rather than throwing -
 * the scraper must keep running for the feeds it CAN load, and log the failure.
 */
interface Feeds {
    suspend fun all(): List<FeedConfig>
}

/**
 * Loads feeds from a JSON array file on disk.
 *
 * Path resolution, in order:
 *   1. The [path] constructor argument, when non-null (used by tests to point
 *      at an explicit file).
 *   2. The first `feeds.json` found by walking up from the cwd (same lookup as
 *      `.env`) - covers `./gradlew :scraper:run` running from the subproject
 *      while the user's `feeds.json` lives at the repo root.
 *   3. Empty list if neither resolves.
 *
 * The file reading is delegated to the injected [read] suspend lambda so tests
 * can supply canned JSON without touching the filesystem. The default reads the
 * resolved path straight from disk.
 *
 * JSON schema (see `feeds.json.example`):
 *   [{ "id": "...", "displayName": "...", "source": "kleinanzeigen",
 *      "url": "https://...", "district": "Barmbek", "enabled": true }]
 * Unknown keys are ignored so the file can grow new fields without breaking older scrapers.
 */
class JsonFileFeeds(
    private val path: String? = null,
    private val read: suspend (String) -> String = ::defaultReadFile,
) : Feeds {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun all(): List<FeedConfig> {
        return try {
            val resolved = path ?: findUpward("feeds.json")
            if (resolved == null) {
                System.err.println("[JsonFileFeeds] no feeds.json found; returning empty list")
                return emptyList()
            }
            val text = read(resolved)
            json.decodeFromString(ListSerializer(FeedConfig.serializer()), text)
        } catch (e: Exception) {
            System.err.println("[JsonFileFeeds] could not load feeds: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        private suspend fun defaultReadFile(filePath: String): String =
            File(filePath).readText()
    }
}