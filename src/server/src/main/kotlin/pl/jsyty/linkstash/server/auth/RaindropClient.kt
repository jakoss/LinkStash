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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
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

@Serializable
data class RaindropCollectionPayload(
    @SerialName("_id")
    val id: Long? = null,
    val title: String? = null,
    val parent: RaindropCollectionParentRef? = null,
    @SerialName("parent.\$id")
    val parentId: Long? = null
) {
    fun stableId(): String? {
        return id?.toString()
    }

    fun parentCollectionId(): String? {
        return parent?.stableId()
            ?: parentId?.toString()
    }
}

@Serializable
data class RaindropCollectionParentRef(
    @SerialName("\$id")
    val id: Long? = null
) {
    fun stableId(): String? {
        return id?.toString()
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

    suspend fun getCollectionById(accessToken: String, collectionId: String): RaindropCollectionPayload? {
        val response = httpClient.get("${config.raindropApiBaseUrl}/collection/$collectionId") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (response.status == HttpStatusCode.NotFound) {
            return null
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop collection lookup failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindropCollection(responseBody)
            ?: throw RaindropUpstreamException("Raindrop collection lookup response missing collection payload")
    }

    suspend fun listCollections(accessToken: String): List<RaindropCollectionPayload> {
        val response = httpClient.get("${config.raindropApiBaseUrl}/collections") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop collections request failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindropCollections(responseBody)
    }

    suspend fun listChildCollections(
        accessToken: String,
        parentCollectionId: String
    ): List<RaindropCollectionPayload> {
        val response = httpClient.get("${config.raindropApiBaseUrl}/collections/childrens") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop child collections request failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindropCollections(responseBody)
            .filter { collection -> collection.parentCollectionId() == parentCollectionId }
    }

    suspend fun createCollection(
        accessToken: String,
        title: String,
        parentCollectionId: String?
    ): RaindropCollectionPayload {
        val requestBody = buildJsonObject {
            put("title", title)
            parentCollectionId?.takeIf { it.isNotBlank() }?.let { parentId ->
                put("parent.\$id", parentId.toRaindropCollectionIdPrimitive())
            }
        }

        val response = httpClient.post("${config.raindropApiBaseUrl}/collection") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(requestBody)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop collection creation failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindropCollection(responseBody)
            ?: throw RaindropUpstreamException("Raindrop collection creation response missing collection payload")
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

    fun decodeRaindropCollection(raw: String): RaindropCollectionPayload? {
        val root = parseJsonObject(raw) ?: return null
        val payloadElement = root["item"] ?: root
        return decodeCollectionPayload(payloadElement)
    }

    fun decodeRaindropCollections(raw: String): List<RaindropCollectionPayload> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val items = root["items"] ?: return emptyList()

        return when (items) {
            is JsonArray -> items.mapNotNull(::decodeCollectionPayload)
            is JsonObject -> items.values.mapNotNull(::decodeCollectionPayload)
            else -> emptyList()
        }
    }

    private fun parseJsonObject(raw: String): JsonObject? {
        return runCatching { json.decodeFromString(JsonElement.serializer(), raw) }
            .getOrNull() as? JsonObject
    }

    private fun decodeCollectionPayload(element: JsonElement): RaindropCollectionPayload? {
        return runCatching {
            json.decodeFromJsonElement(RaindropCollectionPayload.serializer(), element)
        }.getOrNull()
    }
}

private fun String.toRaindropCollectionIdPrimitive(): JsonPrimitive {
    return toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(this)
}
