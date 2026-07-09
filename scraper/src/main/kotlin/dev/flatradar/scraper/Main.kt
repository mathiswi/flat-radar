package dev.flatradar.scraper

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock

/**
 * One-shot scraper run.
 *
 * Loads feeds, fans out one coroutine per feed, parses search -> detail, prints
 * results. Designed to be invoked by host cron via `docker run --rm` and exit
 * cleanly. No internal `while`/`delay` loop - scheduling is external.
 *
 * Exit code 0 on success (even if some feeds failed - we don't want cron to
 * spam root with emails about intermittent parse failures). Process-level
 * failure (can't load feeds, no feeds configured) returns non-zero so the
 * operator notices a broken deployment.
 */
suspend fun main(args: Array<String>) {
    if (args.firstOrNull() == "diagnose") {
        val url = args.getOrNull(1) ?: run {
            System.err.println("usage: scrape diagnose <url>")
            return
        }
        Diagnose.run(url)
        return
    }

    val feeds = JsonFileFeeds()
    val rentFallback: RentFallback? = RentExtractor.fromEnv()
    val backendClient = BackendClient()

    val configs = feeds.all().filter { it.enabled }
    if (configs.isEmpty()) {
        System.err.println("[main] no enabled feeds configured; exiting")
        return
    }

    println("[main] running ${configs.size} feed(s)${if (rentFallback != null) " with LLM fallback" else ""}")

    val now = Clock.System.now().epochSeconds

    val results = coroutineScope {
        configs.map { feed ->
            async { processFeed(feed, rentFallback, now, backendClient) }
        }.awaitAll()
    }

    val total = results.flatten()
    println("[main] done: ${total.size} ad(s) parsed from ${configs.size} feed(s)")
    
    backendClient.close()
}

private suspend fun processFeed(
    feed: FeedConfig,
    rentFallback: RentFallback?,
    now: Long,
    backendClient: BackendClient,
): List<dev.flatradar.shared.ApartmentAd> {
    val parser = SourceParsers.get(feed.source)
    if (parser == null) {
        System.err.println("[${feed.id}] unknown source '${feed.source}'; skipping")
        return emptyList()
    }

    return try {
        val html = fetch(feed.url)
        val refs = parser.parseSearch(html, feed.district)
        println("[${feed.id}] ${refs.size} ad(s) on search page")

        val existingIds = backendClient.preFilter(refs.map { it.adId })
        val newRefs = refs.filter { it.adId !in existingIds }
        println("[${feed.id}] ${newRefs.size} new ad(s) after pre-filter (${existingIds.size} already exist)")

        val ads = coroutineScope {
            newRefs.map { ref ->
                async {
                    try {
                        val detailHtml = fetch(ref.url)
                        val ad = parser.parseDetail(detailHtml, ref.url, feed.district, now * 1000L, rentFallback)
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

        if (ads.isNotEmpty()) {
            try {
                val result = backendClient.ingestBatch(ads)
                println("[${feed.id}] ingested ${result.count ?: ads.size} ad(s)")
            } catch (e: Exception) {
                System.err.println("[${feed.id}] batch ingest failed: ${e.message}")
            }
        }

        ads
    } catch (e: Exception) {
        System.err.println("[${feed.id}] feed failed: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
        emptyList()
    }
}