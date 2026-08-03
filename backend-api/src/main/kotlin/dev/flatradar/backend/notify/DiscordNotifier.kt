package dev.flatradar.backend.notify

import dev.flatradar.shared.ApartmentAd
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Posts a Discord webhook embed for [ad]. One retry after honoring the
 * `retry_after` Discord returns on HTTP 429 (see [extractRetryAfterSeconds]);
 * any other failure (or a second 429) is thrown so [dev.flatradar.backend.OutboxWorker]
 * records the attempt and tries again on its next poll.
 */
class DiscordNotifier(
    private val client: HttpClient,
    private val webhookUrl: String,
) : Notifier {

    private val json = Json { encodeDefaults = false }

    override suspend fun notify(ad: ApartmentAd) {
        val body = json.encodeToString(DiscordWebhookPayload.serializer(), buildPayload(ad))
        postWithRetry(body)
    }

    private suspend fun postWithRetry(body: String, isRetry: Boolean = false) {
        val response = client.post(webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (response.status == HttpStatusCode.TooManyRequests && !isRetry) {
            delay(extractRetryAfterSeconds(response).seconds)
            return postWithRetry(body, isRetry = true)
        }

        check(response.status.isSuccess()) {
            "Discord webhook returned ${response.status}: ${response.bodyAsText().take(500)}"
        }
    }

    /**
     * Discord sends the cooldown both as a `Retry-After` header (seconds, per
     * standard HTTP semantics) and as a `retry_after` field in the JSON body
     * (fractional seconds) - either may be present depending on which rate
     * limit tier was hit. Falls back to 1s if neither parses.
     */
    private suspend fun extractRetryAfterSeconds(response: HttpResponse): Double {
        response.headers["Retry-After"]?.toDoubleOrNull()?.let { return it }
        return runCatching {
            (Json.parseToJsonElement(response.bodyAsText()) as? JsonObject)
                ?.get("retry_after")
                ?.let { it as? JsonPrimitive }
                ?.content
                ?.toDoubleOrNull()
        }.getOrNull() ?: 1.0
    }

    /** `internal` (rather than `private`) so tests can assert the payload shape without a live webhook call. */
    internal fun buildPayload(ad: ApartmentAd): DiscordWebhookPayload {
        val fields = buildList {
            rentField(ad)?.let { add(it) }
            ad.size?.let { add(DiscordEmbedField(name = "Größe", value = "${it.toInt()} m²")) }
            ad.rooms?.let { add(DiscordEmbedField(name = "Zimmer", value = formatRooms(it))) }
            (ad.district ?: ad.location.takeIf { it.isNotBlank() })?.let {
                add(DiscordEmbedField(name = "Ort", value = it))
            }
            ad.distanceMeters?.let { add(DiscordEmbedField(name = "Entfernung", value = formatDistance(it))) }
            add(DiscordEmbedField(name = "Quelle", value = ad.source, inline = true))
        }

        return DiscordWebhookPayload(
            embeds = listOf(
                DiscordEmbed(
                    title = ad.title.take(MAX_TITLE_LENGTH),
                    url = ad.url,
                    color = EMBED_COLOR,
                    fields = fields,
                )
            )
        )
    }

    private fun rentField(ad: ApartmentAd): DiscordEmbedField? {
        val value = ad.totalRent ?: ad.baseRent ?: return null
        val label = if (ad.totalRent != null) "Warmmiete" else "Kaltmiete"
        return DiscordEmbedField(name = label, value = "$value €")
    }

    private fun formatRooms(rooms: Double): String =
        if (rooms == rooms.toInt().toDouble()) "${rooms.toInt()}" else rooms.toString()

    private fun formatDistance(meters: Int): String =
        if (meters >= 1000) "%.1f km".format(meters / 1000.0) else "$meters m"

    private companion object {
        const val MAX_TITLE_LENGTH = 256
        const val EMBED_COLOR = 0x2ECC71
    }
}

@Serializable
data class DiscordWebhookPayload(val embeds: List<DiscordEmbed>)

@Serializable
data class DiscordEmbed(
    val title: String,
    val url: String? = null,
    val color: Int? = null,
    val fields: List<DiscordEmbedField> = emptyList(),
)

@Serializable
data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = true,
)
