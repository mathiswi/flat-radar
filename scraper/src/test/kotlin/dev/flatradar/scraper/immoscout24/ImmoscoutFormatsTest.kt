package dev.flatradar.scraper.immoscout24

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImmoscoutFormatsTest {

    @Test
    fun parseEuros_strips_non_digits() {
        assertEquals(930, ImmoscoutFormats.parseEuros("930\u00A0€"))
        assertEquals(1190, ImmoscoutFormats.parseEuros("1.190 €"))
        assertEquals(2800, ImmoscoutFormats.parseEuros("2800"))
        assertEquals(530, ImmoscoutFormats.parseEuros("530 € zzgl. Heiz- und Nebenkosten"))
    }

    @Test
    fun parseEuros_returns_null_for_non_numeric_text() {
        assertNull(ImmoscoutFormats.parseEuros("keine Angabe"))
    }

    @Test
    fun parseSize_handles_ca_prefix_and_comma_decimal() {
        assertEquals(63.0, ImmoscoutFormats.parseSize("63 m²"))
        assertEquals(63.0, ImmoscoutFormats.parseSize("ca. 63 m²"))
        assertEquals(63.5, ImmoscoutFormats.parseSize("63,5 m²"))
    }

    @Test
    fun parseRooms_handles_comma_decimal() {
        assertEquals(2.5, ImmoscoutFormats.parseRooms("2,5"))
        assertEquals(3.0, ImmoscoutFormats.parseRooms("3"))
    }

    @Test
    fun parseAvailableFrom_handles_d_m_yyyy() {
        assertEquals(LocalDate(2026, 8, 7), ImmoscoutFormats.parseAvailableFrom("7.8.2026"))
    }

    @Test
    fun parseAvailableFrom_returns_null_for_unparseable_text() {
        assertNull(ImmoscoutFormats.parseAvailableFrom("sofort"))
        assertNull(ImmoscoutFormats.parseAvailableFrom("auf Anfrage"))
    }

    @Test
    fun parseDistanceMeters_handles_metres() {
        assertEquals(710, ImmoscoutFormats.parseDistanceMeters("710 m"))
        assertEquals(881, ImmoscoutFormats.parseDistanceMeters("881 m"))
    }

    @Test
    fun parseDistanceMeters_handles_kilometres_with_comma_decimal() {
        assertEquals(1600, ImmoscoutFormats.parseDistanceMeters("1,6 km"))
        assertEquals(1400, ImmoscoutFormats.parseDistanceMeters("1,4 km"))
    }

    @Test
    fun parseDistanceMeters_handles_whole_kilometres() {
        assertEquals(2000, ImmoscoutFormats.parseDistanceMeters("2 km"))
    }

    @Test
    fun parseDistanceMeters_returns_null_for_unparseable_text() {
        assertNull(ImmoscoutFormats.parseDistanceMeters("keine Angabe"))
        assertNull(ImmoscoutFormats.parseDistanceMeters(""))
    }
}
