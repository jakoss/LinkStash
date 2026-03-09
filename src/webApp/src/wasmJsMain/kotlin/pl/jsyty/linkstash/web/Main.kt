@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.jsyty.linkstash.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import io.ktor.http.HttpStatusCode
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlin.js.unsafeCast
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLAnchorElement
import org.w3c.fetch.Headers
import org.w3c.fetch.Response
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.contracts.auth.AuthCsrfTokenResponse
import pl.jsyty.linkstash.contracts.auth.AuthExchangeResponse
import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiError
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.error.ApiErrorEnvelope
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse
import pl.jsyty.linkstash.contracts.user.UserDto

private const val apiBaseUrlStorageKey = "linkstash.web.apiBaseUrl"
private const val selectedSpaceStorageKey = "linkstash.web.selectedSpaceId"
private const val csrfHeaderName = "X-CSRF-Token"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "root") {
        App()
    }
}

@Composable
private fun App() {
    var apiBaseUrl by remember { mutableStateOf(loadStoredApiBaseUrl()) }
    var apiBaseUrlInput by remember { mutableStateOf(apiBaseUrl) }
    var rawToken by remember { mutableStateOf("") }
    var pendingUrl by remember { mutableStateOf("") }
    var newSpaceTitle by remember { mutableStateOf("") }
    var renameSpaceTitle by remember { mutableStateOf("") }
    var exportPreview by remember { mutableStateOf("") }
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
        exportPreview = nextLinks.joinToString(separator = "\n") { it.url }
    }

    fun clearAuthenticatedState(message: String) {
        csrfToken = null
        user = null
        spaces = emptyList()
        links = emptyList()
        exportPreview = ""
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
                        exportPreview = exportPreview,
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
                            exportCurrentLinks(
                                spaceTitle = spaces.firstOrNull { it.id == selectedSpaceId }?.title ?: "links",
                                links = links
                            )
                            statusMessage = "Export downloaded"
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
                        .padding(20.dp)
                )

                if (isBusy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    apiBaseUrlInput: String,
    onApiBaseUrlInputChange: (String) -> Unit,
    rawToken: String,
    onRawTokenChange: (String) -> Unit,
    isBusy: Boolean,
    errorMessage: String?,
    onSignIn: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .width(720.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "LinkStash",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Paste a Raindrop token. By default the app uses the same origin as the page and exchanges the token for an HTTP-only session cookie.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiBaseUrlInput,
                    onValueChange = onApiBaseUrlInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    label = { Text("API base URL") },
                    placeholder = { Text("Same origin (recommended)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = rawToken,
                    onValueChange = onRawTokenChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    enabled = !isBusy,
                    label = { Text("Raindrop token") },
                    placeholder = { Text("Paste your Raindrop API token") }
                )
                if (errorMessage != null) {
                    StatusCard(message = errorMessage, isError = true)
                }
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy
                ) {
                    Text(if (isBusy) "Connecting..." else "Sign in")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceScreen(
    user: UserDto?,
    spaces: List<SpaceDto>,
    selectedSpaceId: String?,
    renameSpaceTitle: String,
    onRenameSpaceTitleChange: (String) -> Unit,
    newSpaceTitle: String,
    onNewSpaceTitleChange: (String) -> Unit,
    pendingUrl: String,
    onPendingUrlChange: (String) -> Unit,
    links: List<LinkDto>,
    exportPreview: String,
    isBusy: Boolean,
    statusMessage: String,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onExport: () -> Unit,
    onSelectSpace: (SpaceDto) -> Unit,
    onCreateSpace: () -> Unit,
    onRenameSpace: () -> Unit,
    onDeleteSpace: () -> Unit,
    onSaveLink: () -> Unit,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit
) {
    val selectedSpace = spaces.firstOrNull { it.id == selectedSpaceId }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = selectedSpace?.title ?: "No space selected",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.displayName ?: user?.id.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(onClick = onRefresh, enabled = !isBusy) {
                        Text("Refresh")
                    }
                    FilledTonalButton(onClick = onExport, enabled = !isBusy) {
                        Text("Export links")
                    }
                    TextButton(onClick = onLogout, enabled = !isBusy) {
                        Text("Log out")
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Spaces",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    spaces.forEach { space ->
                        FilterChip(
                            selected = selectedSpaceId == space.id,
                            onClick = { onSelectSpace(space) },
                            enabled = !isBusy,
                            label = { Text(space.title) }
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newSpaceTitle,
                        onValueChange = onNewSpaceTitleChange,
                        label = { Text("Create a space") },
                        placeholder = { Text("Reading queue") },
                        enabled = !isBusy,
                        singleLine = true,
                        modifier = Modifier.width(280.dp)
                    )
                    Button(onClick = onCreateSpace, enabled = !isBusy) {
                        Text("Add space")
                    }
                }
                if (selectedSpaceId != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = renameSpaceTitle,
                            onValueChange = onRenameSpaceTitleChange,
                            label = { Text("Rename selected") },
                            enabled = !isBusy,
                            singleLine = true,
                            modifier = Modifier.width(280.dp)
                        )
                        Button(onClick = onRenameSpace, enabled = !isBusy) {
                            Text("Rename")
                        }
                        TextButton(onClick = onDeleteSpace, enabled = !isBusy) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Paste a URL into the current space",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = pendingUrl,
                    onValueChange = onPendingUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && selectedSpaceId != null,
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/article") },
                    singleLine = true
                )
                Button(
                    onClick = onSaveLink,
                    enabled = !isBusy && selectedSpaceId != null && pendingUrl.isNotBlank()
                ) {
                    Text("Save link")
                }
            }
        }

        StatusCard(message = errorMessage ?: statusMessage, isError = errorMessage != null)

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = selectedSpace?.title ?: "Links",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (links.isEmpty()) {
                    Text(
                        text = "No links yet. Save a URL above or switch to another space.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    links.forEach { link ->
                        LinkCard(
                            link = link,
                            spaces = spaces,
                            isBusy = isBusy,
                            onMoveLink = onMoveLink,
                            onDeleteLink = onDeleteLink
                        )
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Export preview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = exportPreview,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    readOnly = true
                )
            }
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun LinkCard(
    link: LinkDto,
    spaces: List<SpaceDto>,
    isBusy: Boolean,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var isMenuExpanded by remember(link.id) { mutableStateOf(false) }
    val moveTargets = remember(spaces, link.spaceId) {
        spaces.filter { it.id != link.spaceId }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = link.title ?: link.url,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { uriHandler.openUri(link.url) }
            )
            Text(
                text = link.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!link.excerpt.isNullOrBlank()) {
                val excerpt = link.excerpt.orEmpty()
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(link.spaceId) }
                )
                if (!link.createdAt.isNullOrBlank()) {
                    val createdAt = link.createdAt.orEmpty()
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(createdAt) }
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { uriHandler.openUri(link.url) }) {
                    Text("Open")
                }
                if (moveTargets.isNotEmpty()) {
                    Box {
                        FilledTonalButton(
                            onClick = { isMenuExpanded = true },
                            enabled = !isBusy
                        ) {
                            Text("Move")
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            moveTargets.forEach { space ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(space.title) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onMoveLink(link.id, space.id)
                                    }
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { onDeleteLink(link.id) },
                    enabled = !isBusy
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

private class BrowserApi(
    private val baseUrl: String,
    private val csrfTokenProvider: () -> String?
) {
    suspend fun exchangeRaindropToken(rawToken: String): AuthExchangeResponse {
        val token = rawToken.trim().requireNonBlank("Raindrop token is required")
        return request(
            path = "/v1/auth/raindrop/token",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(
                AuthRaindropTokenExchangeRequest(
                    accessToken = token,
                    sessionMode = AuthSessionMode.COOKIE
                )
            )
        )
    }

    suspend fun fetchCsrfToken(): String {
        return request<AuthCsrfTokenResponse>(
            path = "/v1/auth/csrf",
            method = "GET",
            includeCsrf = false
        ).csrfToken
    }

    suspend fun me(): UserDto = request(path = "/v1/me", method = "GET", includeCsrf = false)

    suspend fun logout() {
        requestUnit(path = "/v1/auth/logout", method = "POST")
    }

    suspend fun listSpaces(): List<SpaceDto> {
        return request<SpacesListResponse>(path = "/v1/spaces", method = "GET", includeCsrf = false).spaces
    }

    suspend fun createSpace(title: String): SpaceDto {
        return request(
            path = "/v1/spaces",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(SpaceCreateRequest(title = title))
        )
    }

    suspend fun renameSpace(spaceId: String, title: String): SpaceDto {
        return request(
            path = "/v1/spaces/$spaceId",
            method = "PATCH",
            bodyText = LinkStashJson.instance.encodeToString(SpaceRenameRequest(title = title))
        )
    }

    suspend fun deleteSpace(spaceId: String) {
        requestUnit(path = "/v1/spaces/$spaceId", method = "DELETE")
    }

    suspend fun listLinks(spaceId: String): List<LinkDto> {
        return request<LinksListResponse>(
            path = "/v1/spaces/$spaceId/links",
            method = "GET",
            includeCsrf = false
        ).links
    }

    suspend fun createLink(spaceId: String, url: String): LinkDto {
        return request(
            path = "/v1/spaces/$spaceId/links",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(LinkCreateRequest(url = url))
        )
    }

    suspend fun moveLink(linkId: String, targetSpaceId: String): LinkDto {
        return request(
            path = "/v1/links/$linkId",
            method = "PATCH",
            bodyText = LinkStashJson.instance.encodeToString(LinkMoveRequest(spaceId = targetSpaceId))
        )
    }

    suspend fun deleteLink(linkId: String) {
        requestUnit(path = "/v1/links/$linkId", method = "DELETE")
    }

    private suspend inline fun <reified T> request(
        path: String,
        method: String,
        bodyText: String? = null,
        includeCsrf: Boolean = true
    ): T {
        val headers = Headers().apply {
            append("Accept", "application/json")
            if (bodyText != null) {
                append("Content-Type", "application/json")
            }
            if (includeCsrf) {
                csrfTokenProvider()?.takeIf { it.isNotBlank() }?.let {
                    append(csrfHeaderName, it)
                }
            }
        }

        val requestInit = createRequestInit()
        requestInit.method = method
        requestInit.headers = headers
        requestInit.credentials = "include".toJsString().unsafeCast<RequestCredentials>()
        if (bodyText != null) {
            requestInit.body = bodyText.toJsString()
        }

        val response = window.fetch(
            "${baseUrl.trimEnd('/')}$path",
            requestInit
        ).await<Response>()
        val responseText = response.text().await<JsString>().toString()
        if (!response.ok) {
            throw ApiException(
                error = parseApiError(status = response.status.toInt(), bodyText = responseText),
                statusCode = HttpStatusCode.fromValue(response.status.toInt())
            )
        }
        return LinkStashJson.instance.decodeFromString(responseText)
    }

    private suspend fun requestUnit(
        path: String,
        method: String,
        bodyText: String? = null,
        includeCsrf: Boolean = true
    ) {
        val headers = Headers().apply {
            append("Accept", "application/json")
            if (bodyText != null) {
                append("Content-Type", "application/json")
            }
            if (includeCsrf) {
                csrfTokenProvider()?.takeIf { it.isNotBlank() }?.let {
                    append(csrfHeaderName, it)
                }
            }
        }

        val requestInit = createRequestInit()
        requestInit.method = method
        requestInit.headers = headers
        requestInit.credentials = "include".toJsString().unsafeCast<RequestCredentials>()
        if (bodyText != null) {
            requestInit.body = bodyText.toJsString()
        }

        val response = window.fetch(
            "${baseUrl.trimEnd('/')}$path",
            requestInit
        ).await<Response>()
        val responseText = response.text().await<JsString>().toString()
        if (!response.ok) {
            throw ApiException(
                error = parseApiError(status = response.status.toInt(), bodyText = responseText),
                statusCode = HttpStatusCode.fromValue(response.status.toInt())
            )
        }
    }
}

@JsFun("() => ({})")
private external fun createJsObject(): JsAny

private fun createRequestInit(): RequestInit = createJsObject().unsafeCast<RequestInit>()

private fun parseApiError(status: Int, bodyText: String): ApiError {
    if (bodyText.isBlank()) {
        return ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status $status"
        )
    }

    return runCatching {
        LinkStashJson.instance.decodeFromString<ApiErrorEnvelope>(bodyText).error
    }.recoverCatching {
        LinkStashJson.instance.decodeFromString<ApiError>(bodyText)
    }.getOrElse {
        ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status $status"
        )
    }
}

private fun Int.toApiErrorCode(): ApiErrorCode {
    return when (this) {
        401 -> ApiErrorCode.UNAUTHORIZED
        403 -> ApiErrorCode.FORBIDDEN
        404 -> ApiErrorCode.NOT_FOUND
        409 -> ApiErrorCode.CONFLICT
        422 -> ApiErrorCode.VALIDATION_ERROR
        429 -> ApiErrorCode.RATE_LIMITED
        in 500..599 -> ApiErrorCode.UPSTREAM_ERROR
        else -> ApiErrorCode.UNKNOWN
    }
}

private fun exportCurrentLinks(spaceTitle: String, links: List<LinkDto>) {
    val payload = links.joinToString(separator = "\n") { it.url }
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = "data:text/plain;charset=utf-8,${encodeURIComponent(payload)}"
    anchor.download = "${spaceTitle.slugify()}-links.txt"
    document.body?.appendChild(anchor)
    anchor.click()
    anchor.remove()
}

private fun loadStoredApiBaseUrl(): String {
    return localStorage.getItem(apiBaseUrlStorageKey)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: suggestedApiBaseUrl()
}

private fun storeApiBaseUrl(value: String) {
    localStorage.setItem(apiBaseUrlStorageKey, value)
}

private fun loadStoredSelectedSpaceId(): String? {
    return localStorage.getItem(selectedSpaceStorageKey)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun storeSelectedSpaceId(spaceId: String?) {
    if (spaceId == null) {
        localStorage.removeItem(selectedSpaceStorageKey)
    } else {
        localStorage.setItem(selectedSpaceStorageKey, spaceId)
    }
}

private fun suggestedApiBaseUrl(): String {
    val protocol = window.location.protocol.ifBlank { "http:" }
    val host = window.location.hostname.ifBlank { "localhost" }
    val port = window.location.port

    if ((host == "localhost" || host == "127.0.0.1") && port == "8081") {
        return "$protocol//$host:8080"
    }

    return if (port.isBlank()) {
        "$protocol//$host"
    } else {
        "$protocol//$host:$port"
    }
}

private fun String.requireNonBlank(message: String): String {
    return takeIf { it.isNotBlank() } ?: error(message)
}

private fun String.requireHttpUrl(): String {
    return takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: error("URL must start with http:// or https://")
}

private fun Throwable.humanMessage(): String {
    return when (this) {
        is ApiException -> error.message
        else -> message ?: this::class.simpleName ?: "Unexpected error"
    }
}

private fun String.slugify(): String {
    return lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "linkstash" }
}

private fun linkStashColorScheme() = lightColorScheme(
    primary = Color(0xFF116466),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F0EF),
    onPrimaryContainer = Color(0xFF082F30),
    secondary = Color(0xFFBA6B2D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE1CC),
    onSecondaryContainer = Color(0xFF3A1F08),
    background = Color(0xFFF3EFE7),
    onBackground = Color(0xFF1D2522),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF1D2522),
    surfaceVariant = Color(0xFFE6DFD4),
    onSurfaceVariant = Color(0xFF4B635F),
    error = Color(0xFFAA3D33),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@JsName("encodeURIComponent")
private external fun encodeURIComponent(value: String): String
