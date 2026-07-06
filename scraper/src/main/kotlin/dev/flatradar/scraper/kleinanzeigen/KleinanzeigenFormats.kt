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
}