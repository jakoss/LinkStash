package pl.jsyty.linkstash.linkstash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto

@Composable
fun LinkStashApp(
    viewModel: LinkStashViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    LinkStashScreen(
        uiState = uiState,
        onUseBearerToken = viewModel::useBearerToken,
        onRefresh = viewModel::refresh,
        onSync = viewModel::syncPendingQueue,
        onLogout = viewModel::logout,
        onSpaceSelected = viewModel::selectSpace,
        onMoveLink = viewModel::moveLink,
        onDeleteLink = viewModel::deleteLink,
        onLoadMore = viewModel::loadMoreLinks,
        onSaveManualUrl = viewModel::saveManualUrl
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkStashScreen(
    uiState: LinkStashUiState,
    onUseBearerToken: (String) -> Unit,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit,
    onSpaceSelected: (String) -> Unit,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit,
    onLoadMore: () -> Unit,
    onSaveManualUrl: (String) -> Unit
) {
    var manualUrl by rememberSaveable { mutableStateOf("") }
    var manualBearerToken by rememberSaveable { mutableStateOf("") }
    var isOverflowMenuExpanded by remember { mutableStateOf(false) }
    var isUrlSheetVisible by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedSpace = remember(uiState.spaces, uiState.selectedSpaceId) {
        uiState.spaces.firstOrNull { it.id == uiState.selectedSpaceId } ?: uiState.spaces.firstOrNull()
    }

    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "LinkStash"
                        )
                        Text(
                            text = if (uiState.isAuthenticated) {
                                selectedSpace?.title ?: "Inbox"
                            } else {
                                "Share now, sync when ready"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (uiState.isAuthenticated) {
                        Box {
                            IconButton(onClick = { isOverflowMenuExpanded = true }) {
                                Text(
                                    text = "⋮",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }

                            DropdownMenu(
                                expanded = isOverflowMenuExpanded,
                                onDismissRequest = { isOverflowMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onRefresh()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sync queue") },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onSync()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logout") },
                                    onClick = {
                                        isOverflowMenuExpanded = false
                                        onLogout()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isUrlSheetVisible = true },
                content = { Text("Paste URL") }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (uiState.isAuthenticated) {
                    AuthenticatedScreen(
                        uiState = uiState,
                        onSpaceSelected = onSpaceSelected,
                        onMoveLink = onMoveLink,
                        onDeleteLink = onDeleteLink,
                        onLoadMore = onLoadMore
                    )
                } else {
                    LoggedOutScreen(
                        uiState = uiState,
                        manualBearerToken = manualBearerToken,
                        onManualBearerTokenChange = { manualBearerToken = it },
                        onUseBearerToken = {
                            onUseBearerToken(manualBearerToken)
                            manualBearerToken = ""
                        }
                    )
                }
            }
        }
    }

    if (isUrlSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isUrlSheetVisible = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Paste URL",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = if (uiState.isAuthenticated) {
                        "Links save to ${selectedSpace?.title ?: "Inbox"} by default."
                    } else {
                        "Shared or pasted links stay in your queue until you log in."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL") }
                )
                Button(
                    onClick = {
                        onSaveManualUrl(manualUrl)
                        manualUrl = ""
                        isUrlSheetVisible = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = manualUrl.isNotBlank()
                ) {
                    Text("Save link")
                }
            }
        }
    }
}

@Composable
private fun LoggedOutScreen(
    uiState: LinkStashUiState,
    manualBearerToken: String,
    onManualBearerTokenChange: (String) -> Unit,
    onUseBearerToken: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Inbox-first saving",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Paste a Raindrop token once, then every shared link lands in Inbox and syncs from the queue automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatPill(text = "${uiState.pendingQueueCount} queued")
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connect Raindrop",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = manualBearerToken,
                    onValueChange = onManualBearerTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Raindrop token") },
                    singleLine = true
                )
                Button(
                    onClick = onUseBearerToken,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = manualBearerToken.isNotBlank()
                ) {
                    Text("Use token")
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedScreen(
    uiState: LinkStashUiState,
    onSpaceSelected: (String) -> Unit,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.spaces.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Spaces",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.spaces, key = { it.id }) { space ->
                            FilterChip(
                                selected = space.id == uiState.selectedSpaceId,
                                onClick = { onSpaceSelected(space.id) },
                                label = { Text(space.title) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.links.isEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nothing here yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (uiState.spaces.isEmpty()) {
                                uiState.statusMessage
                            } else {
                                "New shared links land in Inbox first. Use the button below to paste one manually."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Recent links",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(uiState.links, key = { it.id }) { link ->
                LinkCard(
                    link = link,
                    isPendingMetadata = link.id in uiState.pendingMetadataLinkIds,
                    spaces = uiState.spaces,
                    onMoveLink = onMoveLink,
                    onDeleteLink = onDeleteLink
                )
            }
        }

        if (uiState.nextCursor != null) {
            item {
                FilledTonalButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load more")
                }
            }
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkDto,
    isPendingMetadata: Boolean,
    spaces: List<SpaceDto>,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit
) {
    var isMoveMenuExpanded by remember(link.id) { mutableStateOf(false) }
    val destinationSpaces = remember(spaces, link.spaceId) {
        spaces.filter { it.id != link.spaceId }
    }
    val compactUrlLabel = remember(link.url) { compactUrlLabel(link.url) }
    val displayTitle = remember(link.title, link.url, compactUrlLabel) {
        link.title
            ?.takeIf { it.isNotBlank() && it != link.url }
            ?: compactUrlLabel
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!link.previewImageUrl.isNullOrBlank() || isPendingMetadata) {
                LinkCardPreview(
                    previewImageUrl = link.previewImageUrl,
                    title = displayTitle,
                    isPendingMetadata = isPendingMetadata
                )
            }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = link.url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            link.excerpt?.takeIf { it.isNotBlank() }?.let { excerpt ->
                Text(
                    text = excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (destinationSpaces.isNotEmpty()) {
                    Box {
                        FilledTonalButton(onClick = { isMoveMenuExpanded = true }) {
                            Text("Move")
                        }

                        DropdownMenu(
                            expanded = isMoveMenuExpanded,
                            onDismissRequest = { isMoveMenuExpanded = false }
                        ) {
                            destinationSpaces.forEach { destinationSpace ->
                                DropdownMenuItem(
                                    text = { Text(destinationSpace.title) },
                                    onClick = {
                                        isMoveMenuExpanded = false
                                        onMoveLink(link.id, destinationSpace.id)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Current space",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { onDeleteLink(link.id) }) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun LinkCardPreview(
    previewImageUrl: String?,
    title: String,
    isPendingMetadata: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center
    ) {
        if (!previewImageUrl.isNullOrBlank()) {
            LinkPreviewImage(
                imageUrl = previewImageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isPendingMetadata) {
            Text(
                text = "Parsing preview...",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun compactUrlLabel(url: String): String {
    val withoutScheme = url
        .removePrefix("https://")
        .removePrefix("http://")
        .trim()

    val host = withoutScheme.substringBefore('/')
    val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        .trim('/')

    return if (path.isBlank()) {
        host
    } else {
        "$host/${path.take(32)}"
    }
}
