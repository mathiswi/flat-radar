package dev.flatradar.scraper.kleinanzeigen

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the pure text-format parsers in [KleinanzeigenFormats].
 * These were previously private inside [DetailPageParser] and untestable in
 * isolation; extracting them is what made these tests possible.
 */
class KleinanzeigenFormatsTest {

    @Test
    fun parseEuros_strips_dot_and_currency() {
        assertEquals(1100, KleinanzeigenFormats.parseEuros("1.100 €"))
        assertEquals(130, KleinanzeigenFormats.parseEuros("130 €"))
        assertEquals(615, KleinanzeigenFormats.parseEuros("615 €"))
    }

    @Test
    fun parseEuros_returns_null_for_auf_anfrage_or_blank() {
        assertNull(KleinanzeigenFormats.parseEuros("Auf Anfrage"))
        assertNull(KleinanzeigenFormats.parseEuros("VB"))
        assertNull(KleinanzeigenFormats.parseEuros(""))
    }

    @Test
    fun parseSize_handles_ca_and_german_decimal() {
        assertEquals(57.0, KleinanzeigenFormats.parseSize("57 m²"))
        assertEquals(75.5, KleinanzeigenFormats.parseSize("75,5 m²"))
        assertEquals(80.0, KleinanzeigenFormats.parseSize("ca. 80 m²"))
        assertNull(KleinanzeigenFormats.parseSize("n/a"))
    }

    @Test
    fun parseRooms_german_decimal() {
        assertEquals(3.0, KleinanzeigenFormats.parseRooms("3"))
        assertEquals(2.5, KleinanzeigenFormats.parseRooms("2,5"))
        assertNull(KleinanzeigenFormats.parseRooms("n/a"))
    }

    @Test
    fun parseAvailableFrom_month_year_form() {
        assertEquals(LocalDate(2026, 10, 1), KleinanzeigenFormats.parseAvailableFrom("Oktober 2026"))
        assertEquals(LocalDate(2026, 1, 1), KleinanzeigenFormats.parseAvailableFrom("januar 2026"))
        assertEquals(LocalDate(2026, 12, 1), KleinanzeigenFormats.parseAvailableFrom("Dezember 2026"))
    }

    @Test
    fun parseAvailableFrom_sofort_returns_null() {
        assertNull(KleinanzeigenFormats.parseAvailableFrom("sofort"))
        assertNull(KleinanzeigenFormats.parseAvailableFrom("Sofort. Einzug möglich."))
    }

    @Test
    fun parseAvailableFrom_unknown_format_returns_null() {
        // The structured-attr parser does not parse "01.08.2026" - lock that
        // behaviour so a future extension is a deliberate test change, not a
        // silent drift. (Numeric dates in the *description* are handled by
        // parseAvailabilityFromText instead.)
        assertNull(KleinanzeigenFormats.parseAvailableFrom("01.08.2026"))
        assertNull(KleinanzeigenFormats.parseAvailableFrom("negotiable"))
    }

    private val today = LocalDate(2026, 9, 11)

    @Test
    fun parseAvailabilityFromText_numeric_date_after_keyword() {
        assertEquals(
            LocalDate(2026, 10, 1),
            KleinanzeigenFormats.parseAvailabilityFromText(
                "…schöne Wohnung.\n\nWeitere Angaben\n• Verfügbar ab: 01.10.2026\n• saniert", today,
            ),
        )
        assertEquals(
            LocalDate(2026, 10, 1),
            KleinanzeigenFormats.parseAvailabilityFromText("Frei ab 1.10.26", today),
        )
    }

    @Test
    fun parseAvailabilityFromText_month_name_forms() {
        assertEquals(
            LocalDate(2026, 10, 1),
            KleinanzeigenFormats.parseAvailabilityFromText("Bezug ab dem 1. Oktober 2026", today),
        )
        assertEquals(
            LocalDate(2026, 12, 1),
            KleinanzeigenFormats.parseAvailabilityFromText("Verfügbar ab Dezember 2026", today),
        )
    }

    @Test
    fun parseAvailabilityFromText_sofort_resolves_to_today() {
        assertEquals(today, KleinanzeigenFormats.parseAvailabilityFromText("Wohnung ab sofort frei", today))
        assertEquals(today, KleinanzeigenFormats.parseAvailabilityFromText("Verfügbar ab: sofort", today))
    }

    @Test
    fun parseAvailabilityFromText_ignores_unrelated_dates() {
        // A date not tied to an availability keyword must not be mistaken for the move-in date.
        assertNull(
            KleinanzeigenFormats.parseAvailabilityFromText("Baujahr 1998, letzte Sanierung 01.03.2020.", today),
        )
        assertNull(KleinanzeigenFormats.parseAvailabilityFromText("frei ab nach Vereinbarung", today))
    }

    @Test
    fun hasAvailabilitySignal_detects_wording() {
        assertEquals(true, KleinanzeigenFormats.hasAvailabilitySignal("frei ab nach Vereinbarung"))
        assertEquals(true, KleinanzeigenFormats.hasAvailabilitySignal("ab sofort beziehbar"))
        assertEquals(false, KleinanzeigenFormats.hasAvailabilitySignal("Schöne Wohnung mit Balkon"))
    }
}