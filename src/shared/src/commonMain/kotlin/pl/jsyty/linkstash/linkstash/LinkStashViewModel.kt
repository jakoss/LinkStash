package pl.jsyty.linkstash.linkstash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

class LinkStashViewModel(
    private val repository: LinkStashRepository,
    private val defaultSpaceTitle: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(LinkStashUiState())
    val uiState: StateFlow<LinkStashUiState> = _uiState.asStateFlow()

    private val actionMutex = Mutex()
    private val metadataPollingBatches = mutableListOf<MetadataPollingBatch>()
    private var metadataPollingJob: Job? = null

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

    fun refreshIfAuthenticated() {
        if (!uiState.value.isAuthenticated || uiState.value.isLoading) {
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
            val createdLinks = repository.flushPendingToDefaultSpace(spaces)
            val pendingCount = repository.pendingCount()
            val selectedSpaceId = uiState.value.selectedSpaceId
            val refreshedLinks = if (selectedSpaceId != null) {
                repository.listLinks(spaceId = selectedSpaceId)
            } else {
                LinksListResponse(links = emptyList())
            }
            val pendingMetadataIds = resolvePendingMetadataIds(
                visibleLinks = refreshedLinks.links,
                trackedLinks = createdLinks
            )

            _uiState.update {
                it.copy(
                    pendingQueueCount = pendingCount,
                    links = refreshedLinks.links,
                    nextCursor = refreshedLinks.nextCursor,
                    pendingMetadataLinkIds = pendingMetadataIds,
                    statusMessage = if (createdLinks.isNotEmpty()) {
                        "Synced ${createdLinks.size} queued link(s)"
                    } else {
                        "No queued links were synced"
                    }
                )
            }
            reconcileMetadataPolling()
        }
    }

    fun selectSpace(spaceId: String) {
        cancelMetadataPollingJob()
        runBusyAction {
            val linksResponse = repository.listLinks(spaceId = spaceId)
            val pendingMetadataIds = resolvePendingMetadataIds(visibleLinks = linksResponse.links)

            _uiState.update {
                it.copy(
                    selectedSpaceId = spaceId,
                    links = linksResponse.links,
                    nextCursor = linksResponse.nextCursor,
                    pendingMetadataLinkIds = pendingMetadataIds,
                    statusMessage = "Loaded links"
                )
            }
            reconcileMetadataPolling()
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
        stopMetadataPolling(clearPendingIds = true)
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
        stopMetadataPolling(clearPendingIds = false)
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
            stopMetadataPolling(clearPendingIds = true)
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
        reconcileMetadataPolling()
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
        val selectedSpaceId = resolveSelectedSpaceId(
            spaces = spaces,
            preferredSpaceId = uiState.value.selectedSpaceId
        )
        val (resolvedSelectedSpaceId, linksResponse) = safeListLinks(selectedSpaceId)
        val createdLinks = repository.flushPendingToDefaultSpace(spaces)
        val pendingCount = repository.pendingCount()
        val refreshedLinksResponse = if (createdLinks.isNotEmpty() && resolvedSelectedSpaceId != null) {
            safeListLinks(resolvedSelectedSpaceId).second
        } else {
            linksResponse
        }
        val pendingMetadataIds = resolvePendingMetadataIds(
            visibleLinks = refreshedLinksResponse.links,
            trackedLinks = createdLinks
        )

        _uiState.update {
            it.copy(
                isAuthenticated = true,
                user = user,
                spaces = spaces,
                selectedSpaceId = resolvedSelectedSpaceId,
                links = refreshedLinksResponse.links,
                nextCursor = refreshedLinksResponse.nextCursor,
                pendingMetadataLinkIds = pendingMetadataIds,
                pendingQueueCount = pendingCount,
                statusMessage = if (createdLinks.isNotEmpty()) {
                    "$statusMessage. Synced ${createdLinks.size} queued link(s)."
                } else {
                    statusMessage
                }
            )
        }
        reconcileMetadataPolling()
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
        val pendingMetadataIds = resolvePendingMetadataIds(visibleLinks = linksResponse.links)

        _uiState.update {
            it.copy(
                links = linksResponse.links,
                nextCursor = linksResponse.nextCursor,
                pendingMetadataLinkIds = pendingMetadataIds,
                statusMessage = statusMessage
            )
        }
        reconcileMetadataPolling()
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

    private fun resolvePendingMetadataIds(
        visibleLinks: List<LinkDto>,
        trackedLinks: List<LinkDto> = emptyList()
    ): Set<String> {
        val visibleLinksById = visibleLinks.associateBy(LinkDto::id)
        val trimmedBatches = metadataPollingBatches.mapNotNull { batch ->
            val remainingIds = batch.ids.filterTo(mutableSetOf()) { linkId ->
                visibleLinksById[linkId]?.let(::needsMetadataPolling) == true
            }
            batch.copy(ids = remainingIds).takeIf { it.ids.isNotEmpty() }
        }

        metadataPollingBatches.clear()
        metadataPollingBatches.addAll(trimmedBatches)

        val activeIds = metadataPollingBatches.flatMapTo(mutableSetOf()) { it.ids }
        val newIds = trackedLinks.mapNotNullTo(mutableSetOf()) { trackedLink ->
            visibleLinksById[trackedLink.id]
                ?.takeIf(::needsMetadataPolling)
                ?.id
                ?.takeIf { it !in activeIds }
        }

        if (newIds.isNotEmpty()) {
            metadataPollingBatches += MetadataPollingBatch(ids = newIds)
        }

        return metadataPollingBatches.flatMapTo(mutableSetOf()) { it.ids }
    }

    private fun reconcileMetadataPolling() {
        val currentState = uiState.value
        val shouldPoll = currentState.isAuthenticated &&
            currentState.selectedSpaceId != null &&
            currentState.pendingMetadataLinkIds.isNotEmpty()

        if (!shouldPoll) {
            cancelMetadataPollingJob()
            return
        }

        if (metadataPollingJob?.isActive == true) {
            return
        }

        metadataPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(METADATA_POLL_INTERVAL_MS)

                val shouldContinue = actionMutex.withLock {
                    val stateBeforeRefresh = uiState.value
                    val selectedSpaceId = stateBeforeRefresh.selectedSpaceId
                    if (!stateBeforeRefresh.isAuthenticated ||
                        selectedSpaceId == null ||
                        stateBeforeRefresh.pendingMetadataLinkIds.isEmpty()
                    ) {
                        return@withLock false
                    }

                    try {
                        val linksResponse = repository.listLinks(spaceId = selectedSpaceId)
                        val pendingMetadataIds = advancePendingMetadataBatches(linksResponse.links)
                        val mergedLinks = mergeFirstPageLinks(
                            currentLinks = stateBeforeRefresh.links,
                            refreshedFirstPageLinks = linksResponse.links
                        )
                        val mergedNextCursor = mergeNextCursor(
                            currentLinks = stateBeforeRefresh.links,
                            currentNextCursor = stateBeforeRefresh.nextCursor,
                            refreshedFirstPageLinks = linksResponse.links,
                            refreshedNextCursor = linksResponse.nextCursor
                        )

                        _uiState.update {
                            it.copy(
                                links = mergedLinks,
                                nextCursor = mergedNextCursor,
                                pendingMetadataLinkIds = pendingMetadataIds
                            )
                        }
                    } catch (error: Throwable) {
                        stopMetadataPolling(clearPendingIds = false)
                        handleFailure(error, keepSessionOnError = true)
                        return@withLock false
                    }

                    uiState.value.pendingMetadataLinkIds.isNotEmpty()
                }

                if (!shouldContinue) {
                    break
                }
            }

            metadataPollingJob = null
        }
    }

    private fun advancePendingMetadataBatches(visibleLinks: List<LinkDto>): Set<String> {
        val visibleLinksById = visibleLinks.associateBy(LinkDto::id)
        val nextBatches = metadataPollingBatches.mapNotNull { batch ->
            val unresolvedIds = batch.ids.filterTo(mutableSetOf()) { linkId ->
                visibleLinksById[linkId]?.let(::needsMetadataPolling) == true
            }

            if (unresolvedIds.isEmpty()) {
                return@mapNotNull null
            }

            val nextAttempt = batch.attempts + 1
            if (nextAttempt >= METADATA_POLL_MAX_ATTEMPTS) {
                return@mapNotNull null
            }

            batch.copy(ids = unresolvedIds, attempts = nextAttempt)
        }

        metadataPollingBatches.clear()
        metadataPollingBatches.addAll(nextBatches)

        return metadataPollingBatches.flatMapTo(mutableSetOf()) { it.ids }
    }

    private fun mergeFirstPageLinks(
        currentLinks: List<LinkDto>,
        refreshedFirstPageLinks: List<LinkDto>
    ): List<LinkDto> {
        if (currentLinks.size <= refreshedFirstPageLinks.size) {
            return refreshedFirstPageLinks
        }

        val refreshedIds = refreshedFirstPageLinks.mapTo(mutableSetOf()) { it.id }
        return refreshedFirstPageLinks + currentLinks.filter { it.id !in refreshedIds }
    }

    private fun mergeNextCursor(
        currentLinks: List<LinkDto>,
        currentNextCursor: String?,
        refreshedFirstPageLinks: List<LinkDto>,
        refreshedNextCursor: String?
    ): String? {
        return if (currentLinks.size > refreshedFirstPageLinks.size && currentNextCursor != null) {
            currentNextCursor
        } else {
            refreshedNextCursor
        }
    }

    private fun stopMetadataPolling(clearPendingIds: Boolean) {
        cancelMetadataPollingJob()
        metadataPollingBatches.clear()
        if (clearPendingIds) {
            _uiState.update { it.copy(pendingMetadataLinkIds = emptySet()) }
        }
    }

    private fun cancelMetadataPollingJob() {
        metadataPollingJob?.cancel()
        metadataPollingJob = null
    }

    private data class MetadataPollingBatch(
        val ids: Set<String>,
        val attempts: Int = 0
    )

    private companion object {
        const val METADATA_POLL_INTERVAL_MS = 2_000L
        const val METADATA_POLL_MAX_ATTEMPTS = 10
    }
}
