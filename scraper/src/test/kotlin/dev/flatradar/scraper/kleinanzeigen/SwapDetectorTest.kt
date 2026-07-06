package dev.flatradar.scraper.kleinanzeigen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwapDetectorTest {

    @Test
    fun title_signal() {
        assertTrue(SwapDetector.isSwapByTitle("TAUSCHWOHNUNG - Hamburg Nord"))
        assertFalse(SwapDetector.isSwapByTitle("Schöne 2-Zi-Wohnung"))
        assertFalse(SwapDetector.isSwapByTitle(null))
    }

    @Test
    fun slug_signal() {
        assertTrue(SwapDetector.isSwapBySlug("/s-anzeige/tauschwohnung-gemuetliche-wohnung/3425649112-203-9480"))
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
        assertFalse(SwapDetector.isSwapByDescription("Es handelt sich hierbei um ein Tauschangebot."))
    }
}