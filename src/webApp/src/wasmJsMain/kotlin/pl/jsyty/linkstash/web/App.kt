package pl.jsyty.linkstash.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto

@Composable
fun App() {
    val storedSessionExpected = remember { loadStoredSessionExpected() }
    val storedApiBaseUrl = remember { loadStoredApiBaseUrl() }
    val cachedWorkspace = remember(storedApiBaseUrl) { loadStoredWorkspaceCache(storedApiBaseUrl) }
    var apiBaseUrl by remember { mutableStateOf(storedApiBaseUrl) }
    var apiBaseUrlInput by remember { mutableStateOf(apiBaseUrl) }
    var rawToken by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf("") }
    var newSpaceTitle by remember { mutableStateOf("") }
    var renameSpaceTitle by remember {
        mutableStateOf(
            cachedWorkspace
                ?.spaces
                ?.firstOrNull { it.id == cachedWorkspace.selectedSpaceId }
                ?.title
                .orEmpty()
        )
    }
    var archiveSpaceTitle by remember { mutableStateOf("") }
    var csrfToken by remember { mutableStateOf<String?>(null) }
    var shouldShowWorkspace by remember { mutableStateOf(storedSessionExpected || cachedWorkspace != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var workspaceState by remember {
        mutableStateOf(
            cachedWorkspace?.toWorkspaceState() ?: WebWorkspaceState(
                selectedSpaceId = loadStoredSelectedSpaceId()
            )
        )
    }
    var metadataPollingTargets by remember { mutableStateOf<Map<String, WebMetadataPollingTarget>>(emptyMap()) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var nextSpaceRequestId by remember { mutableStateOf(0) }
    var nextOptimisticLinkId by remember { mutableStateOf(0) }
    val latestSpaceRequestIds = remember { mutableMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun api(baseUrl: String = apiBaseUrl) = BrowserApi(baseUrl) { csrfToken }

    fun updateWorkspace(transform: (WebWorkspaceState) -> WebWorkspaceState) {
        workspaceState = transform(workspaceState)
    }

    fun updateOperations(transform: (WebWorkspaceOperations) -> WebWorkspaceOperations) {
        updateWorkspace { state ->
            state.copy(operations = transform(state.operations))
        }
    }

    fun setSessionExpected(expected: Boolean) {
        shouldShowWorkspace = expected
        storeSessionExpected(expected)
    }

    fun updateSelectedSpace(spaceId: String?, availableSpaces: List<SpaceDto> = workspaceState.spaces) {
        updateWorkspace { state ->
            state.copy(selectedSpaceId = spaceId)
        }
        renameSpaceTitle = availableSpaces.firstOrNull { it.id == spaceId }?.title.orEmpty()
        storeSelectedSpaceId(spaceId)
    }

    fun reconcileMetadataTargets(extraTargets: Map<String, WebMetadataPollingTarget> = emptyMap()) {
        val retainedTargets = resolveMetadataPollingTargets(
            currentTargets = metadataPollingTargets,
            linksBySpaceId = workspaceState.linksBySpaceId
        )
        val validExtraTargets = extraTargets.filter { (linkId, target) ->
            workspaceState.linksBySpaceId[target.spaceId]
                ?.any { item ->
                        item.link.id == linkId &&
                            item.syncState == WebLinkSyncState.Synced &&
                            shouldPollMetadata(item.link)
                } == true
        }

        metadataPollingTargets = retainedTargets + validExtraTargets
    }

    fun updateSpaceLinks(spaceId: String, transform: (List<WebLinkItem>) -> List<WebLinkItem>) {
        updateWorkspace { state ->
            state.copy(
                linksBySpaceId = state.linksBySpaceId + (spaceId to transform(state.linksBySpaceId[spaceId].orEmpty()))
            )
        }
        reconcileMetadataTargets()
    }

    fun mergeRemoteLinksIntoSpace(spaceId: String, remoteLinks: List<LinkDto>) {
        updateSpaceLinks(spaceId) { existing ->
            mergeRemoteLinks(existing = existing, remoteLinks = remoteLinks)
        }
    }

    fun trackMetadataIfNeeded(spaceId: String, link: LinkDto) {
        if (!shouldPollMetadata(link)) {
            return
        }

        reconcileMetadataTargets(
            extraTargets = mapOf(
                link.id to WebMetadataPollingTarget(spaceId = spaceId)
            )
        )
    }

    fun clearAuthenticatedState() {
        refreshJob?.cancel()
        refreshJob = null
        csrfToken = null
        metadataPollingTargets = emptyMap()
        newSpaceTitle = ""
        renameSpaceTitle = ""
        archiveSpaceTitle = ""
        pendingUrl = ""
        errorMessage = null
        workspaceState = WebWorkspaceState()
        storeSelectedSpaceId(null)
        storeWorkspaceCache(null)
        setSessionExpected(false)
    }

    suspend fun showStatus(message: String) {
        snackbarHostState.showSnackbar(message)
    }

    suspend fun handleActionError(error: Throwable): Boolean {
        if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
            clearAuthenticatedState()
            return true
        }

        val message = error.humanMessage()
        errorMessage = message
        snackbarHostState.showSnackbar(message)
        return false
    }

    suspend fun fetchWorkspaceSnapshot(
        preferredSpaceId: String?,
        baseUrl: String
    ): WorkspaceSnapshot = coroutineScope {
        val currentApi = api(baseUrl)
        val userDeferred = async { currentApi.me() }
        val spacesDeferred = async { currentApi.listSpaces().sortedByTitle() }
        val csrfDeferred = async { currentApi.fetchCsrfToken() }
        val availableSpaces = spacesDeferred.await()
        val resolvedSpaceId = resolveSelectedSpaceId(
            spaces = availableSpaces,
            preferredSpaceId = preferredSpaceId
        )
        val links = resolvedSpaceId?.let { currentApi.listLinks(it) }.orEmpty()

        WorkspaceSnapshot(
            user = userDeferred.await(),
            spaces = availableSpaces,
            csrfToken = csrfDeferred.await(),
            selectedSpaceId = resolvedSpaceId,
            links = links
        )
    }

    fun applyWorkspaceSnapshot(snapshot: WorkspaceSnapshot) {
        val validSpaceIds = snapshot.spaces.mapTo(mutableSetOf()) { it.id }
        val nextLinksBySpace = workspaceState.linksBySpaceId
            .filterKeys { it in validSpaceIds }
            .toMutableMap()

        snapshot.selectedSpaceId?.let { spaceId ->
            nextLinksBySpace[spaceId] = mergeRemoteLinks(
                existing = nextLinksBySpace[spaceId].orEmpty(),
                remoteLinks = snapshot.links
            )
        }

        csrfToken = snapshot.csrfToken
        updateWorkspace { state ->
            state.copy(
                user = snapshot.user,
                spaces = snapshot.spaces,
                selectedSpaceId = snapshot.selectedSpaceId,
                linksBySpaceId = nextLinksBySpace
            )
        }
        updateSelectedSpace(snapshot.selectedSpaceId, snapshot.spaces)
        setSessionExpected(true)
        reconcileMetadataTargets()
    }

    fun launchRefresh(
        preferredSpaceId: String? = workspaceState.selectedSpaceId,
        baseUrl: String = apiBaseUrl,
        showStatusMessage: String? = null
    ) {
        if (refreshJob?.isActive == true) {
            return
        }

        refreshJob = scope.launch {
            updateOperations { operations ->
                operations.copy(refreshing = true)
            }
            errorMessage = null
            try {
                applyWorkspaceSnapshot(
                    fetchWorkspaceSnapshot(
                        preferredSpaceId = preferredSpaceId,
                        baseUrl = baseUrl
                    )
                )
                if (showStatusMessage != null) {
                    showStatus(showStatusMessage)
                }
            } catch (error: Throwable) {
                handleActionError(error)
            } finally {
                updateOperations { operations ->
                    operations.copy(refreshing = false)
                }
                refreshJob = null
            }
        }
    }

    fun loadSpaceLinks(spaceId: String, showLoadedStatus: Boolean = false) {
        nextSpaceRequestId += 1
        val requestId = nextSpaceRequestId
        latestSpaceRequestIds[spaceId] = requestId
        updateOperations { operations ->
            operations.copy(loadingSpaceIds = operations.loadingSpaceIds + spaceId)
        }

        scope.launch {
            try {
                val remoteLinks = api().listLinks(spaceId)
                if (latestSpaceRequestIds[spaceId] != requestId) {
                    return@launch
                }
                mergeRemoteLinksIntoSpace(spaceId, remoteLinks)
                if (showLoadedStatus && workspaceState.selectedSpaceId == spaceId) {
                    showStatus("Loaded ${workspaceState.selectedSpace()?.title ?: "links"}")
                }
            } catch (error: Throwable) {
                if (latestSpaceRequestIds[spaceId] != requestId) {
                    return@launch
                }
                handleActionError(error)
            } finally {
                if (latestSpaceRequestIds[spaceId] == requestId) {
                    updateOperations { operations ->
                        operations.copy(loadingSpaceIds = operations.loadingSpaceIds - spaceId)
                    }
                }
            }
        }
    }

    fun saveLink(spaceId: String, rawUrl: String, existingKey: String? = null) {
        scope.launch {
            val normalizedUrl = try {
                rawUrl.trim().requireHttpUrl()
            } catch (error: Throwable) {
                handleActionError(error)
                return@launch
            }

            val optimisticKey = existingKey ?: run {
                nextOptimisticLinkId += 1
                "optimistic-link-$nextOptimisticLinkId"
            }
            val optimisticLink = WebLinkItem(
                key = optimisticKey,
                link = LinkDto(
                    id = optimisticKey,
                    url = normalizedUrl,
                    title = normalizedUrl,
                    spaceId = spaceId
                ),
                syncState = WebLinkSyncState.Saving,
                retryUrl = normalizedUrl
            )

            pendingUrl = ""
            errorMessage = null
            updateOperations { operations ->
                operations.copy(savingLink = true)
            }
            updateSpaceLinks(spaceId) { existing ->
                listOf(optimisticLink) + existing.filterNot { it.key == optimisticKey }
            }

            try {
                val createdLink = api().createLink(spaceId, normalizedUrl)
                updateSpaceLinks(spaceId) { existing ->
                    listOf(createdLink.toWebLinkItem()) + existing.filterNot {
                        it.key == optimisticKey || it.link.id == createdLink.id
                    }
                }
                trackMetadataIfNeeded(spaceId, createdLink)
                showStatus("Link saved")
            } catch (error: Throwable) {
                if (handleActionError(error)) {
                    return@launch
                }

                val failureMessage = error.humanMessage()
                updateSpaceLinks(spaceId) { existing ->
                    listOf(
                        optimisticLink.copy(
                            syncState = WebLinkSyncState.Failed,
                            failureMessage = failureMessage
                        )
                    ) + existing.filterNot { it.key == optimisticKey }
                }
            } finally {
                updateOperations { operations ->
                    operations.copy(savingLink = false)
                }
            }
        }
    }

    LaunchedEffect(apiBaseUrl, shouldShowWorkspace) {
        if (!shouldShowWorkspace || workspaceState.operations.initialLoad || workspaceState.operations.sessionRestore) {
            return@LaunchedEffect
        }

        updateOperations { operations ->
            operations.copy(sessionRestore = true)
        }
        try {
            applyWorkspaceSnapshot(
                fetchWorkspaceSnapshot(
                    preferredSpaceId = workspaceState.selectedSpaceId,
                    baseUrl = apiBaseUrl
                )
            )
        } catch (error: Throwable) {
            handleActionError(error)
        } finally {
            updateOperations { operations ->
                operations.copy(sessionRestore = false)
            }
        }
    }

    LaunchedEffect(apiBaseUrl, shouldShowWorkspace, workspaceState) {
        storeWorkspaceCache(
            workspaceState
                .takeIf { shouldShowWorkspace }
                ?.toCache(apiBaseUrl = apiBaseUrl)
        )
    }

    LaunchedEffect(metadataPollingTargets) {
        if (metadataPollingTargets.isEmpty()) {
            return@LaunchedEffect
        }

        delay(METADATA_POLL_INTERVAL_MS)

        val targetsBySpace = metadataPollingTargets.entries.groupBy(
            keySelector = { it.value.spaceId },
            valueTransform = { it.key }
        )

        targetsBySpace.forEach { (spaceId, linkIds) ->
            try {
                val remoteLinks = api().listLinks(spaceId)
                mergeRemoteLinksIntoSpace(spaceId, remoteLinks)

                val remoteLinksById = remoteLinks.associateBy { it.id }
                val nextTargets = metadataPollingTargets.toMutableMap()
                linkIds.forEach { linkId ->
                    val currentTarget = nextTargets[linkId] ?: return@forEach
                    val refreshedLink = remoteLinksById[linkId]

                    when {
                        refreshedLink == null -> nextTargets.remove(linkId)
                        !shouldPollMetadata(refreshedLink) -> nextTargets.remove(linkId)
                        currentTarget.attempts + 1 >= METADATA_POLL_MAX_ATTEMPTS -> nextTargets.remove(linkId)
                        else -> nextTargets[linkId] = currentTarget.copy(attempts = currentTarget.attempts + 1)
                    }
                }
                metadataPollingTargets = resolveMetadataPollingTargets(
                    currentTargets = nextTargets,
                    linksBySpaceId = workspaceState.linksBySpaceId
                )
            } catch (error: Throwable) {
                if (handleActionError(error)) {
                    return@LaunchedEffect
                }
            }
        }
    }

    val visibleLinks = workspaceState.visibleLinks()
    val selectedSpaceId = workspaceState.selectedSpaceId
    val isBlockingWorkspaceLoad = shouldShowWorkspace &&
        !workspaceState.hasVisibleWorkspaceContent() &&
        workspaceState.operations.initialLoad

    MaterialTheme(colorScheme = linkStashColorScheme()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!shouldShowWorkspace) {
                    LoginScreen(
                        apiBaseUrlInput = apiBaseUrlInput,
                        onApiBaseUrlInputChange = { apiBaseUrlInput = it },
                        rawToken = rawToken,
                        onRawTokenChange = { rawToken = it },
                        isBusy = workspaceState.operations.initialLoad,
                        errorMessage = errorMessage,
                        onSignIn = {
                            val nextApiBaseUrl = apiBaseUrlInput.trim().ifBlank { suggestedApiBaseUrl() }
                            scope.launch {
                                updateOperations { operations ->
                                    operations.copy(initialLoad = true)
                                }
                                errorMessage = null
                                try {
                                    val exchange = api(nextApiBaseUrl).exchangeRaindropToken(rawToken.trim())
                                    apiBaseUrl = nextApiBaseUrl
                                    storeApiBaseUrl(nextApiBaseUrl)
                                    storeWorkspaceCache(null)
                                    apiBaseUrlInput = nextApiBaseUrl
                                    rawToken = ""
                                    csrfToken = exchange.csrfToken
                                    setSessionExpected(true)
                                    shouldShowWorkspace = true
                                    applyWorkspaceSnapshot(
                                        fetchWorkspaceSnapshot(
                                            preferredSpaceId = workspaceState.selectedSpaceId,
                                            baseUrl = nextApiBaseUrl
                                        )
                                    )
                                    showStatus("Signed in")
                                } catch (error: Throwable) {
                                    shouldShowWorkspace = false
                                    setSessionExpected(false)
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(initialLoad = false)
                                    }
                                }
                            }
                        }
                    )
                } else {
                    WorkspaceScreen(
                        user = workspaceState.user,
                        spaces = workspaceState.spaces,
                        selectedSpaceId = selectedSpaceId,
                        renameSpaceTitle = renameSpaceTitle,
                        onRenameSpaceTitleChange = { renameSpaceTitle = it },
                        newSpaceTitle = newSpaceTitle,
                        onNewSpaceTitleChange = { newSpaceTitle = it },
                        archiveSpaceTitle = archiveSpaceTitle,
                        onArchiveSpaceTitleChange = { archiveSpaceTitle = it },
                        pendingUrl = pendingUrl,
                        onPendingUrlChange = { pendingUrl = it },
                        links = visibleLinks,
                        isRefreshing = workspaceState.operations.refreshing,
                        isExporting = workspaceState.operations.exporting,
                        isLoggingOut = workspaceState.operations.loggingOut,
                        isSavingLink = workspaceState.operations.savingLink,
                        isSelectedSpaceLoading = selectedSpaceId != null &&
                            selectedSpaceId in workspaceState.operations.loadingSpaceIds,
                        isCreatingSpace = workspaceState.operations.creatingSpace,
                        isRenamingSelectedSpace = workspaceState.operations.renamingSpaceId == selectedSpaceId,
                        isArchivingSelectedSpace = workspaceState.operations.archivingSpaceId == selectedSpaceId,
                        isDeletingSelectedSpace = workspaceState.operations.deletingSpaceId == selectedSpaceId,
                        onRefresh = {
                            launchRefresh(showStatusMessage = "Refreshed")
                        },
                        onLogout = {
                            scope.launch {
                                updateOperations { operations ->
                                    operations.copy(loggingOut = true)
                                }
                                try {
                                    api().logout()
                                    clearAuthenticatedState()
                                    showStatus("Logged out")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(loggingOut = false)
                                    }
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                updateOperations { operations ->
                                    operations.copy(exporting = true)
                                }
                                try {
                                    copyCurrentLinksToClipboard(
                                        visibleLinks
                                            .filter { it.syncState == WebLinkSyncState.Synced }
                                            .map(WebLinkItem::link)
                                    )
                                    showStatus(
                                        if (visibleLinks.isEmpty()) "No links to copy"
                                        else "Copied ${visibleLinks.count { it.syncState == WebLinkSyncState.Synced }} links"
                                    )
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(exporting = false)
                                    }
                                }
                            }
                        },
                        onSelectSpace = { space ->
                            updateSelectedSpace(space.id, workspaceState.spaces)
                            loadSpaceLinks(space.id)
                        },
                        onCreateSpace = {
                            scope.launch {
                                val title = try {
                                    newSpaceTitle.trim().requireNonBlank("Space title is required")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                    return@launch
                                }

                                updateOperations { operations ->
                                    operations.copy(creatingSpace = true)
                                }
                                try {
                                    val createdSpace = api().createSpace(title)
                                    val nextSpaces = (workspaceState.spaces + createdSpace)
                                        .distinctBy { it.id }
                                        .sortedByTitle()
                                    newSpaceTitle = ""
                                    updateWorkspace { state ->
                                        state.copy(
                                            spaces = nextSpaces,
                                            selectedSpaceId = createdSpace.id,
                                            linksBySpaceId = state.linksBySpaceId + (createdSpace.id to emptyList())
                                        )
                                    }
                                    updateSelectedSpace(createdSpace.id, nextSpaces)
                                    loadSpaceLinks(createdSpace.id)
                                    showStatus("Space created")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(creatingSpace = false)
                                    }
                                }
                            }
                        },
                        onRenameSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            val originalSpace = workspaceState.selectedSpace() ?: return@WorkspaceScreen
                            scope.launch {
                                val title = try {
                                    renameSpaceTitle.trim().requireNonBlank("Space title is required")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                    return@launch
                                }

                                val optimisticSpace = originalSpace.copy(title = title)
                                val previousSpaces = workspaceState.spaces
                                updateOperations { operations ->
                                    operations.copy(renamingSpaceId = currentSpaceId)
                                }
                                updateWorkspace { state ->
                                    state.copy(
                                        spaces = state.spaces
                                            .map { space -> if (space.id == currentSpaceId) optimisticSpace else space }
                                            .sortedByTitle()
                                    )
                                }
                                updateSelectedSpace(currentSpaceId, workspaceState.spaces)
                                try {
                                    val renamedSpace = api().renameSpace(currentSpaceId, title)
                                    val nextSpaces = workspaceState.spaces
                                        .map { space -> if (space.id == currentSpaceId) renamedSpace else space }
                                        .sortedByTitle()
                                    updateWorkspace { state ->
                                        state.copy(spaces = nextSpaces)
                                    }
                                    updateSelectedSpace(currentSpaceId, nextSpaces)
                                    showStatus("Space renamed")
                                } catch (error: Throwable) {
                                    updateWorkspace { state ->
                                        state.copy(spaces = previousSpaces)
                                    }
                                    updateSelectedSpace(currentSpaceId, previousSpaces)
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(renamingSpaceId = null)
                                    }
                                }
                            }
                        },
                        onArchiveSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                val title = try {
                                    archiveSpaceTitle.trim().requireNonBlank("Archive space title is required")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                    return@launch
                                }

                                updateOperations { operations ->
                                    operations.copy(archivingSpaceId = currentSpaceId)
                                }
                                try {
                                    val archiveResponse = api().archiveSpace(currentSpaceId, title)
                                    val nextSpaces = (workspaceState.spaces + archiveResponse.space)
                                        .distinctBy { it.id }
                                        .sortedByTitle()
                                    archiveSpaceTitle = ""
                                    updateWorkspace { state ->
                                        state.copy(
                                            spaces = nextSpaces,
                                            selectedSpaceId = archiveResponse.space.id,
                                            linksBySpaceId = state.linksBySpaceId
                                                .minus(currentSpaceId) +
                                                (archiveResponse.space.id to emptyList())
                                        )
                                    }
                                    updateSelectedSpace(archiveResponse.space.id, nextSpaces)
                                    loadSpaceLinks(archiveResponse.space.id)
                                    showStatus(
                                        if (archiveResponse.movedLinksCount == 0) "Archive space created"
                                        else "Archived ${archiveResponse.movedLinksCount} links"
                                    )
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(archivingSpaceId = null)
                                    }
                                }
                            }
                        },
                        onDeleteSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                updateOperations { operations ->
                                    operations.copy(deletingSpaceId = currentSpaceId)
                                }
                                try {
                                    api().deleteSpace(currentSpaceId)
                                    val nextSpaces = workspaceState.spaces
                                        .filterNot { it.id == currentSpaceId }
                                        .sortedByTitle()
                                    val nextSelectedSpaceId = resolveSelectedSpaceId(
                                        spaces = nextSpaces,
                                        preferredSpaceId = nextSpaces.firstOrNull()?.id
                                    )
                                    updateWorkspace { state ->
                                        state.copy(
                                            spaces = nextSpaces,
                                            selectedSpaceId = nextSelectedSpaceId,
                                            linksBySpaceId = state.linksBySpaceId - currentSpaceId
                                        )
                                    }
                                    updateSelectedSpace(nextSelectedSpaceId, nextSpaces)
                                    nextSelectedSpaceId?.let(::loadSpaceLinks)
                                    showStatus("Space deleted")
                                } catch (error: Throwable) {
                                    handleActionError(error)
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(deletingSpaceId = null)
                                    }
                                }
                            }
                        },
                        onSaveLink = { urlInput ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            saveLink(currentSpaceId, urlInput)
                        },
                        onRetryLink = { linkKey ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            val failedLink = visibleLinks.firstOrNull { it.key == linkKey } ?: return@WorkspaceScreen
                            saveLink(
                                spaceId = currentSpaceId,
                                rawUrl = failedLink.retryUrl ?: failedLink.link.url,
                                existingKey = failedLink.key
                            )
                        },
                        onDismissFailedLink = { linkKey ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            updateSpaceLinks(currentSpaceId) { existing ->
                                existing.filterNot { it.key == linkKey }
                            }
                        },
                        onMoveLink = { linkId, targetSpaceId ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            val movedItem = visibleLinks.firstOrNull { it.link.id == linkId } ?: return@WorkspaceScreen
                            val targetWasCached = workspaceState.linksBySpaceId.containsKey(targetSpaceId)
                            val movedPreview = movedItem.copy(
                                link = movedItem.link.copy(spaceId = targetSpaceId)
                            )

                            updateOperations { operations ->
                                operations.copy(movingLinkIds = operations.movingLinkIds + linkId)
                            }
                            updateSpaceLinks(currentSpaceId) { existing ->
                                existing.filterNot { it.link.id == linkId }
                            }
                            if (targetWasCached) {
                                updateSpaceLinks(targetSpaceId) { existing ->
                                    listOf(movedPreview) + existing.filterNot { it.link.id == linkId }
                                }
                            }

                            scope.launch {
                                try {
                                    val movedLink = api().moveLink(linkId, targetSpaceId)
                                    if (targetWasCached) {
                                        updateSpaceLinks(targetSpaceId) { existing ->
                                            listOf(movedLink.toWebLinkItem()) + existing.filterNot { it.link.id == linkId }
                                        }
                                    }
                                    showStatus("Link moved")
                                } catch (error: Throwable) {
                                    if (!handleActionError(error)) {
                                        updateSpaceLinks(currentSpaceId) { existing ->
                                            listOf(movedItem) + existing.filterNot { it.link.id == linkId }
                                        }
                                        if (targetWasCached) {
                                            updateSpaceLinks(targetSpaceId) { existing ->
                                                existing.filterNot { it.link.id == linkId }
                                            }
                                        }
                                    }
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(movingLinkIds = operations.movingLinkIds - linkId)
                                    }
                                }
                            }
                        },
                        onDeleteLink = { linkId ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            val deletedItem = visibleLinks.firstOrNull { it.link.id == linkId } ?: return@WorkspaceScreen

                            updateOperations { operations ->
                                operations.copy(deletingLinkIds = operations.deletingLinkIds + linkId)
                            }
                            updateSpaceLinks(currentSpaceId) { existing ->
                                existing.filterNot { it.link.id == linkId }
                            }

                            scope.launch {
                                try {
                                    api().deleteLink(linkId)
                                    showStatus("Link deleted")
                                } catch (error: Throwable) {
                                    if (!handleActionError(error)) {
                                        updateSpaceLinks(currentSpaceId) { existing ->
                                            listOf(deletedItem) + existing.filterNot { it.link.id == linkId }
                                        }
                                    }
                                } finally {
                                    updateOperations { operations ->
                                        operations.copy(deletingLinkIds = operations.deletingLinkIds - linkId)
                                    }
                                }
                            }
                        }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (isBlockingWorkspaceLoad) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = if (workspaceState.operations.sessionRestore) "Restoring session..."
                                else "Loading workspace...",
                                modifier = Modifier.padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class WorkspaceSnapshot(
    val user: pl.jsyty.linkstash.contracts.user.UserDto,
    val spaces: List<SpaceDto>,
    val csrfToken: String,
    val selectedSpaceId: String?,
    val links: List<LinkDto>
)

private const val METADATA_POLL_INTERVAL_MS = 2_000L
private const val METADATA_POLL_MAX_ATTEMPTS = 10
