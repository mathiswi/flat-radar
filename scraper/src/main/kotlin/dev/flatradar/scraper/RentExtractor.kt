package dev.flatradar.scraper

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * LLM-based fallback for rent extraction when Kleinanzeigen's structured attribute list
 * has no usable price fields. Targets German flat-ad free-text descriptions, e.g.
 *
 *   "Miete liegt bei 1100 Euro kalt zzgl. Nebenkosten."
 *   "Warmmiete 1.087 € inkl. 110 € NK und 110 € Heizung"
 *   "Auf Anfrage"  ->  all null
 *
 * Design:
 *   - Calls Gemini 3.1 Flash Lite with JSON-mode (responseMimeType "application/json") so the
 *     response is always parseable.
 *   - Returns null on missing config, failed call, or empty result.
 *
 * Cost: <1 Cent/month for typical flat-radar volume.
 *
 * This is the only class in the scraper that knows about the genai SDK; everything else
 * programs against the [RentFallback] interface.
 */
class RentExtractor private constructor(
    private val client: Client
) : RentFallback {

    override suspend fun extract(description: String): ParsedRents? = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(description)
        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .temperature(0.0f)
            .maxOutputTokens(200)
            .build()

        try {
            System.err.println(
                "[RentExtractor] sending 1 LLM request for description of ${description.length} chars"
            )
            val response: GenerateContentResponse = client.models.generateContent(MODEL, prompt, config)
            val text = response.text() ?: return@withContext null
            parseResponse(text)
        } catch (e: Exception) {
            System.err.println("[RentExtractor] LLM call failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val MODEL = "gemini-3.1-flash-lite"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Returns a [RentExtractor] backed by Gemini, or null when no API key is configured. */
        fun fromEnv(): RentExtractor? {
            val apiKey = Env.get("GEMINI_API_KEY")?.takeIf { it.isNotBlank() } ?: return null
            return RentExtractor(Client.builder().apiKey(apiKey).build())
        }

        internal fun parseResponse(text: String): ParsedRents? = try {
            val parsed = json.decodeFromString<ParsedRents>(text)
            parsed.takeUnless { it.isEmpty() }
        } catch (e: Exception) {
            System.err.println("[RentExtractor] could not parse LLM response as JSON: ${e.message}; raw=$text")
            null
        }

        internal fun buildPrompt(description: String): String = """
            You are a rent-price extractor for German real-estate listings.
            Read the ad text below and extract the four price components when present.

            Rules:
            - Output each amount as a plain integer, no currency symbol, no thousands separators, no decimal comma (e.g. write "1100", not "1.100" or "1100 €").
            - Map German terms as follows:
                "Kaltmiete" / "Grundmiete" / "Nettokaltmiete"  -> baseRent
                "Warmmiete" / "Gesamtmiete" / "Miete warm" / "inkl. Nebenkosten"  -> totalRent
                "Nebenkosten" / "NK"  (when given as an amount, not just "zzgl. NK")  -> sideCosts
                "Heizkosten" / "Heizung"  (when given as an amount)  -> heatingCosts
            - For any component not mentioned in the text, output null.
            - If no rent is mentioned at all (e.g. "Auf Anfrage"), output null for all four.
            - Reply with JSON ONLY, no explanations.

            Response schema:
            {"baseRent": int|null, "sideCosts": int|null, "heatingCosts": int|null, "totalRent": int|null}

            Ad text:
            """
            .trimIndent() + "\n\n" + description
    }
}