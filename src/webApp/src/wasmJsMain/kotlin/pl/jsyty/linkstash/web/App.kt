package pl.jsyty.linkstash.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto
import kotlinx.coroutines.launch

@Composable
fun App() {
    var apiBaseUrl by remember { mutableStateOf(loadStoredApiBaseUrl()) }
    var apiBaseUrlInput by remember { mutableStateOf(apiBaseUrl) }
    var rawToken by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf("") }
    var newSpaceTitle by remember { mutableStateOf("") }
    var renameSpaceTitle by remember { mutableStateOf("") }
    var csrfToken by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf<UserDto?>(null) }
    var spaces by remember { mutableStateOf(emptyList<SpaceDto>()) }
    var selectedSpaceId by remember { mutableStateOf(loadStoredSelectedSpaceId()) }
    var links by remember { mutableStateOf(emptyList<LinkDto>()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Paste a token to sign in.") }
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

    fun clearAuthenticatedState(message: String) {
        csrfToken = null
        user = null
        spaces = emptyList()
        links = emptyList()
        selectSpace(null)
        statusMessage = message
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
    }

    suspend fun runBusyAction(action: suspend () -> Unit) {
        isBusy = true
        errorMessage = null
        try {
            action()
        } catch (error: Throwable) {
            if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
                clearAuthenticatedState("Session expired. Paste a token to sign in again.")
            }
            errorMessage = error.humanMessage()
            snackbarHostState.showSnackbar(error.humanMessage())
        } finally {
            isBusy = false
        }
    }

    LaunchedEffect(apiBaseUrl) {
        try {
            refreshWorkspace(baseUrl = apiBaseUrl)
            statusMessage = "Session restored"
            errorMessage = null
        } catch (error: Throwable) {
            if (error is ApiException && error.error.code == ApiErrorCode.UNAUTHORIZED) {
                clearAuthenticatedState("Paste a token to start a new session.")
                errorMessage = null
            } else {
                errorMessage = error.humanMessage()
            }
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
                if (user == null) {
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
                                    apiBaseUrl = nextApiBaseUrl
                                    storeApiBaseUrl(nextApiBaseUrl)
                                    apiBaseUrlInput = nextApiBaseUrl
                                    rawToken = ""
                                    user = exchange.user
                                    csrfToken = exchange.csrfToken
                                    refreshWorkspace(baseUrl = nextApiBaseUrl)
                                    statusMessage = "Signed in"
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
                        pendingUrl = pendingUrl,
                        onPendingUrlChange = { pendingUrl = it },
                        links = links,
                        isBusy = isBusy,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onRefresh = {
                            scope.launch {
                                runBusyAction {
                                    refreshWorkspace()
                                    statusMessage = "Refreshed"
                                }
                            }
                        },
                        onLogout = {
                            scope.launch {
                                runBusyAction {
                                    api().logout()
                                    clearAuthenticatedState("Logged out")
                                }
                            }
                        },
                        onExport = {
                            scope.launch {
                                runBusyAction {
                                    copyCurrentLinksToClipboard(links)
                                    statusMessage = if (links.isEmpty()) {
                                        "No links to copy"
                                    } else {
                                        "Copied ${links.size} links"
                                    }
                                }
                            }
                        },
                        onSelectSpace = { space ->
                            selectSpace(space.id)
                            scope.launch {
                                runBusyAction {
                                    val visibleLinks = api().listLinks(space.id)
                                    updateVisibleLinks(visibleLinks)
                                    statusMessage = "Loaded ${space.title}"
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
                                    statusMessage = "Space created"
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
                                    statusMessage = "Space renamed"
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
                                    statusMessage = "Space deleted"
                                }
                            }
                        },
                        onSaveLink = {
                            val currentSpaceId = selectedSpaceId ?: return@WorkspaceScreen
                            scope.launch {
                                runBusyAction {
                                    val url = pendingUrl.trim().requireHttpUrl()
                                    val createdLink = api().createLink(currentSpaceId, url)
                                    pendingUrl = ""
                                    updateVisibleLinks(listOf(createdLink) + links.filterNot { it.id == createdLink.id })
                                    statusMessage = "Link saved"
                                }
                            }
                        },
                        onMoveLink = { linkId, targetSpaceId ->
                            scope.launch {
                                runBusyAction {
                                    api().moveLink(linkId, targetSpaceId)
                                    updateVisibleLinks(links.filterNot { it.id == linkId })
                                    statusMessage = "Link moved"
                                }
                            }
                        },
                        onDeleteLink = { linkId ->
                            scope.launch {
                                runBusyAction {
                                    api().deleteLink(linkId)
                                    updateVisibleLinks(links.filterNot { it.id == linkId })
                                    statusMessage = "Link deleted"
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
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}
