package dev.flatradar.scraper.kleinanzeigen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwapDetectorTest {

    @Test
    fun title_signal() {
        assertTrue(SwapDetector.isSwapByTitle("TAUSCHWOHNUNG - Hamburg Nord"))
        assertTrue(SwapDetector.isSwapByTitle("Wohnungstausch: Käthnerort 57"))
        // Real leaks that startsWith missed: misspelled/mid-title tausch, and SAGA "gegen" swaps.
        assertTrue(SwapDetector.isSwapByTitle("Wohnungtausch Saga"))
        assertTrue(SwapDetector.isSwapByTitle("SAGA Wohnung gegen SAGA"))
        assertTrue(SwapDetector.isSwapByTitle("Saga Wohnungstausch 2 Zimmer gegen 3"))
        assertFalse(SwapDetector.isSwapByTitle("Schöne 2-Zi-Wohnung"))
        // "gegen" that is about money, and an explicit negation, must NOT be flagged.
        assertFalse(SwapDetector.isSwapByTitle("2-Zi-Wohnung, gegen Kaution"))
        assertFalse(SwapDetector.isSwapByTitle("Schöne Wohnung, kein Tausch"))
        assertFalse(SwapDetector.isSwapByTitle(null))
    }

    @Test
    fun slug_signal() {
        assertTrue(SwapDetector.isSwapBySlug("/s-anzeige/tauschwohnung-gemuetliche-wohnung/3425649112-203-9480"))
        assertTrue(SwapDetector.isSwapBySlug("/s-anzeige/wohnungtausch-saga/3503054877-203-16442"))
        assertTrue(SwapDetector.isSwapBySlug("/s-anzeige/saga-wohnung-gegen-saga/3502348870-203-9506"))
        assertFalse(SwapDetector.isSwapBySlug("/s-anzeige/2-zi-wohnung-in-niendorf/3452480994-203-16475"))
        assertFalse(SwapDetector.isSwapBySlug(null))
    }

    @Test
    fun account_signal() {
        assertTrue(SwapDetector.isSwapByAccount("Tauschwohnung GmbH"))
        assertFalse(SwapDetector.isSwapByAccount("VestenbergImmobilien"))
    }

    @Test
    fun description_signal() {
        assertTrue(SwapDetector.isSwapByDescription("Es handelt es sich hierbei um ein Tauschangebot. Sonst nichts."))
        // Bare "Tauschangebot" anywhere is a swap marker; and the real SAGA "zum Tausch" phrasing.
        assertTrue(SwapDetector.isSwapByDescription("Nette Wohnung. Es handelt sich um ein Tauschangebot."))
        assertTrue(SwapDetector.isSwapByDescription("Ich biete die Wohnung zum Tausch im Rahmen eines SAGA-gegen-SAGA-Tauschs an."))
        // Negation ("Tausch nicht möglich") stays a normal rental.
        assertFalse(SwapDetector.isSwapByDescription("Schöne 2-Zimmer-Wohnung. Kaution 2 Monate. Tausch nicht möglich."))
        assertFalse(SwapDetector.isSwapByDescription(null))
    }
}
