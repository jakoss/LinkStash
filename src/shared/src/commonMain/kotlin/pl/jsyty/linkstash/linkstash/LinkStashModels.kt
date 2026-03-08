package pl.jsyty.linkstash.linkstash

import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

data class LinkStashClientConfig(
    val apiBaseUrl: String,
    val defaultSpaceTitle: String = "Inbox"
)

data class PendingQueuedLink(
    val id: Long,
    val url: String,
    val createdAtEpochSeconds: Long
)

interface LinkStashSessionStore {
    suspend fun readBearerToken(): String?
    suspend fun writeBearerToken(token: String)
    suspend fun readServerUrl(): String?
    suspend fun writeServerUrl(serverUrl: String)
    suspend fun clearBearerToken()
}

interface LinkStashPendingQueueStore {
    suspend fun enqueue(url: String): Boolean
    suspend fun listOldest(limit: Int = 200): List<PendingQueuedLink>
    suspend fun deleteById(id: Long)
    suspend fun count(): Int
}

data class LinkStashUiState(
    val serverUrl: String = "",
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val user: UserDto? = null,
    val spaces: List<SpaceDto> = emptyList(),
    val selectedSpaceId: String? = null,
    val links: List<LinkDto> = emptyList(),
    val nextCursor: String? = null,
    val pendingMetadataLinkIds: Set<String> = emptySet(),
    val pendingQueueCount: Int = 0,
    val statusMessage: String = "Paste Raindrop token to sync and browse links"
)

fun normalizeServerUrl(rawServerUrl: String): String? {
    val trimmed = rawServerUrl.trim().removeSuffix("/")
    if (trimmed.isBlank()) {
        return null
    }
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return null
    }

    return trimmed.removeSuffix("/v1")
}

fun serverUrlToApiBaseUrl(serverUrl: String): String {
    val normalizedServerUrl = normalizeServerUrl(serverUrl)
        ?: error("Server URL must start with http:// or https://")
    return "$normalizedServerUrl/v1/"
}

fun apiBaseUrlToServerUrl(apiBaseUrl: String): String {
    return normalizeServerUrl(apiBaseUrl) ?: apiBaseUrl.trim().removeSuffix("/")
}

fun needsMetadataPolling(link: LinkDto): Boolean {
    return link.previewImageUrl.isNullOrBlank() &&
        link.excerpt.isNullOrBlank() &&
        (link.title.isNullOrBlank() || link.title == link.url)
}
