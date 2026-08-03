package dev.flatradar.backend

import dev.flatradar.backend.notify.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger("OutboxWorker")

/**
 * Polls [ListingRepository]'s notification outbox and delivers each unsent row
 * through [notifier], marking it sent on success or recording the failure so the
 * next poll retries it (up to [maxAttempts]).
 *
 * Runs as a plain infinite loop - [delay] is cooperatively cancellable, so
 * cancelling the coroutine this is launched in (Ktor cancels the whole
 * `Application` scope on shutdown, see [Application.module]) stops the loop
 * cleanly without any extra flag/lock.
 */
class OutboxWorker(
    private val repository: ListingRepository,
    private val notifier: Notifier,
    private val pollInterval: Duration = 5.seconds,
    private val batchSize: Int = 20,
    private val maxAttempts: Int = 5,
) {
    suspend fun run() {
        logger.info("[outbox] worker started (poll every ${pollInterval.inWholeSeconds}s, max $maxAttempts attempts)")
        while (true) {
            try {
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("[outbox] poll iteration failed", e)
            }
            delay(pollInterval)
        }
    }

    /** One poll-fetch-deliver iteration, exposed `internal` so tests can drive it deterministically instead of racing [run]'s loop/delay. */
    internal suspend fun pollOnce() {
        val items = withContext(Dispatchers.IO) { repository.fetchUnsentOutbox(batchSize, maxAttempts) }
        for (item in items) {
            try {
                notifier.notify(item.ad)
                withContext(Dispatchers.IO) { repository.markOutboxSent(item.outboxId) }
                logger.info("[outbox] notified: ${item.ad.id}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val attempts = item.attempts + 1
                withContext(Dispatchers.IO) {
                    repository.markOutboxFailed(item.outboxId, attempts, e.message ?: e::class.simpleName ?: "unknown error")
                }
                if (attempts >= maxAttempts) {
                    logger.error("[outbox] giving up on ${item.ad.id} after $attempts attempts: ${e.message}")
                } else {
                    logger.warn("[outbox] attempt $attempts/$maxAttempts failed for ${item.ad.id}: ${e.message}")
                }
            }
        }
    }
}
