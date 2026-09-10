package dev.flatradar.scraper.kleinanzeigen

import kotlinx.datetime.LocalDate

/**
 * Pure text-format parsers for the German Kleinanzeigen markup.
 *
 * Extracted from [DetailPageParser] so they can be unit-tested in isolation and
 * reused by other Kleinanzeigen-related code (e.g. a future [SearchPageParser]
 * extension that wants to peek at formatted prices on the search page).
 *
 * All functions return `null` for any input they cannot confidently interpret -
 * callers should treat `null` as "field absent on the page", never as zero.
 */
internal object KleinanzeigenFormats {

    /** German month names (lowercase) -> 1..12. Used by [parseAvailableFrom]. */
    private val MONTHS: Map<String, Int> = mapOf(
        "januar" to 1, "februar" to 2, "märz" to 3, "april" to 4,
        "mai" to 5, "juni" to 6, "juli" to 7, "august" to 8,
        "september" to 9, "oktober" to 10, "november" to 11, "dezember" to 12,
    )

    /** "1.100 €" | "130 €" | "615 €" -> 1100/130/615 ; "Auf Anfrage" / non-numeric -> null. */
    fun parseEuros(text: String): Int? =
        text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** "57 m²" -> 57.0 ; "75,5 m²" -> 75.5 ; "ca. 80 m²" -> 80.0. */
    fun parseSize(text: String): Double? =
        text.replace("ca.", "", ignoreCase = true)
            .replace("m²", "")
            .replace(",", ".")
            .trim()
            .toDoubleOrNull()

    /** "3" -> 3.0 ; "2,5" -> 2.5. */
    fun parseRooms(text: String): Double? =
        text.replace(",", ".").trim().toDoubleOrNull()

    /**
     * "Oktober 2026" -> LocalDate(2026, 10, 1).
     * "sofort" / "01.08.2026" / unknown -> null  (extend as needed).
     */
    fun parseAvailableFrom(text: String): LocalDate? {
        val lower = text.lowercase().trim()
        if (lower.startsWith("sofort")) return null
        val parts = lower.split(" ", limit = 2)
        if (parts.size != 2) return null
        val month = MONTHS[parts[0]] ?: return null
        val year = parts[1].toIntOrNull() ?: return null
        return LocalDate(year, month, 1)
    }

    /** Phrases that flag an availability keyword directly preceding the date. */
    private val AVAIL_KEYWORDS = listOf(
        "verfügbar ab", "verfügbar:", "frei ab", "bezugsfrei ab",
        "beziehbar ab", "bezug ab", "bezug:", "einzug ab", "einzug:",
    )

    /** "ab sofort" and friends anywhere in the text => available today. */
    private val SOFORT = listOf(
        "ab sofort", "sofort verfügbar", "sofort beziehbar", "sofort frei", "sofort bezugsfrei",
    )

    private val NUMERIC_DATE = Regex("""(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{2,4})""")
    private val DAY_MONTH_YEAR = Regex("""(\d{1,2})\.?\s*(${MONTHS.keys.joinToString("|")})\s+(\d{4})""")
    private val MONTH_YEAR = Regex("""(${MONTHS.keys.joinToString("|")})\s+(\d{4})""")

    /**
     * Cheap, deterministic move-in-date scan of a Kleinanzeigen *description* for ads that
     * carry no structured "Verfügbar ab" attribute row. Only trusts a date that sits right
     * after an availability keyword (e.g. "Verfügbar ab: 01.10.2026"), so it can't grab an
     * unrelated date from the prose (Baujahr, Sanierung, …). Handles numeric dates,
     * "1. Oktober 2026", bare "Oktober 2026", and "sofort" (=> [today]). Returns null when
     * nothing confident is found — the caller may then try the LLM fallback.
     */
    fun parseAvailabilityFromText(text: String, today: LocalDate): LocalDate? {
        val lower = text.lowercase()
        for (kw in AVAIL_KEYWORDS) {
            var at = lower.indexOf(kw)
            while (at >= 0) {
                val tail = lower.substring(at + kw.length).take(40)
                parseDateToken(tail, today)?.let { return it }
                at = lower.indexOf(kw, at + kw.length)
            }
        }
        if (SOFORT.any { lower.contains(it) }) return today
        return null
    }

    /** True when the text mentions availability at all — the gate for the LLM fallback. */
    fun hasAvailabilitySignal(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("verfügbar", "frei ab", "bezugsfrei", "beziehbar", "bezug ab", "einzug", "sofort")
            .any { lower.contains(it) }
    }

    /** Parses the snippet right after an availability keyword; [today] resolves "sofort". */
    private fun parseDateToken(tail: String, today: LocalDate): LocalDate? {
        if (tail.trimStart(' ', ':', '-', '\t', '.').startsWith("sofort")) return today

        NUMERIC_DATE.find(tail)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate(year, mo.toInt(), d.toInt()) }.getOrNull()
        }
        DAY_MONTH_YEAR.find(tail)?.let { m ->
            val (d, mon, y) = m.destructured
            val month = MONTHS[mon] ?: return@let
            return runCatching { LocalDate(y.toInt(), month, d.toInt()) }.getOrNull()
        }
        MONTH_YEAR.find(tail)?.let { m ->
            val (mon, y) = m.destructured
            val month = MONTHS[mon] ?: return@let
            return runCatching { LocalDate(y.toInt(), month, 1) }.getOrNull()
        }
        return null
    }
}