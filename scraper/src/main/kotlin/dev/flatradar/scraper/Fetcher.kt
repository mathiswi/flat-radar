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

private suspend fun fetchHttp(client: HttpClient, url: String): String =
    if (isImmoscoutMobileApi(url)) fetchImmoscoutMobile(client, url) else fetchBrowser(client, url)

private fun isImmoscoutMobileApi(url: String): Boolean =
    url.startsWith("https://$IMMOSCOUT_MOBILE_HOST/")

private suspend fun fetchBrowser(client: HttpClient, url: String): String {
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
