package pl.jsyty.linkstash.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.server.config.AppConfig

class RaindropUnauthorizedException(message: String = "Raindrop unauthorized") : RuntimeException(message)

class RaindropUpstreamException(message: String) : RuntimeException(message)

@Serializable
data class RaindropTokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresInSeconds: Long? = null,
    val scope: String? = null
)

@Serializable
data class RaindropUserEnvelope(
    val result: Boolean? = null,
    val user: RaindropUserPayload? = null
)

@Serializable
data class RaindropUserPayload(
    @SerialName("_id")
    val id: Long? = null,
    @SerialName("\$id")
    val legacyId: Long? = null,
    val fullName: String? = null,
    val name: String? = null,
    val email: String? = null,
    val login: String? = null
) {
    fun stableId(): String? {
        return id?.toString() ?: legacyId?.toString()
    }

    fun displayName(): String? {
        return fullName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: login?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
    }
}

class RaindropClient(
    private val config: AppConfig,
    private val httpClient: HttpClient
) {
    fun buildAuthorizeUrl(state: String, redirectUri: String): String {
        return URLBuilder(config.raindropAuthorizeUrl).apply {
            parameters.append("client_id", config.raindropClientId)
            parameters.append("redirect_uri", redirectUri)
            parameters.append("response_type", "code")
            parameters.append("state", state)
        }.buildString()
    }

    suspend fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        codeVerifier: String?
    ): RaindropTokenResponse {
        val response = httpClient.post(config.raindropTokenUrl) {
            contentType(ContentType.Application.FormUrlEncoded)
            accept(ContentType.Application.Json)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", config.raindropClientId)
                        append("client_secret", config.raindropClientSecret)
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", redirectUri)
                        codeVerifier?.takeIf { it.isNotBlank() }?.let { append("code_verifier", it) }
                    }
                )
            )
        }

        return parseTokenResponse(response.status, response.bodyAsTextSafely(), "code exchange")
    }

    suspend fun refreshAccessToken(refreshToken: String): RaindropTokenResponse {
        val response = httpClient.post(config.raindropTokenUrl) {
            contentType(ContentType.Application.FormUrlEncoded)
            accept(ContentType.Application.Json)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("client_id", config.raindropClientId)
                        append("client_secret", config.raindropClientSecret)
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                    }
                )
            )
        }

        return parseTokenResponse(response.status, response.bodyAsTextSafely(), "token refresh")
    }

    suspend fun fetchCurrentUser(accessToken: String): RaindropUserPayload {
        val response = httpClient.get("${config.raindropApiBaseUrl}/user") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop user request failed (${response.status.value}): ${response.bodyAsTextSafely()}"
            )
        }

        val body = try {
            response.body<RaindropUserEnvelope>()
        } catch (_: ContentConvertException) {
            throw RaindropUpstreamException("Raindrop user response could not be parsed")
        }

        return body.user ?: throw RaindropUpstreamException("Raindrop user response missing user payload")
    }

    private fun parseTokenResponse(status: HttpStatusCode, responseBody: String, action: String): RaindropTokenResponse {
        if (status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException("Raindrop $action failed with unauthorized status")
        }

        if (!status.isSuccess()) {
            throw RaindropUpstreamException("Raindrop $action failed (${status.value}): $responseBody")
        }

        return try {
            LinkStashJsonReader.decodeRaindropTokenResponse(responseBody)
        } catch (error: Exception) {
            throw RaindropUpstreamException("Raindrop $action response could not be parsed")
        }
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsTextSafely(): String {
    return runCatching { body<String>() }.getOrDefault("")
}

private object LinkStashJsonReader {
    private val json = pl.jsyty.linkstash.contracts.LinkStashJson.instance

    fun decodeRaindropTokenResponse(raw: String): RaindropTokenResponse {
        return json.decodeFromString(RaindropTokenResponse.serializer(), raw)
    }
}
