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

open class LinkStashRepository(
    private val sessionStore: LinkStashSessionStore,
    private val pendingQueueStore: LinkStashPendingQueueStore,
    private val config: LinkStashClientConfig
) {
    private var cachedBearerToken: String? = null
    private var currentServerUrl = apiBaseUrlToServerUrl(config.apiBaseUrl)
    private var apiClient = createApiClient()

    open suspend fun hydrateSessionToken() {
        sessionStore.readServerUrl()
            ?.let(::normalizeServerUrl)
            ?.let { persistedServerUrl ->
                if (persistedServerUrl != currentServerUrl) {
                    currentServerUrl = persistedServerUrl
                    rebuildApiClient()
                }
            }

        cachedBearerToken = sessionStore.readBearerToken()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    open fun serverUrl(): String = currentServerUrl

    open suspend fun updateServerUrl(rawServerUrl: String) {
        val normalizedServerUrl = normalizeServerUrl(rawServerUrl)
            ?: error("Server URL must start with http:// or https://")

        if (normalizedServerUrl == currentServerUrl) {
            return
        }

        currentServerUrl = normalizedServerUrl
        sessionStore.writeServerUrl(normalizedServerUrl)
        cachedBearerToken = null
        sessionStore.clearBearerToken()
        rebuildApiClient()
    }

    open fun hasSessionToken(): Boolean = !cachedBearerToken.isNullOrBlank()

    open suspend fun fetchCurrentUser(): UserDto {
        return apiClient.authApi.me()
    }

    open suspend fun exchangeRaindropToken(rawRaindropToken: String): UserDto {
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

    open suspend fun listSpaces(): List<SpaceDto> {
        return apiClient.spacesApi.list().spaces
    }

    open suspend fun listLinks(spaceId: String, cursor: String? = null): LinksListResponse {
        return apiClient.linksApi.list(spaceId = spaceId, cursor = cursor)
    }

    open suspend fun createLink(spaceId: String, url: String): LinkDto {
        return apiClient.linksApi.create(
            spaceId = spaceId,
            request = LinkCreateRequest(url = url)
        )
    }

    open suspend fun moveLink(linkId: String, targetSpaceId: String): LinkDto {
        return apiClient.linksApi.move(
            linkId = linkId,
            request = LinkMoveRequest(spaceId = targetSpaceId)
        )
    }

    open suspend fun deleteLink(linkId: String) {
        apiClient.linksApi.delete(linkId)
    }

    open suspend fun enqueuePendingLink(url: String): Boolean {
        return pendingQueueStore.enqueue(url)
    }

    open suspend fun pendingCount(): Int {
        return pendingQueueStore.count()
    }

    open suspend fun flushPendingToDefaultSpace(spaces: List<SpaceDto>): List<LinkDto> {
        val targetSpaceId = resolveDefaultSpaceId(spaces) ?: return emptyList()
        val pendingLinks = pendingQueueStore.listOldest()

        val createdLinks = mutableListOf<LinkDto>()
        for (pendingLink in pendingLinks) {
            try {
                val createdLink = createLink(spaceId = targetSpaceId, url = pendingLink.url)
                pendingQueueStore.deleteById(pendingLink.id)
                createdLinks += createdLink
            } catch (_: Exception) {
                // Keep remaining queue as-is and retry later.
                break
            }
        }

        return createdLinks
    }

    open suspend fun clearSession() {
        cachedBearerToken = null
        sessionStore.clearBearerToken()
    }

    open fun close() {
        apiClient.close()
    }

    private fun createApiClient(): LinkStashApiClient {
        return LinkStashApiClient.create(
            LinkStashApiClientConfig(
                baseUrl = serverUrlToApiBaseUrl(currentServerUrl),
                bearerTokenProvider = { cachedBearerToken }
            )
        )
    }

    private fun rebuildApiClient() {
        apiClient.close()
        apiClient = createApiClient()
    }

    private fun resolveDefaultSpaceId(spaces: List<SpaceDto>): String? {
        return spaces.firstOrNull { it.title.equals(config.defaultSpaceTitle, ignoreCase = true) }?.id
            ?: spaces.firstOrNull()?.id
    }
}
