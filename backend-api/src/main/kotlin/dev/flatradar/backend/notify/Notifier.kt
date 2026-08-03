package dev.flatradar.backend.notify

import dev.flatradar.shared.ApartmentAd

/**
 * Delivers a "new listing" alert for [ad]. Implementations should throw on any
 * failure (network error, non-2xx response) so [dev.flatradar.backend.OutboxWorker]
 * can record the attempt and retry later - a swallowed exception here would look
 * like a silently lost notification.
 *
 * This boundary is deliberately narrow (one listing in, nothing out) so a future
 * delivery mechanism - a second channel (Telegram, email) or a message-broker
 * publisher (e.g. RabbitMQ) instead of a direct HTTP call - is a new
 * implementation of this interface, not a change to [dev.flatradar.backend.OutboxWorker]
 * or [dev.flatradar.backend.ListingRepository].
 */
interface Notifier {
    suspend fun notify(ad: ApartmentAd)
}
