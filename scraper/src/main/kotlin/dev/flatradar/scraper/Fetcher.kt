package dev.flatradar.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
    when {
        url.startsWith("mock://") -> {
            val resource = url.removePrefix("mock://")
            loadResource("/mock/$resource")
        }
        url.startsWith("https://") -> {
            TODO("Ktor client for real HTTP - switch feeds.json URL from mock:// to https:// to activate")
        }
        else -> throw IllegalArgumentException("Unsupported URL scheme: $url")
    }
}