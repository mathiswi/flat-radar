package dev.flatradar.scraper

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches [url] through [client]. `mock://<path>` URLs are read from the
 * `/mock/<path>` classpath resource and never touch [client] or the network,
 * so tests and [Diagnose] can share this function without a real HttpClient.
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

private suspend fun fetchHttp(client: HttpClient, url: String): String {
    return client.request(url) {
        method = HttpMethod.Get
        header("User-Agent", BROWSER_UA)
        header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
        header("Accept-Encoding", "gzip, deflate, br")
        header("Connection", "keep-alive")
        header("Upgrade-Insecure-Requests", "1")
    }.bodyAsText()
}

private const val BROWSER_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
