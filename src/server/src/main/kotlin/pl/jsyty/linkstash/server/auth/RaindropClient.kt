package pl.jsyty.linkstash.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
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

@Serializable
data class RaindropRaindropPayload(
    @SerialName("_id")
    val id: Long? = null,
    val link: String? = null,
    val title: String? = null,
    val excerpt: String? = null,
    val created: String? = null,
    val collection: JsonElement? = null,
    @SerialName("collection.\$id")
    val collectionId: Long? = null
) {
    fun stableId(): String? {
        return id?.toString()
    }

    fun stableCollectionId(): String? {
        val fromCollectionObject = when (val rawCollection = collection) {
            is JsonObject -> rawCollection["\$id"]
                ?.jsonPrimitive
                ?.contentOrNull
            is JsonPrimitive -> rawCollection.contentOrNull
            else -> null
        }?.takeIf { it.isNotBlank() }

        if (fromCollectionObject != null) {
            return fromCollectionObject
        }

        return collectionId?.toString()
    }
}

class RaindropClient(
    private val config: AppConfig,
    private val httpClient: HttpClient
) {
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
                put(
                    "parent",
                    buildJsonObject {
                        put("\$id", parentId.toRaindropCollectionIdPrimitive())
                    }
                )
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

    suspend fun updateCollection(
        accessToken: String,
        collectionId: String,
        title: String
    ): RaindropCollectionPayload {
        val requestBody = buildJsonObject {
            put("title", title)
        }

        val response = httpClient.put("${config.raindropApiBaseUrl}/collection/$collectionId") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(requestBody)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (response.status == HttpStatusCode.NotFound) {
            throw RaindropUpstreamException("Raindrop collection was not found")
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop collection update failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindropCollection(responseBody)
            ?: throw RaindropUpstreamException("Raindrop collection update response missing collection payload")
    }

    suspend fun deleteCollection(
        accessToken: String,
        collectionId: String
    ): Boolean {
        val response = httpClient.delete("${config.raindropApiBaseUrl}/collection/$collectionId") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (response.status == HttpStatusCode.NotFound) {
            return false
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop collection deletion failed (${response.status.value}): $responseBody"
            )
        }

        return true
    }

    suspend fun listRaindrops(
        accessToken: String,
        collectionId: String,
        page: Int,
        perPage: Int
    ): List<RaindropRaindropPayload> {
        val response = httpClient.get("${config.raindropApiBaseUrl}/raindrops/$collectionId") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            parameter("page", page)
            parameter("perpage", perPage)
            parameter("sort", "-created")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop raindrops list failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindrops(responseBody)
    }

    suspend fun createRaindrop(
        accessToken: String,
        collectionId: String,
        url: String
    ): RaindropRaindropPayload {
        val requestBody = buildJsonObject {
            put("link", url)
            put("collection.\$id", collectionId.toRaindropCollectionIdPrimitive())
            put("pleaseParse", buildJsonObject {})
        }

        val response = httpClient.post("${config.raindropApiBaseUrl}/raindrop") {
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
                "Raindrop raindrop creation failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindrop(responseBody)
            ?: throw RaindropUpstreamException("Raindrop creation response missing item payload")
    }

    suspend fun getRaindropById(accessToken: String, raindropId: String): RaindropRaindropPayload? {
        val response = httpClient.get("${config.raindropApiBaseUrl}/raindrop/$raindropId") {
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
                "Raindrop raindrop lookup failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindrop(responseBody)
            ?: throw RaindropUpstreamException("Raindrop lookup response missing item payload")
    }

    suspend fun moveRaindropToCollection(
        accessToken: String,
        raindropId: String,
        collectionId: String
    ): RaindropRaindropPayload {
        val requestBody = buildJsonObject {
            put("collection.\$id", collectionId.toRaindropCollectionIdPrimitive())
        }

        val response = httpClient.put("${config.raindropApiBaseUrl}/raindrop/$raindropId") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody(requestBody)
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (response.status == HttpStatusCode.NotFound) {
            throw RaindropUpstreamException("Raindrop raindrop was not found")
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop raindrop move failed (${response.status.value}): $responseBody"
            )
        }

        return LinkStashJsonReader.decodeRaindrop(responseBody)
            ?: throw RaindropUpstreamException("Raindrop move response missing item payload")
    }

    suspend fun deleteRaindrop(
        accessToken: String,
        raindropId: String
    ): Boolean {
        val response = httpClient.delete("${config.raindropApiBaseUrl}/raindrop/$raindropId") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        if (response.status == HttpStatusCode.Unauthorized) {
            throw RaindropUnauthorizedException()
        }

        if (response.status == HttpStatusCode.NotFound) {
            return false
        }

        val responseBody = response.bodyAsTextSafely()
        if (!response.status.isSuccess()) {
            throw RaindropUpstreamException(
                "Raindrop raindrop deletion failed (${response.status.value}): $responseBody"
            )
        }

        return true
    }

}

private suspend fun io.ktor.client.statement.HttpResponse.bodyAsTextSafely(): String {
    return runCatching { body<String>() }.getOrDefault("")
}

private object LinkStashJsonReader {
    private val json = pl.jsyty.linkstash.contracts.LinkStashJson.instance

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

    fun decodeRaindrop(raw: String): RaindropRaindropPayload? {
        val root = parseJsonObject(raw) ?: return null
        val payloadElement = root["item"] ?: root
        return decodeRaindropPayload(payloadElement)
    }

    fun decodeRaindrops(raw: String): List<RaindropRaindropPayload> {
        val root = parseJsonObject(raw) ?: return emptyList()
        val items = root["items"] ?: return emptyList()

        return when (items) {
            is JsonArray -> items.mapNotNull(::decodeRaindropPayload)
            is JsonObject -> items.values.mapNotNull(::decodeRaindropPayload)
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

    private fun decodeRaindropPayload(element: JsonElement): RaindropRaindropPayload? {
        return runCatching {
            json.decodeFromJsonElement(RaindropRaindropPayload.serializer(), element)
        }.getOrNull()
    }
}

private fun String.toRaindropCollectionIdPrimitive(): JsonPrimitive {
    return toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(this)
}
