package dev.flatradar.scraper

import dev.flatradar.shared.FeedConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.system.exitProcess

/** Shared across all feeds: the whole point is a single global rate limit, not one per feed. */
private const val DETAIL_FETCH_PERMITS = 3
private val DETAIL_FETCH_DELAY_RANGE = 500L..1500L

/**
 * One-shot scraper run.
 *
 * Loads feeds, fans out one coroutine per feed, parses search -> detail, prints
 * results. Designed to be invoked by host cron via `docker run --rm` and exit
 * cleanly. No internal `while`/`delay` loop - scheduling is external.
 *
 * Exit code 0 on success (even if some feeds failed - we don't want cron to
 * spam root with emails about intermittent parse failures). Process-level
 * failure (can't load feeds, no feeds configured) exits 1 so the operator
 * notices a broken deployment.
 */
suspend fun main(args: Array<String>) {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    try {
        if (args.firstOrNull() == "diagnose") {
            val url = args.getOrNull(1) ?: run {
                System.err.println("usage: scrape diagnose <url>")
                return
            }
            Diagnose.run(httpClient, url)
            return
        }

        val backendClient = BackendClient(httpClient)
        val feeds = BackendFeeds(backendClient)
        val availabilityFallback = AvailabilityExtractor.fromEnv()
        val detailFetchLimiter = Semaphore(DETAIL_FETCH_PERMITS)

        val configs = try {
            feeds.all().filter { it.enabled }
        } catch (e: Exception) {
            System.err.println("[main] failed to load feeds: ${e.message}")
            exitProcess(1)
        }
        if (configs.isEmpty()) {
            System.err.println("[main] no enabled feeds configured; exiting")
            exitProcess(1)
        }

        println("[main] running ${configs.size} feed(s)${if (availabilityFallback != null) " with LLM availability fallback" else ""}")

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val results = coroutineScope {
            configs.map { feed ->
                async { processFeed(feed, timestamp, backendClient, httpClient, detailFetchLimiter, availabilityFallback) }
            }.awaitAll()
        }

        val total = results.flatten()
        println("[main] done: ${total.size} ad(s) parsed from ${configs.size} feed(s)")
    } finally {
        httpClient.close()
    }
}

private suspend fun processFeed(
    feed: FeedConfig,
    timestamp: Long,
    backendClient: BackendClient,
    httpClient: HttpClient,
    detailFetchLimiter: Semaphore,
    availabilityFallback: AvailabilityFallback?,
): List<dev.flatradar.shared.ApartmentAd> {
    val parser = SourceParsers.get(feed.source)
    if (parser == null) {
        System.err.println("[${feed.id}] unknown source '${feed.source}'; skipping")
        return emptyList()
    }

    return try {
        val searchUrl = parser.searchUrl(feed.url)
        val firstPage = fetch(httpClient, searchUrl)
        val refs = fetchAllSearchPages(parser, searchUrl, firstPage, httpClient, detailFetchLimiter)
        println("[${feed.id}] ${refs.size} ad(s) on search page")

        val existingIds = backendClient.preFilter(refs.map { it.adId })
        val newRefs = refs.filter { it.adId !in existingIds }
        println("[${feed.id}] ${newRefs.size} new ad(s) after pre-filter (${existingIds.size} already exist)")

        val ads = coroutineScope {
            newRefs.map { ref ->
                async {
                    try {
                        val detailHtml = detailFetchLimiter.withPermit {
                            delay(Random.nextLong(DETAIL_FETCH_DELAY_RANGE.first, DETAIL_FETCH_DELAY_RANGE.last))
                            fetch(httpClient, ref.url)
                        }
                        val ad = parser.parseDetail(detailHtml, ref.url, feed.district, timestamp, ref, availabilityFallback)
                        if (ad == null) {
                            println("[${feed.id}] skip (null): ${ref.adId} ${ref.title}")
                        } else {
                            println("[${feed.id}] ok: ${ref.adId} ${ref.title}")
                        }
                        ad
                    } catch (e: Exception) {
                        System.err.println("[${feed.id}] error: ${ref.adId} ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        for (ad in ads) {
            try {
                val inserted = backendClient.ingest(ad)
                println("[${feed.id}] ${if (inserted) "ingested" else "already exists"}: ${ad.id}")
            } catch (e: Exception) {
                System.err.println("[${feed.id}] ingest failed: ${ad.id} ${e.message}")
            }
        }

        // Report the full visible set for delisting reconcile. Kept inside the try, after a
        // successful search+pagination parse, so a failed run never reports a partial set
        // (which would falsely delist the ads it couldn't fetch). Runs *after* ingest so
        // this run's brand-new rows already exist when reconcile claims their feed_id -
        // otherwise a listing visible for only one run (churned/reposted ads get a fresh
        // id each time) would keep feed_id NULL forever and never become delistable.
        try {
            val delisted = backendClient.reportSeen(feed.id, refs.map { it.adId })
            if (delisted > 0) println("[${feed.id}] marked $delisted listing(s) delisted")
        } catch (e: Exception) {
            System.err.println("[${feed.id}] seen-report failed: ${e.message}")
        }

        ads
    } catch (e: Exception) {
        System.err.println("[${feed.id}] feed failed: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}

/**
 * Parses the already-fetched first search-result page, then - for paginated
 * sources like immoscout24 - fetches and parses any remaining pages through the
 * same [detailFetchLimiter] used for detail fetches. Single-page sources (the
 * default [SourceParser.nextSearchPageUrls]) never enter the extra-pages branch,
 * so this is a no-op wrapper for them beyond the initial [SourceParser.parseSearch] call.
 */
private suspend fun fetchAllSearchPages(
    parser: SourceParser,
    firstPageUrl: String,
    firstPageResponse: String,
    httpClient: HttpClient,
    detailFetchLimiter: Semaphore,
): List<AdRef> {
    val firstPageRefs = parser.parseSearch(firstPageResponse)
    val extraPageUrls = parser.nextSearchPageUrls(firstPageUrl, firstPageResponse)
    if (extraPageUrls.isEmpty()) return firstPageRefs

    val extraRefs = coroutineScope {
        extraPageUrls.map { pageUrl ->
            async {
                detailFetchLimiter.withPermit {
                    delay(Random.nextLong(DETAIL_FETCH_DELAY_RANGE.first, DETAIL_FETCH_DELAY_RANGE.last))
                    parser.parseSearch(fetch(httpClient, pageUrl))
                }
            }
        }.awaitAll().flatten()
    }
    return firstPageRefs + extraRefs
}
