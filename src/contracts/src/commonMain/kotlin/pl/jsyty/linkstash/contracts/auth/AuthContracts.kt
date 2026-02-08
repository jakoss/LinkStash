package pl.jsyty.linkstash.contracts.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthStartResponse(
    val url: String
)

@Serializable
data class AuthExchangeRequest(
    val code: String,
    val state: String,
    val redirectUri: String,
    val codeVerifier: String? = null
)
