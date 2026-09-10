package dev.flatradar.scraper

import kotlinx.datetime.LocalDate

/**
 * Extracts a move-in date ("Verfügbar ab") from a Kleinanzeigen ad's free-text
 * description for the ads that carry no structured "Verfügbar ab" attribute row and
 * whose date the cheap deterministic scan ([kleinanzeigen.KleinanzeigenFormats.parseAvailabilityFromText])
 * couldn't resolve. The parser only wants a single suspend function — the
 * implementation may be the LLM-backed [AvailabilityExtractor], a stub in tests, or
 * anything else. The scraper package must not depend on a concrete LLM SDK.
 *
 * Returns `null` when no move-in date can be determined (caller keeps `availableFrom`
 * null, never a guess).
 */
fun interface AvailabilityFallback {
    suspend fun extract(description: String): LocalDate?
}
