package dev.flatradar.scraper

import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration test that calls the real Gemini API. Excluded from normal `test`
 * runs unless GEMINI_API_KEY is set in the environment. Run explicitly:
 *
 *   GEMINI_API_KEY=xxx ./gradlew :scraper:test --tests '*RentExtractorIntegrationTest' --rerun-tasks
 *
 * This covers the one-liner network glue in [RentExtractor.extract] that the unit
 * tests in [RentExtractorTest] deliberately skip. Input is the real description
 * of a current Barmbek-Nord ad (fixtures/mock/detail_normal.html).
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class RentExtractorIntegrationTest {

    @Test
    fun extracts_rents_from_real_ad_description() = runTest {
        val extractor = RentExtractor.fromEnv()!!
        val description = descriptionFromFixture("/mock/kleinanzeigen/detail_normal.html")

        val parsed = extractor.extract(description)

        assertNotNull(parsed, "expected non-null rents from Gemini")
        assertEquals(1100, parsed?.baseRent, "Miete liegt bei 1100 Euro kalt -> baseRent 1100")
        assertNull(parsed?.sideCosts, "zzgl. Nebenkosten mentioned without amount -> null")
        assertNull(parsed?.heatingCosts, "Heizkosten not mentioned -> null")
        assertNull(parsed?.totalRent, "Warmmiete not mentioned -> null")
    }

    @Test
    fun returns_null_for_auf_anfrage() = runTest {
        val extractor = RentExtractor.fromEnv()!!
        val parsed = extractor.extract("Preis auf Anfrage.")
        // Gemini should return either null (all-null -> we collapse to null) or no rents.
        assertTrue(parsed == null || parsed?.isEmpty() == true)
    }

    private fun descriptionFromFixture(path: String): String =
        loadResource(path)
            .let { Jsoup.parse(it) }
            .selectFirst("#viewad-description-text")
            ?.text()
            ?: error("fixture $path missing #viewad-description-text")
}