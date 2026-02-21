package pl.jsyty.linkstash.linkstash

import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.client.LinkStashApiClient
import pl.jsyty.linkstash.contracts.client.LinkStashApiClientConfig
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

class LinkStashRepository(
    private val sessionStore: LinkStashSessionStore,
    private val pendingQueueStore: LinkStashPendingQueueStore,
    private val config: LinkStashClientConfig
) {
    private var cachedBearerToken: String? = null

    private val apiClient = LinkStashApiClient.create(
        LinkStashApiClientConfig(
            baseUrl = config.apiBaseUrl,
            bearerTokenProvider = { cachedBearerToken }
        )
    )

    suspend fun hydrateSessionToken() {
        cachedBearerToken = sessionStore.readBearerToken()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun hasSessionToken(): Boolean = !cachedBearerToken.isNullOrBlank()

    suspend fun fetchCurrentUser(): UserDto {
        return apiClient.authApi.me()
    }

    suspend fun exchangeRaindropToken(rawRaindropToken: String): UserDto {
        val normalizedRaindropToken = rawRaindropToken.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: error("Raindrop token is required")

        val authResponse = apiClient.authApi.exchangeRaindropToken(
            AuthRaindropTokenExchangeRequest(
                accessToken = normalizedRaindropToken,
                sessionMode = AuthSessionMode.BEARER
            )
        )

        val bearerToken = authResponse.bearerToken
            ?.takeIf { it.isNotBlank() }
            ?: error("Token exchange did not return bearer token")

        cachedBearerToken = bearerToken
        sessionStore.writeBearerToken(bearerToken)

        return authResponse.user
    }

    suspend fun listSpaces(): List<SpaceDto> {
        return apiClient.spacesApi.list().spaces
    }

    suspend fun listLinks(spaceId: String, cursor: String? = null): LinksListResponse {
        return apiClient.linksApi.list(spaceId = spaceId, cursor = cursor)
    }

    suspend fun createLink(spaceId: String, url: String): LinkDto {
        return apiClient.linksApi.create(
            spaceId = spaceId,
            request = LinkCreateRequest(url = url)
        )
    }

    suspend fun moveLink(linkId: String, targetSpaceId: String): LinkDto {
        return apiClient.linksApi.move(
            linkId = linkId,
            request = LinkMoveRequest(spaceId = targetSpaceId)
        )
    }

    suspend fun deleteLink(linkId: String) {
        apiClient.linksApi.delete(linkId)
    }

    suspend fun enqueuePendingLink(url: String): Boolean {
        return pendingQueueStore.enqueue(url)
    }

    suspend fun pendingCount(): Int {
        return pendingQueueStore.count()
    }

    suspend fun flushPendingToDefaultSpace(spaces: List<SpaceDto>): Int {
        val targetSpaceId = resolveDefaultSpaceId(spaces) ?: return 0
        val pendingLinks = pendingQueueStore.listOldest()

        var sentCount = 0
        for (pendingLink in pendingLinks) {
            try {
                createLink(spaceId = targetSpaceId, url = pendingLink.url)
                pendingQueueStore.deleteById(pendingLink.id)
                sentCount += 1
            } catch (_: Exception) {
                // Keep remaining queue as-is and retry later.
                break
            }
        }

        return sentCount
    }

    suspend fun clearSession() {
        cachedBearerToken = null
        sessionStore.clearBearerToken()
    }

    fun close() {
        apiClient.close()
    }

    private fun resolveDefaultSpaceId(spaces: List<SpaceDto>): String? {
        return spaces.firstOrNull { it.title.equals(config.defaultSpaceTitle, ignoreCase = true) }?.id
            ?: spaces.firstOrNull()?.id
    }
}
