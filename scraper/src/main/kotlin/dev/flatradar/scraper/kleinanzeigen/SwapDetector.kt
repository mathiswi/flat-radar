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

    /** Lightweight title check — call from SearchPageParser to skip the detail fetch entirely.
     * Delegates to the source-agnostic [dev.flatradar.scraper.SwapDetector] which handles both
     * "Tauschwohnung" (Kleinanzeigen) and "Wohnungstausch" (ImmoScout24) prefixes. */
    fun isSwapByTitle(title: String?): Boolean =
        dev.flatradar.scraper.SwapDetector.isSwapByTitle(title)

    /** Lightweight URL-slug check — call from SearchPageParser. Pass the ad's data-href, full URL, or slug.
     * Scans the whole slug (hyphens → spaces) through the shared swap-token matcher, so
     * "wohnungtausch-saga" and "saga-wohnung-gegen-saga" are caught, not just a leading token. */
    fun isSwapBySlug(url: String?): Boolean {
        if (url == null) return false
        val slug = url.substringAfterLast("/s-anzeige/", "").substringBefore("/")
        if (slug.isEmpty()) return false
        return dev.flatradar.scraper.SwapDetector.matchesSwapText(slug.replace('-', ' ').uppercase())
    }

    fun isSwapByAccount(sellerName: String?): Boolean =
        sellerName != null && sellerName.contains("Tauschwohnung", ignoreCase = true)

    fun isSwapByDescription(description: String?): Boolean {
        if (description == null) return false
        val d = description.trim()
        if (d.startsWith("Es handelt es sich hierbei um ein Tauschangebot.")) return true
        // Positive swap phrases only, so a negation ("Tausch nicht möglich") is NOT flagged.
        // Catches SAGA-style swaps whose title lacks a tausch token, e.g. a description reading
        // "… zum Tausch im Rahmen eines SAGA-gegen-SAGA-Tauschs …".
        val u = d.uppercase()
        return u.contains("TAUSCHANGEBOT") || u.contains("TAUSCHWOHNUNG") ||
            u.contains("WOHNUNGSTAUSCH") || u.contains("WOHNUNGTAUSCH") ||
            Regex("""\b(ZUM|IM)\s+TAUSCH""").containsMatchIn(u)
    }

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