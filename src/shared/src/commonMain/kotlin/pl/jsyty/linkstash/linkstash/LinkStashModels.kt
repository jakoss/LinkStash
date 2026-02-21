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
    suspend fun clearBearerToken()
}

interface LinkStashPendingQueueStore {
    suspend fun enqueue(url: String): Boolean
    suspend fun listOldest(limit: Int = 200): List<PendingQueuedLink>
    suspend fun deleteById(id: Long)
    suspend fun count(): Int
}

data class LinkStashUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val user: UserDto? = null,
    val spaces: List<SpaceDto> = emptyList(),
    val selectedSpaceId: String? = null,
    val links: List<LinkDto> = emptyList(),
    val nextCursor: String? = null,
    val pendingQueueCount: Int = 0,
    val statusMessage: String = "Paste Raindrop token to sync and browse links"
)
