package pl.jsyty.linkstash.linkstash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceDto

class LinkStashViewModel(
    private val repository: LinkStashRepository,
    private val defaultSpaceTitle: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(LinkStashUiState())
    val uiState: StateFlow<LinkStashUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            repository.hydrateSessionToken()
            val pendingCount = repository.pendingCount()
            val hasSessionToken = repository.hasSessionToken()
            _uiState.update {
                it.copy(
                    isAuthenticated = hasSessionToken,
                    pendingQueueCount = pendingCount
                )
            }

            if (hasSessionToken) {
                runBusyAction(keepSessionOnError = true) {
                    refreshAllData(statusMessage = "Session restored")
                }
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

        viewModelScope.launch {
            val wasQueued = repository.enqueuePendingLink(normalizedUrl)
            val pendingCount = repository.pendingCount()
            _uiState.update { state ->
                state.copy(
                    pendingQueueCount = pendingCount,
                    statusMessage = if (wasQueued) {
                        "Queued shared URL"
                    } else {
                        "URL already queued"
                    }
                )
            }

            if (uiState.value.isAuthenticated) {
                syncPendingQueue()
            }
        }
    }

    fun saveManualUrl(rawUrl: String) = onSharedUrlReceived(rawUrl)

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
        viewModelScope.launch {
            repository.clearSession()
            val pendingCount = repository.pendingCount()
            _uiState.value = LinkStashUiState(
                pendingQueueCount = pendingCount,
                statusMessage = "Logged out"
            )
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private fun runBusyAction(
        keepSessionOnError: Boolean = uiState.value.isAuthenticated,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch {
            actionMutex.withLock {
                _uiState.update { it.copy(isLoading = true) }
                try {
                    action()
                } catch (error: Throwable) {
                    handleFailure(error, keepSessionOnError)
                } finally {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private suspend fun handleFailure(
        error: Throwable,
        keepSessionOnError: Boolean
    ) {
        if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
            repository.clearSession()
            val pendingCount = repository.pendingCount()
            _uiState.value = LinkStashUiState(
                pendingQueueCount = pendingCount,
                statusMessage = "Session expired. Paste token again."
            )
            return
        }

        val shouldRemainAuthenticated = keepSessionOnError || uiState.value.isAuthenticated
        val statusMessage = when (error) {
            is ApiException -> error.error.message
            else -> connectionAwareMessage(error, shouldRemainAuthenticated)
        }

        _uiState.update {
            it.copy(
                isAuthenticated = shouldRemainAuthenticated,
                statusMessage = statusMessage
            )
        }
    }

    private fun connectionAwareMessage(
        error: Throwable,
        isAuthenticated: Boolean
    ): String {
        val diagnostic = buildString {
            append(error::class.simpleName.orEmpty())
            append(' ')
            append(error.message.orEmpty())
        }.lowercase()

        val looksLikeConnectionFailure = listOf(
            "connect",
            "connection",
            "network",
            "timeout",
            "refused",
            "unresolved",
            "host"
        ).any { diagnostic.contains(it) }

        if (looksLikeConnectionFailure) {
            return if (isAuthenticated) {
                "Offline. Shared links will stay queued until the server is reachable."
            } else {
                "Can't reach the server right now. You can still queue links offline."
            }
        }

        return error.message ?: "Unexpected error"
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
