package dev.flatradar.scraper.immoscout24

import kotlinx.datetime.LocalDate

/**
 * Pure text-format parsers for the German strings ImmoScout24's mobile API embeds
 * in expose attribute values (e.g. `"930\u00A0€"`, `"63 m²"`, `"7.8.2026"`).
 *
 * Mirrors [dev.flatradar.scraper.kleinanzeigen.KleinanzeigenFormats]'s contract: every
 * function returns `null` for input it cannot confidently interpret - callers should
 * treat `null` as "field absent", never as zero.
 */
internal object ImmoscoutFormats {

    /** "930\u00A0€" | "1.190 €" | "2800" | "530 € zzgl. Heiz- und Nebenkosten" -> 930/1190/2800/530. */
    fun parseEuros(text: String): Int? =
        text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** "63 m²" | "ca. 63 m²" | "63,5 m²" -> 63.0 / 63.0 / 63.5. */
    fun parseSize(text: String): Double? =
        text.replace("ca.", "", ignoreCase = true)
            .replace("m²", "")
            .replace("\u00A0", "")
            .replace(",", ".")
            .trim()
            .toDoubleOrNull()

    /** "2,5" -> 2.5 ; "3" -> 3.0. */
    fun parseRooms(text: String): Double? =
        text.replace(",", ".").trim().toDoubleOrNull()

    /**
     * "710 m" -> 710 ; "1,6 km" -> 1600 ; "1,4 km" -> 1400 (rounded to the nearest metre).
     * Formats observed in `item.address.distance` on the mobile-API search-list response.
     */
    fun parseDistanceMeters(text: String): Int? {
        val trimmed = text.trim()
        val numberPart = trimmed.substringBefore(" ").replace(",", ".")
        val number = numberPart.toDoubleOrNull() ?: return null
        return when {
            trimmed.endsWith("km", ignoreCase = true) -> Math.round(number * 1000).toInt()
            trimmed.endsWith("m", ignoreCase = true) -> Math.round(number).toInt()
            else -> null
        }
    }

    /** "7.8.2026" (d.M.yyyy) -> LocalDate(2026, 8, 7). "sofort" / unparseable text -> null. */
    fun parseAvailableFrom(text: String): LocalDate? {
        val trimmed = text.trim()
        val parts = trimmed.split(".")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        return try {
            LocalDate(year, month, day)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
