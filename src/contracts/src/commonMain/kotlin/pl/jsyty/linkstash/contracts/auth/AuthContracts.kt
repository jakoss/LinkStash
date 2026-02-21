package pl.jsyty.linkstash.contracts.auth

import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.contracts.user.UserDto

@Serializable
enum class AuthSessionMode {
    COOKIE,
    BEARER
}

@Serializable
data class AuthRaindropTokenExchangeRequest(
    val accessToken: String,
    val sessionMode: AuthSessionMode = AuthSessionMode.COOKIE
)

@Serializable
data class AuthExchangeResponse(
    val user: UserDto,
    val bearerToken: String? = null,
    val bearerTokenExpiresAtEpochSeconds: Long? = null
)
