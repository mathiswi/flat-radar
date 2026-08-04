package dev.flatradar.scraper

/**
 * Source-agnostic "Tauschwohnung" (swap-apartment) title signal, shared by every
 * [SourceParser] that wants to skip swap offers before wasting a detail fetch.
 *
 * The prefix check is deliberately loose (`startsWith`, case-insensitive) so it
 * matches the title conventions observed on every source scraped so far:
 * Kleinanzeigen ("TAUSCHWOHNUNG - ...") and ImmoScout24 ("Tauschwohnung: ...",
 * "Wohnungstausch: ...").
 */
object SwapDetector {
    fun isSwapByTitle(title: String?): Boolean {
        if (title == null) return false
        val t = title.trim().uppercase()
        return t.startsWith("TAUSCHWOHNUNG") || t.startsWith("WOHNUNGSTAUSCH") || t.startsWith("WOHNUNGSSWAP")
    }
}
