package dev.flatradar.backend

import dev.flatradar.shared.ApartmentAd
import org.h2.jdbcx.JdbcDataSource
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Repository-level tests for the notification outbox. See [ListingRoutesTest] for
 * the H2-in-PostgreSQL-mode rationale - same setup, just exercised directly
 * against [ListingRepository] instead of through HTTP routes, since there is no
 * route exposing the outbox (it's an internal implementation detail consumed by
 * [OutboxWorker]).
 */
class ListingRepositoryTest {

    private fun freshRepository(): ListingRepository {
        val name = "outboxtest${dbCounter.incrementAndGet()}"
        val dataSource: DataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        runMigrations(dataSource, "db/changelog/test-changelog.yaml")
        return ListingRepository(dataSource)
    }

    private fun sampleAd(id: String = "ad-1") = ApartmentAd(
        id = id,
        title = "Nice flat",
        size = 57.0,
        rooms = 2.0,
        bedrooms = null,
        bathrooms = null,
        floor = null,
        apartmentType = null,
        availableFrom = null,
        deposit = 1000,
        baseRent = 900,
        sideCosts = 100,
        heatingCosts = 50,
        totalRent = 1050,
        location = "22880 Niendorf",
        url = "https://example.de/$id",
        source = "kleinanzeigen",
        district = "Niendorf",
        timestamp = 1_750_000_000_000L,
    )

    @Test
    fun a_new_listing_gets_exactly_one_outbox_row() {
        val repository = freshRepository()

        repository.upsert(sampleAd("new-1"))

        val unsent = repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5)
        assertEquals(1, unsent.size)
        assertEquals("new-1", unsent.single().ad.id)
        assertEquals(0, unsent.single().attempts)
    }

    @Test
    fun re_ingesting_an_existing_listing_creates_no_extra_outbox_row() {
        val repository = freshRepository()

        repository.upsert(sampleAd("dup-1"))
        repository.upsert(sampleAd("dup-1"))

        val unsent = repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5)
        assertEquals(1, unsent.size)
    }

    @Test
    fun marking_an_outbox_row_sent_removes_it_from_the_unsent_query() {
        val repository = freshRepository()
        repository.upsert(sampleAd("sent-1"))
        val outboxId = repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).single().outboxId

        repository.markOutboxSent(outboxId)

        assertTrue(repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).isEmpty())
    }

    @Test
    fun a_failed_outbox_row_stays_unsent_but_records_the_attempt_until_the_cap() {
        val repository = freshRepository()
        repository.upsert(sampleAd("fail-1"))
        val outboxId = repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).single().outboxId

        repository.markOutboxFailed(outboxId, newAttemptCount = 1, error = "boom")

        val stillUnsent = repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5)
        assertEquals(1, stillUnsent.size)
        assertEquals(1, stillUnsent.single().attempts)

        repository.markOutboxFailed(outboxId, newAttemptCount = 5, error = "boom again")

        // maxAttempts = 5 excludes attempts >= 5 -> row no longer surfaces (dead-lettered).
        assertTrue(repository.fetchUnsentOutbox(limit = 10, maxAttempts = 5).isEmpty())
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
    }
}
