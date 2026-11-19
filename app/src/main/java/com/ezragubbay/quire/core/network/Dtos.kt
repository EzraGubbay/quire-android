package com.ezragubbay.quire.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON config: tolerate additive server fields (the mobile API evolves within v1). */
val QuireJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable
data class PairRequest(
    val code: String,
    val deviceName: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val createdAt: String? = null,
)

@Serializable
data class PairResponse(
    val token: String,
    val device: DeviceDto,
)

@Serializable
data class MeResponse(
    val login: String,
    val apiVersion: String,
    val device: DeviceDto,
)

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody,
)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String = "",
)
