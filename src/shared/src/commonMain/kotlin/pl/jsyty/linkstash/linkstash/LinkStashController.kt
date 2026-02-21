package pl.jsyty.linkstash.linkstash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceDto

class LinkStashController(
    private val repository: LinkStashRepository,
    private val defaultSpaceTitle: String,
    private val scope: CoroutineScope
) {
    private val _uiState = MutableStateFlow(LinkStashUiState())
    val uiState: StateFlow<LinkStashUiState> = _uiState.asStateFlow()

    fun initialize() {
        scope.launch {
            repository.hydrateSessionToken()
            val pendingCount = repository.pendingCount()
            _uiState.update { it.copy(pendingQueueCount = pendingCount) }

            if (repository.hasSessionToken()) {
                refreshAllData(statusMessage = "Session restored")
            }
        }
    }

    fun useBearerToken(rawToken: String) {
        val normalizedToken = rawToken.trim()
        if (normalizedToken.isBlank()) {
            _uiState.update {
                it.copy(statusMessage = "Raindrop token is required")
            }
            return
        }

        runBusyAction {
            repository.exchangeRaindropToken(normalizedToken)
            refreshAllData(statusMessage = "Logged in with token")
        }
    }

    fun onSharedUrlReceived(rawUrl: String) {
        val normalizedUrl = rawUrl.trim()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            _uiState.update {
                it.copy(statusMessage = "Shared text does not contain an http(s) URL")
            }
            return
        }

        scope.launch {
            repository.enqueuePendingLink(normalizedUrl)
            val pendingCount = repository.pendingCount()
            _uiState.update { state ->
                state.copy(
                    pendingQueueCount = pendingCount,
                    statusMessage = "Queued shared URL"
                )
            }

            if (uiState.value.isAuthenticated) {
                syncPendingQueue()
            }
        }
    }

    fun refresh() {
        if (!uiState.value.isAuthenticated) {
            _uiState.update {
                it.copy(statusMessage = "Authenticate with token first")
            }
            return
        }

        runBusyAction {
            refreshAllData(statusMessage = "Refreshed")
        }
    }

    fun onNetworkAvailable() {
        val currentState = uiState.value
        if (!currentState.isAuthenticated || currentState.pendingQueueCount <= 0) {
            return
        }
        syncPendingQueue()
    }

    fun syncPendingQueue() {
        if (!uiState.value.isAuthenticated) {
            return
        }

        runBusyAction {
            val spaces = uiState.value.spaces.ifEmpty { repository.listSpaces() }
            val sentCount = repository.flushPendingToDefaultSpace(spaces)
            val pendingCount = repository.pendingCount()

            val selectedSpaceId = uiState.value.selectedSpaceId
            val refreshedLinks = if (selectedSpaceId != null) {
                repository.listLinks(spaceId = selectedSpaceId)
            } else {
                LinksListResponse(links = emptyList())
            }

            _uiState.update {
                it.copy(
                    pendingQueueCount = pendingCount,
                    links = refreshedLinks.links,
                    nextCursor = refreshedLinks.nextCursor,
                    statusMessage = if (sentCount > 0) {
                        "Synced $sentCount queued link(s)"
                    } else {
                        "No queued links were synced"
                    }
                )
            }
        }
    }

    fun selectSpace(spaceId: String) {
        runBusyAction {
            val linksResponse = repository.listLinks(spaceId = spaceId)
            _uiState.update {
                it.copy(
                    selectedSpaceId = spaceId,
                    links = linksResponse.links,
                    nextCursor = linksResponse.nextCursor,
                    statusMessage = "Loaded links"
                )
            }
        }
    }

    fun loadMoreLinks() {
        val currentState = uiState.value
        val spaceId = currentState.selectedSpaceId ?: return
        val cursor = currentState.nextCursor ?: return

        runBusyAction {
            val linksResponse = repository.listLinks(spaceId = spaceId, cursor = cursor)
            _uiState.update {
                it.copy(
                    links = it.links + linksResponse.links,
                    nextCursor = linksResponse.nextCursor,
                    statusMessage = "Loaded more links"
                )
            }
        }
    }

    fun moveLink(linkId: String, targetSpaceId: String) {
        runBusyAction {
            repository.moveLink(linkId = linkId, targetSpaceId = targetSpaceId)
            refreshSelectedSpaceLinks(statusMessage = "Moved link")
        }
    }

    fun deleteLink(linkId: String) {
        runBusyAction {
            repository.deleteLink(linkId)
            refreshSelectedSpaceLinks(statusMessage = "Deleted link")
        }
    }

    fun logout() {
        scope.launch {
            repository.clearSession()
            val pendingCount = repository.pendingCount()
            _uiState.value = LinkStashUiState(
                pendingQueueCount = pendingCount,
                statusMessage = "Logged out"
            )
        }
    }

    fun close() {
        repository.close()
    }

    private fun runBusyAction(action: suspend () -> Unit) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                action()
            } catch (error: ApiException) {
                if (error.error.code == ApiErrorCode.UNAUTHORIZED) {
                    repository.clearSession()
                    val pendingCount = repository.pendingCount()
                    _uiState.value = LinkStashUiState(
                        pendingQueueCount = pendingCount,
                        statusMessage = "Session expired. Paste token again."
                    )
                } else {
                    _uiState.update {
                        it.copy(statusMessage = error.error.message)
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(statusMessage = error.message ?: "Unexpected error")
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun refreshAllData(statusMessage: String) {
        val user = repository.fetchCurrentUser()
        val spaces = repository.listSpaces()
        val selectedSpaceId = resolveSelectedSpaceId(spaces = spaces, preferredSpaceId = uiState.value.selectedSpaceId)

        val (resolvedSelectedSpaceId, linksResponse) = safeListLinks(selectedSpaceId)

        val syncedCount = repository.flushPendingToDefaultSpace(spaces)
        val pendingCount = repository.pendingCount()

        val refreshedLinksResponse = if (syncedCount > 0 && resolvedSelectedSpaceId != null) {
            safeListLinks(resolvedSelectedSpaceId).second
        } else {
            linksResponse
        }

        _uiState.update {
            it.copy(
                isAuthenticated = true,
                user = user,
                spaces = spaces,
                selectedSpaceId = resolvedSelectedSpaceId,
                links = refreshedLinksResponse.links,
                nextCursor = refreshedLinksResponse.nextCursor,
                pendingQueueCount = pendingCount,
                statusMessage = if (syncedCount > 0) {
                    "$statusMessage. Synced $syncedCount queued link(s)."
                } else {
                    statusMessage
                }
            )
        }
    }

    private suspend fun safeListLinks(spaceId: String?): Pair<String?, LinksListResponse> {
        if (spaceId == null) {
            return null to LinksListResponse(links = emptyList(), nextCursor = null)
        }

        return try {
            spaceId to repository.listLinks(spaceId = spaceId)
        } catch (error: ApiException) {
            if (error.error.code == ApiErrorCode.NOT_FOUND) {
                null to LinksListResponse(links = emptyList(), nextCursor = null)
            } else {
                throw error
            }
        }
    }

    private suspend fun refreshSelectedSpaceLinks(statusMessage: String) {
        val selectedSpaceId = uiState.value.selectedSpaceId ?: return
        val linksResponse = repository.listLinks(spaceId = selectedSpaceId)
        _uiState.update {
            it.copy(
                links = linksResponse.links,
                nextCursor = linksResponse.nextCursor,
                statusMessage = statusMessage
            )
        }
    }

    private fun resolveSelectedSpaceId(
        spaces: List<SpaceDto>,
        preferredSpaceId: String?
    ): String? {
        val validPreferredSpaceId = preferredSpaceId
            ?.takeIf { preferredId -> spaces.any { it.id == preferredId } }

        if (validPreferredSpaceId != null) {
            return validPreferredSpaceId
        }

        return spaces.firstOrNull {
            it.title.equals(defaultSpaceTitle, ignoreCase = true)
        }?.id ?: spaces.firstOrNull()?.id
    }
}
