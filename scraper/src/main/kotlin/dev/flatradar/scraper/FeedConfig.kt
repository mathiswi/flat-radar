package dev.flatradar.scraper

import kotlinx.serialization.Serializable

@Serializable
data class FeedConfig(
    val id: String,
    val displayName: String,
    val url: String,
    val district: String,
    val source: String = "kleinanzeigen",
    val enabled: Boolean = true
)