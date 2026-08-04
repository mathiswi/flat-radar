package dev.flatradar.scraper

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * Fetches [url] through [client]. `mock://<path>` URLs are read from the
 * `/mock/<path>` classpath resource. `https://www.kleinanzeigen.de/...` URLs
 * are fetched via a `curl` subprocess because Ktor's Java/CIO engines trigger
 * TLS fingerprinting that causes Kleinanzeigen to serve a stripped-down page
 * without listing markup. Other `https://` URLs use [client] as before.
 */
suspend fun fetch(client: HttpClient, url: String): String = withContext(Dispatchers.IO) {
    when {
        url.startsWith("mock://") -> {
            val resource = url.removePrefix("mock://")
            loadResource("/mock/$resource")
        }
        url.startsWith("https://") -> fetchHttp(client, url)
        else -> throw IllegalArgumentException("Unsupported URL scheme: $url")
    }
}

private suspend fun fetchHttp(client: HttpClient, url: String): String = when {
    isImmoscoutMobileApi(url) -> fetchImmoscoutMobile(client, url)
    isKleinanzeigen(url) -> fetchWithCurl(url)
    else -> fetchBrowser(client, url)
}

private fun isKleinanzeigen(url: String): Boolean =
    url.startsWith("https://www.kleinanzeigen.de/")

private fun isImmoscoutMobileApi(url: String): Boolean =
    url.startsWith("https://$IMMOSCOUT_MOBILE_HOST/")

private suspend fun fetchBrowser(client: HttpClient, url: String): String {
    return client.request(url) {
        method = HttpMethod.Get
        header("User-Agent", BROWSER_UA)
        header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
        header("Connection", "keep-alive")
        header("Upgrade-Insecure-Requests", "1")
    }.bodyAsText()
}

/**
 * Falls back to an external `curl` process for Kleinanzeigen because the
 * JVM's TLS fingerprint (both Java and CIO engines) triggers the CDN to serve
 * a stripped-down page without listing markup.
 */
private suspend fun fetchWithCurl(url: String): String = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(
        "curl", "-sS", "--compressed",
        "-H", "User-Agent: $BROWSER_UA",
        "-H", "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "-H", "Accept-Language: de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
        url
    )
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    if (exit != 0) throw RuntimeException("curl exited $exit for $url: ${output.take(300)}")
    output
}

/**
 * The mobile-app backend needs only a device User-Agent - no auth, no cookies (see
 * plan `immoscout24_scraper_via_mobile_api`). `/search/list` is a POST with a static
 * JSON body (its documented shape; the actual filter criteria live in the query
 * string); every other path (e.g. `/expose/{id}`) is a plain GET.
 */
private suspend fun fetchImmoscoutMobile(client: HttpClient, url: String): String {
    val isSearchList = URI(url).path == "/search/list"
    return client.request(url) {
        method = if (isSearchList) HttpMethod.Post else HttpMethod.Get
        header("User-Agent", IMMOSCOUT_MOBILE_UA)
        header("Accept", "application/json")
        if (isSearchList) {
            contentType(ContentType.Application.Json)
            setBody(IMMOSCOUT_SEARCH_BODY)
        }
    }.bodyAsText()
}

private const val BROWSER_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private const val IMMOSCOUT_MOBILE_HOST = "api.mobile.immobilienscout24.de"
private const val IMMOSCOUT_MOBILE_UA = "ImmoScout_27.12_26.2_._"
private const val IMMOSCOUT_SEARCH_BODY = """{"supportedResultListType":[],"userData":{}}"""
