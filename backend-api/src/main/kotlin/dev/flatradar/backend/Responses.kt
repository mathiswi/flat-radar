package dev.flatradar.backend

import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(val status: String)

@Serializable
data class ReadyResponse(val status: String, val error: String? = null)

@Serializable
data class ErrorResponse(val error: String)
