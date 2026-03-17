package pl.jsyty.linkstash.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto
import kotlinx.coroutines.launch

@Composable
fun App() {
    val storedSessionExpected = remember { loadStoredSessionExpected() }
    var apiBaseUrl by remember { mutableStateOf(loadStoredApiBaseUrl()) }
    var apiBaseUrlInput by remember { mutableStateOf(apiBaseUrl) }
    var rawToken by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf("") }
    var newSpaceTitle by remember { mutableStateOf("") }
    var renameSpaceTitle by remember { mutableStateOf("") }
    var archiveSpaceTitle by remember { mutableStateOf("") }
    var csrfToken by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<UserDto?>(null) }
    var spaces by remember { mutableStateOf(emptyList<SpaceDto>()) }
    var selectedSpaceId by remember { mutableStateOf(loadStoredSelectedSpaceId()) }
    var links by remember { mutableStateOf(emptyList<LinkDto>()) }
    var shouldShowWorkspace by remember { mutableStateOf(storedSessionExpected) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun api(baseUrl: String = apiBaseUrl) = BrowserApi(baseUrl) { csrfToken }

    fun selectSpace(spaceId: String?, availableSpaces: List<SpaceDto> = spaces) {
        selectedSpaceId = spaceId
        renameSpaceTitle = availableSpaces.firstOrNull { it.id == spaceId }?.title.orEmpty()
        storeSelectedSpaceId(spaceId)
    }

    fun updateVisibleLinks(nextLinks: List<LinkDto>) {
        links = nextLinks
    }

    fun setSessionExpected(expected: Boolean) {
        shouldShowWorkspace = expected
        storeSessionExpected(expected)
    }

    fun clearAuthenticatedState() {
        csrfToken = null
        user = null
        spaces = emptyList()
        links = emptyList()
        selectSpace(null)
        setSessionExpected(false)
    }

    suspend fun showStatus(message: String) {
        snackbarHostState.showSnackbar(message)
    }

    suspend fun refreshWorkspace(preferredSpaceId: String? = selectedSpaceId, baseUrl: String = apiBaseUrl) {
        val currentApi = api(baseUrl)
        val currentUser = currentApi.me()
        val availableSpaces = currentApi.listSpaces().sortedBy { it.title.lowercase() }
        val nextCsrfToken = currentApi.fetchCsrfToken()
        val resolvedSpaceId = preferredSpaceId
            ?.takeIf { preferredId -> availableSpaces.any { it.id == preferredId } }
            ?: availableSpaces.firstOrNull()?.id
        val visibleLinks = resolvedSpaceId
            ?.let { currentApi.listLinks(it) }
            ?: emptyList()

        user = currentUser
        csrfToken = nextCsrfToken
        spaces = availableSpaces
        updateVisibleLinks(visibleLinks)
        selectSpace(resolvedSpaceId, availableSpaces)
        setSessionExpected(true)
    }

    suspend fun runBusyAction(action: suspend () -> Unit) {
        isBusy = true
        errorMessage = null
        try {
            action()
        } catch (error: Throwable) {
            if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
                clearAuthenticatedState()
            }
            errorMessage = error.humanMessage()
            snackbarHostState.showSnackbar(error.humanMessage())
        } finally {
            isBusy = false
        }
    }

    LaunchedEffect(apiBaseUrl, shouldShowWorkspace, user) {
        if (!shouldShowWorkspace || user != null) {
            return@LaunchedEffect
        }

        isBusy = true
        try {
            refreshWorkspace(baseUrl = apiBaseUrl)
            errorMessage = null
        } catch (error: Throwable) {
            if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
                clearAuthenticatedState()
                errorMessage = null
            } else {
                errorMessage = error.humanMessage()
                snackbarHostState.showSnackbar(error.humanMessage())
            }
        } finally {
            isBusy = false
        }
    }

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
                        isBusy = isBusy,
                        errorMessage = errorMessage,
                        onSignIn = {
                            val nextApiBaseUrl = apiBaseUrlInput.trim().ifBlank { suggestedApiBaseUrl() }
                            scope.launch {
                                runBusyAction {
                                    val exchange = api(nextApiBaseUrl).exchangeRaindropToken(rawToken.trim())
                                    setSessionExpected(true)
                                    apiBaseUrl = nextApiBaseUrl
                                    storeApiBaseUrl(nextApiBaseUrl)
                                    apiBaseUrlInput = nextApiBaseUrl
                                    rawToken = ""
                                    user = exchange.user
                                    csrfToken = exchange.csrfToken
                                    refreshWorkspace(baseUrl = nextApiBaseUrl)
                                    showStatus("Signed in")
                                }
                            }
                        }
                    )
                } else {
                    WorkspaceScreen(
                        user = user,
                        spaces = spaces,
                        selectedSpaceId = selectedSpaceId,
                        renameSpaceTitle = renameSpaceTitle,
                        onRenameSpaceTitleChange = { renameSpaceTitle = it },
                        newSpaceTitle = newSpaceTitle,
                        onNewSpaceTitleChange = { newSpaceTitle = it },
                        archiveSpaceTitle = archiveSpaceTitle,
                        onArchiveSpaceTitleChange = { archiveSpaceTitle = it },
                        pendingUrl = pendingUrl,
                        onPendingUrlChange = { pendingUrl = it },
                        links = links,
                        isBusy = isBusy,
                        onRefresh = {
                            scope.launch {
                                runBusyAction {
                                    refreshWorkspace()
                                    showStatus("Refreshed")
                                }
                            }
                        },
                        onLogout = {
                            scope.launch {
                                runBusyAction {
                                    api().logout()
                                    clearAuthenticatedState()
                                    showStatus("Logged out")
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                runBusyAction {
                                    copyCurrentLinksToClipboard(links)
                                    showStatus(if (links.isEmpty()) "No links to copy" else "Copied ${links.size} links")
                                }
                            }
                        },
                        onSelectSpace = { space ->
                            selectSpace(space.id)
                            scope.launch {
                                runBusyAction {
                                    val visibleLinks = api().listLinks(space.id)
                                    updateVisibleLinks(visibleLinks)
                                    showStatus("Loaded ${space.title}")
                                }
                            }
                        },
                        onCreateSpace = {
                            scope.launch {
                                runBusyAction {
                                    val title = newSpaceTitle.trim().requireNonBlank("Space title is required")
                                    val createdSpace = api().createSpace(title)
                                    val nextSpaces = (spaces + createdSpace)
                                        .distinctBy { it.id }
                                        .sortedBy { it.title.lowercase() }
                                    spaces = nextSpaces
                                    selectSpace(createdSpace.id, nextSpaces)
                                    updateVisibleLinks(emptyList())
                                    newSpaceTitle = ""
                                    showStatus("Space created")
                                }
                            }
                        },
                        onRenameSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                runBusyAction {
                                    val title = renameSpaceTitle.trim().requireNonBlank("Space title is required")
                                    val renamedSpace = api().renameSpace(currentSpaceId, title)
                                    val nextSpaces = spaces
                                        .map { space -> if (space.id == currentSpaceId) renamedSpace else space }
                                        .sortedBy { it.title.lowercase() }
                                    spaces = nextSpaces
                                    selectSpace(currentSpaceId, nextSpaces)
                                    showStatus("Space renamed")
                                }
                            }
                        },
                        onArchiveSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                runBusyAction {
                                    val title = archiveSpaceTitle.trim().requireNonBlank("Archive space title is required")
                                    val archiveResponse = api().archiveSpace(currentSpaceId, title)
                                    archiveSpaceTitle = ""
                                    refreshWorkspace(preferredSpaceId = archiveResponse.space.id)
                                    showStatus(
                                        if (archiveResponse.movedLinksCount == 0) "Archive space created"
                                        else "Archived ${archiveResponse.movedLinksCount} links"
                                    )
                                }
                            }
                        },
                        onDeleteSpace = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                runBusyAction {
                                    api().deleteSpace(currentSpaceId)
                                    val nextSpaces = spaces
                                        .filterNot { it.id == currentSpaceId }
                                        .sortedBy { it.title.lowercase() }
                                    spaces = nextSpaces
                                    val nextSelectedSpaceId = nextSpaces.firstOrNull()?.id
                                    if (nextSelectedSpaceId == null) {
                                        selectSpace(null, nextSpaces)
                                        updateVisibleLinks(emptyList())
                                    } else {
                                        val nextLinks = api().listLinks(nextSelectedSpaceId)
                                        selectSpace(nextSelectedSpaceId, nextSpaces)
                                        updateVisibleLinks(nextLinks)
                                    }
                                    showStatus("Space deleted")
                                }
                            }
                        },
                        onSaveLink = { urlInput ->
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                runBusyAction {
                                    val url = urlInput.trim().requireHttpUrl()
                                    val createdLink = api().createLink(currentSpaceId, url)
                                    pendingUrl = ""
                                    updateVisibleLinks(listOf(createdLink) + links.filterNot { it.id == createdLink.id })
                                    showStatus("Link saved")
                                }
                            }
                        },
                        onMoveLink = { linkId, targetSpaceId ->
                            scope.launch {
                                runBusyAction {
                                    api().moveLink(linkId, targetSpaceId)
                                    updateVisibleLinks(links.filterNot { it.id == linkId })
                                    showStatus("Link moved")
                                }
                            }
                        },
                        onDeleteLink = { linkId ->
                            scope.launch {
                                runBusyAction {
                                    api().deleteLink(linkId)
                                    updateVisibleLinks(links.filterNot { it.id == linkId })
                                    showStatus("Link deleted")
                                }
                            }
                        }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )

                if (isBusy) {
                    Surface(
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Working...",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
