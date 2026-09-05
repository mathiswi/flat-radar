package dev.flatradar.scraper

/**
 * Source-agnostic "Tauschwohnung" (swap-apartment) signal, shared by every
 * [SourceParser] that wants to skip swap offers before wasting a detail fetch.
 *
 * Matches the real title conventions seen across sources - Kleinanzeigen
 * ("TAUSCHWOHNUNG - …", "Wohnungtausch …", "SAGA Wohnung gegen SAGA") and
 * ImmoScout24 ("Tauschwohnung: …", "Wohnungstausch: …"). The check is a substring
 * match (not `startsWith`) because the swap token is often mid-title, plus a
 * guarded "… gegen …" heuristic for SAGA-style swaps that carry no explicit tausch
 * word in the title.
 */
object SwapDetector {
    // "gegen" phrases that are about money/terms, not a flat-for-flat swap.
    private val MONEY_WORDS = listOf(
        "KAUTION", "ABLÖSE", "ABLOESE", "PROVISION", "GEBÜHR", "GEBUEHR", "STAFFEL",
        "BÜRGSCHAFT", "BUERGSCHAFT",
    )

    fun isSwapByTitle(title: String?): Boolean =
        title != null && matchesSwapText(title.uppercase())

    /**
     * Swap signal over an uppercased blob - a title, or a URL slug with '-' → ' '.
     *  - any "TAUSCH" token (Tauschwohnung / Wohnungstausch / Wohnungtausch / Tausche /
     *    Tauschangebot), unless explicitly negated ("KEIN TAUSCH"); or "SWAP".
     *  - otherwise a "… GEGEN …" swap: needs a home word and must not be a money-"gegen"
     *    phrase, so a real rental ("Wohnung gegen Kaution") is not dropped.
     */
    fun matchesSwapText(upper: String): Boolean {
        if (upper.contains("TAUSCH")) return !upper.contains("KEIN TAUSCH")
        if (upper.contains("SWAP")) return true
        if (!Regex("""\bGEGEN\b""").containsMatchIn(upper)) return false
        val homeWord = upper.contains("WOHNUNG") || upper.contains("ZIMMER") || upper.contains("SAGA")
        return homeWord && MONEY_WORDS.none { upper.contains(it) }
    }
}
