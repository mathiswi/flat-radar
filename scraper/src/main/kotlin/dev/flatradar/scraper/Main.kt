package dev.flatradar.scraper

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
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
    val httpClient = HttpClient(Java) {
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

        val feeds = JsonFileFeeds()
        val rentFallback: RentFallback? = RentExtractor.fromEnv()
        val backendClient = BackendClient(httpClient)
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

        println("[main] running ${configs.size} feed(s)${if (rentFallback != null) " with LLM fallback" else ""}")

        val timestamp = Clock.System.now().toEpochMilliseconds()

        val results = coroutineScope {
            configs.map { feed ->
                async { processFeed(feed, rentFallback, timestamp, backendClient, httpClient, detailFetchLimiter) }
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
    rentFallback: RentFallback?,
    timestamp: Long,
    backendClient: BackendClient,
    httpClient: HttpClient,
    detailFetchLimiter: Semaphore,
): List<dev.flatradar.shared.ApartmentAd> {
    val parser = SourceParsers.get(feed.source)
    if (parser == null) {
        System.err.println("[${feed.id}] unknown source '${feed.source}'; skipping")
        return emptyList()
    }

    return try {
        val html = fetch(httpClient, feed.url)
        val refs = parser.parseSearch(html, feed.district)
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
                        val ad = parser.parseDetail(detailHtml, ref.url, feed.district, timestamp, rentFallback)
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

        ads
    } catch (e: Exception) {
        System.err.println("[${feed.id}] feed failed: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}
