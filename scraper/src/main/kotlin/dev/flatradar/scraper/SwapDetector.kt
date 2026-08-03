package dev.flatradar.scraper

/**
 * Source-agnostic "Tauschwohnung" (swap-apartment) title signal, shared by every
 * [SourceParser] that wants to skip swap offers before wasting a detail fetch.
 *
 * The prefix check is deliberately loose (`startsWith`, case-insensitive) so it
 * matches the title conventions observed on every source scraped so far:
 * Kleinanzeigen ("TAUSCHWOHNUNG - ...") and ImmoScout24 ("Tauschwohnung: ...").
 * Source-specific signals (account name, URL slug, description text) stay in
 * that source's own package - see [dev.flatradar.scraper.kleinanzeigen.SwapDetector].
 */
object SwapDetector {
    fun isSwapByTitle(title: String?): Boolean =
        title != null && title.trim().uppercase().startsWith("TAUSCHWOHNUNG")
}
