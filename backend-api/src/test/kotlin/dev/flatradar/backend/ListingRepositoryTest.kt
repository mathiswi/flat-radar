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

    // --- Delisting reconcile (reconcileSeen) ---

    private fun ListingRepository.delistedAtOf(id: String): Long? =
        findAll().first { it.id == id }.delistedAt

    @Test
    fun reconcile_marks_a_listing_delisted_after_threshold_consecutive_misses() {
        val repository = freshRepository()
        repository.upsert(sampleAd("keep"))
        repository.upsert(sampleAd("gone"))

        // First run claims both for the feed; nothing delisted yet.
        assertEquals(0, repository.reconcileSeen("feed1", listOf("keep", "gone"), threshold = 2))

        // Miss #1: below threshold, still listed.
        assertEquals(0, repository.reconcileSeen("feed1", listOf("keep"), threshold = 2))
        assertEquals(null, repository.delistedAtOf("gone"))

        // Miss #2: reaches threshold -> delisted (and reported as one newly delisted).
        assertEquals(1, repository.reconcileSeen("feed1", listOf("keep"), threshold = 2))
        assertTrue(repository.delistedAtOf("gone") != null)
        assertEquals(null, repository.delistedAtOf("keep"))
    }

    @Test
    fun reconcile_clears_delisting_when_a_listing_reappears() {
        val repository = freshRepository()
        repository.upsert(sampleAd("flap"))
        repository.reconcileSeen("feed1", listOf("flap"), threshold = 1) // claim ownership
        repository.reconcileSeen("feed1", listOf("someone-else"), threshold = 1) // flap absent -> delisted
        assertTrue(repository.delistedAtOf("flap") != null, "should be delisted after a miss at threshold 1")

        repository.reconcileSeen("feed1", listOf("flap"), threshold = 1)
        assertEquals(null, repository.delistedAtOf("flap"), "reappearing clears delisting")
    }

    @Test
    fun reconcile_ignores_an_empty_seen_set() {
        val repository = freshRepository()
        repository.upsert(sampleAd("safe"))
        repository.reconcileSeen("feed1", listOf("safe"), threshold = 1) // claim

        // An empty report is inconclusive (soft-block / genuinely-empty page) and must
        // not advance miss counters or delist anything.
        assertEquals(0, repository.reconcileSeen("feed1", emptyList(), threshold = 1))
        assertEquals(null, repository.delistedAtOf("safe"))
    }

    @Test
    fun reconcile_only_touches_its_own_feeds_listings() {
        val repository = freshRepository()
        repository.upsert(sampleAd("owned-by-1"))
        repository.reconcileSeen("feed1", listOf("owned-by-1"), threshold = 1) // feed1 claims it

        // feed2 reporting a different id must not delist feed1's listing, even past threshold.
        repository.reconcileSeen("feed2", listOf("owned-by-2"), threshold = 1)
        repository.reconcileSeen("feed2", listOf("owned-by-2"), threshold = 1)
        assertEquals(null, repository.delistedAtOf("owned-by-1"))
    }

    private companion object {
        val dbCounter = AtomicInteger(0)
    }
}
