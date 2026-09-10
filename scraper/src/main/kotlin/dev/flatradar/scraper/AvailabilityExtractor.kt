package dev.flatradar.scraper

import com.google.genai.Client
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM-based last-resort fallback for the "Verfügbar ab" (move-in) date, used only when
 * an ad has no structured "Verfügbar ab" attribute row AND the cheap deterministic scan
 * ([kleinanzeigen.KleinanzeigenFormats.parseAvailabilityFromText]) found nothing — i.e.
 * the date is phrased loosely in the description, e.g.
 *
 *   "frei ab Dezember"                  -> first of the next December >= today
 *   "Bezug ab dem 1. Oktober 2026"      -> 2026-10-01
 *   "ab sofort beziehbar"               -> today
 *   "auf Anfrage" / no date mentioned   -> null
 *
 * Design (mirrors the former RentExtractor removed in 5605a92):
 *   - Gemini 3.1 Flash Lite with JSON-mode (responseMimeType "application/json") so the
 *     response is always parseable.
 *   - Returns null on missing config, failed call, or empty/unparseable result.
 *
 * This is the only class in the scraper that knows about the genai SDK; everything else
 * programs against the [AvailabilityFallback] interface.
 */
class AvailabilityExtractor private constructor(
    private val client: Client,
) : AvailabilityFallback {

    override suspend fun extract(description: String): LocalDate? = withContext(Dispatchers.IO) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Berlin")).date
        val prompt = buildPrompt(description, today)
        val config = GenerateContentConfig.builder()
            .responseMimeType("application/json")
            .temperature(0.0f)
            .maxOutputTokens(60)
            .build()

        try {
            System.err.println(
                "[AvailabilityExtractor] sending 1 LLM request for description of ${description.length} chars"
            )
            val response: GenerateContentResponse = client.models.generateContent(MODEL, prompt, config)
            val text = response.text() ?: return@withContext null
            parseResponse(text)
        } catch (e: Exception) {
            System.err.println("[AvailabilityExtractor] LLM call failed: ${e.message}")
            null
        }
    }

    @Serializable
    private data class Parsed(val availableFrom: String? = null)

    companion object {
        private const val MODEL = "gemini-3.1-flash-lite"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Returns an [AvailabilityExtractor] backed by Gemini, or null when no API key is configured. */
        fun fromEnv(): AvailabilityExtractor? {
            val apiKey = Env.get("GEMINI_API_KEY")?.takeIf { it.isNotBlank() } ?: return null
            return AvailabilityExtractor(Client.builder().apiKey(apiKey).build())
        }

        internal fun parseResponse(text: String): LocalDate? = try {
            val iso = json.decodeFromString<Parsed>(text).availableFrom?.trim()?.takeIf { it.isNotEmpty() }
            iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        } catch (e: Exception) {
            System.err.println("[AvailabilityExtractor] could not parse LLM response as JSON: ${e.message}; raw=$text")
            null
        }

        internal fun buildPrompt(description: String, today: LocalDate): String = """
            You extract the move-in date ("Verfügbar ab" / "frei ab" / "bezugsfrei ab") from
            a German real-estate ad description. Today's date is $today.

            Rules:
            - Output the date as an ISO string "YYYY-MM-DD".
            - A concrete date -> that date (e.g. "01.10.2026", "1.10.26", "1. Oktober 2026" -> "2026-10-01").
            - A bare month with no day ("frei ab Dezember") -> the 1st of the next occurrence of
              that month on or after today.
            - "sofort" / "ab sofort" / "sofort beziehbar" / "sofort verfügbar" -> today's date ($today).
            - If no move-in date is stated at all (e.g. "auf Anfrage", or the text never mentions
              availability), output null.
            - Reply with JSON ONLY, no explanations.

            Response schema:
            {"availableFrom": "YYYY-MM-DD" | null}

            Ad description:
            """
            .trimIndent() + "\n\n" + description
    }
}
