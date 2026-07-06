package dev.flatradar.scraper

import java.io.File

/**
 * Walks up to [maxDepth] parent directories from the JVM's current working
 * directory looking for a file named [fileName]. Returns the absolute path of
 * the first hit, or `null` if none is found.
 *
 * Single helper used by both [Env] (for `.env`) and [JsonFileFeeds] (for
 * `feeds.json`) so the lookup behaves the same no matter which subproject Gradle
 * sets as the cwd when running `:scraper:run`.
 */
internal fun findUpward(fileName: String, maxDepth: Int = 4): String? {
    var dir: File? = File(System.getProperty("user.dir"))
    repeat(maxDepth) {
        val candidate = File(dir, fileName)
        if (candidate.isFile) return candidate.absolutePath
        dir = dir?.parentFile
    }
    return null
}