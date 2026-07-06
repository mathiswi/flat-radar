package dev.flatradar.scraper.kleinanzeigen

/**
 * Detects swap-apartment ("Tauschwohnung") ads that should be filtered out.
 *
 * Signals observed on Kleinanzeigen.de (ordered by reliability):
 *  1. Account name in seller card contains "Tauschwohnung"  — hardest to spoof, detail-page only
 *  2. Title text starts with literal "TAUSCHWOHNUNG"          — strong, available on search AND detail
 *  3. Canonical-URL slug starts with "tauschwohnung-"          — URL signal, available on both
 *  4. Description opens with "Es handelt es sich hierbei um ein Tauschangebot." — detail only
 *
 * Cheap string-only checks for use on the search page (no jsoup Document required):
 *  - [isSwapByTitle] / [isSwapBySlug]
 *
 * Full check for the detail page (all four sources):
 *  - [isSwap]
 *
 * Note: the literal German markers ("TAUSCHWOHNUNG", "tauschwohnung-", "Tauschwohnung GmbH",
 * "Es handelt es sich hierbei um ein Tauschangebot.") are kept as-is because they are
 * real-world data tokens emitted by Kleinanzeigen, not code identifiers.
 */
object SwapDetector {

    /** Lightweight title check — call from SearchPageParser to skip the detail fetch entirely. */
    fun isSwapByTitle(title: String?): Boolean =
        title != null && title.trim().uppercase().startsWith("TAUSCHWOHNUNG")

    /** Lightweight URL-slug check — call from SearchPageParser. Pass the ad's data-href, full URL, or slug. */
    fun isSwapBySlug(url: String?): Boolean {
        if (url == null) return false
        val slug = url.substringAfterLast("/s-anzeige/", "")
            .substringBefore("-")
            .lowercase()
        return slug.startsWith("tauschwohnung")
    }

    fun isSwapByAccount(sellerName: String?): Boolean =
        sellerName != null && sellerName.contains("Tauschwohnung", ignoreCase = true)

    fun isSwapByDescription(description: String?): Boolean =
        description != null && description.trim().startsWith("Es handelt es sich hierbei um ein Tauschangebot.")

    fun isSwap(
        title: String? = null,
        url: String? = null,
        sellerName: String? = null,
        description: String? = null
    ): Boolean =
        isSwapByTitle(title) ||
        isSwapBySlug(url) ||
        isSwapByAccount(sellerName) ||
        isSwapByDescription(description)
}