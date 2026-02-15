package pl.jsyty.linkstash.contracts.auth

import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.contracts.user.UserDto

@Serializable
data class AuthStartResponse(
    val url: String
)

@Serializable
enum class AuthSessionMode {
    COOKIE,
    BEARER
}

@Serializable
data class AuthExchangeRequest(
    val code: String,
    val state: String,
    val redirectUri: String,
    val codeVerifier: String? = null,
    val sessionMode: AuthSessionMode = AuthSessionMode.COOKIE
)

@Serializable
data class AuthExchangeResponse(
    val user: UserDto,
    val bearerToken: String? = null,
    val bearerTokenExpiresAtEpochSeconds: Long? = null
)
