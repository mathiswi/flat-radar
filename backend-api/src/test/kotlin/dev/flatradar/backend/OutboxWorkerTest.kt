package dev.flatradar.backend

import dev.flatradar.backend.notify.Notifier
import dev.flatradar.shared.ApartmentAd
import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [Notifier] fake that either records the delivered ad or throws, depending on [shouldFail]. */
private class FakeNotifier(private val shouldFail: (ApartmentAd) -> Boolean = { false }) : Notifier {
    val delivered = mutableListOf<ApartmentAd>()

    override suspend fun notify(ad: ApartmentAd) {
        if (shouldFail(ad)) error("simulated delivery failure for ${ad.id}")
        delivered += ad
    }
}

class OutboxWorkerTest {

    private fun freshRepository(): ListingRepository {
        val name = "workertest${dbCounter.incrementAndGet()}"
        val dataSource: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        runMigrations(dataSource, "db/changelog/test-changelog.yaml")
        return ListingRepository(dataSource)
    }

    private fun sampleAd(id: String) = ApartmentAd(
        id = id,
        title = "Nice flat $id",
        size = 57.0,
        rooms = 2.0,
        bedrooms = null,
        bathrooms = null,
        floor = null,
        apartmentType = null,
        availableFrom = null,
        deposit = null,
        baseRent = 900,
        sideCosts = null,
        heatingCosts = null,
        totalRent = 1050,
        location = "22880 Niendorf",
        url = "https://example.de/$id",
        source = "kleinanzeigen",
        district = "Niendorf",
        timestamp = 1_750_000_000_000L,
    )

    @Test
    fun a_pending_listing_is_delivered_and_marked_sent() = runTest {
        val repository = freshRepository()
        repository.upsert(sampleAd("a"))
        val notifier = FakeNotifier()
        val worker = OutboxWorker(repository, notifier, maxAttempts = 5)

        worker.pollOnce()

        assertEquals(listOf("a"), notifier.delivered.map { it.id })
        assertTrue(repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).isEmpty())
    }

    @Test
    fun a_failing_notifier_records_the_attempt_and_the_row_is_retried_next_poll() = runTest {
        val repository = freshRepository()
        repository.upsert(sampleAd("b"))
        var failuresLeft = 2
        val notifier = FakeNotifier(shouldFail = { failuresLeft-- > 0 })
        val worker = OutboxWorker(repository, notifier, maxAttempts = 5)

        worker.pollOnce() // fails, attempts -> 1
        worker.pollOnce() // fails, attempts -> 2
        worker.pollOnce() // succeeds

        assertEquals(listOf("b"), notifier.delivered.map { it.id })
        assertTrue(repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).isEmpty())
    }

    @Test
    fun a_notifier_that_always_fails_stops_being_retried_once_max_attempts_is_reached() = runTest {
        val repository = freshRepository()
        repository.upsert(sampleAd("c"))
        val notifier = FakeNotifier(shouldFail = { true })
        val worker = OutboxWorker(repository, notifier, maxAttempts = 3)

        repeat(5) { worker.pollOnce() }

        assertTrue(notifier.delivered.isEmpty())
        // After 3 recorded attempts the row drops out of the maxAttempts-bounded query
        // (dead-lettered) instead of being retried forever.
        assertTrue(repository.fetchUnsentOutbox(limit = 10, maxAttempts = 3).isEmpty())
    }

    @Test
    fun multiple_pending_listings_are_all_delivered_in_one_poll() = runTest {
        val repository = freshRepository()
        repository.upsert(sampleAd("x"))
        repository.upsert(sampleAd("y"))
        repository.upsert(sampleAd("z"))
        val notifier = FakeNotifier()
        val worker = OutboxWorker(repository, notifier, maxAttempts = 5)

        worker.pollOnce()

        assertEquals(setOf("x", "y", "z"), notifier.delivered.map { it.id }.toSet())
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
    }
}
