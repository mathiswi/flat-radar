package dev.flatradar.backend

import kotlinx.serialization.Serializable

const val API_V1 = "/api/v1"

@Serializable
data class StatusResponse(val status: String)

@Serializable
data class ReadyResponse(val status: String, val error: String? = null)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class StatsResponse(
    val totalListings: Int,
    val pendingOutbox: Int,
    val deadLettered: Int,
    val lastScrape: Long?,  // epoch millis; null when no listings have been ingested yet
)
