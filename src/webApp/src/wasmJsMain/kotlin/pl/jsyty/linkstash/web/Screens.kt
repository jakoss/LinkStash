package pl.jsyty.linkstash.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.image.LandscapistImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

@Composable
fun LoginScreen(
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

@Composable
fun WorkspaceScreen(
    user: UserDto?,
    spaces: List<SpaceDto>,
    selectedSpaceId: String?,
    renameSpaceTitle: String,
    onRenameSpaceTitleChange: (String) -> Unit,
    newSpaceTitle: String,
    onNewSpaceTitleChange: (String) -> Unit,
    archiveSpaceTitle: String,
    onArchiveSpaceTitleChange: (String) -> Unit,
    pendingUrl: String,
    onPendingUrlChange: (String) -> Unit,
    links: List<LinkDto>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onExport: () -> Unit,
    onSelectSpace: (SpaceDto) -> Unit,
    onCreateSpace: () -> Unit,
    onRenameSpace: () -> Unit,
    onArchiveSpace: () -> Unit,
    onDeleteSpace: () -> Unit,
    onSaveLink: (String) -> Unit,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit
) {
    val selectedSpace = spaces.firstOrNull { it.id == selectedSpaceId }
    val displayName = user?.displayName ?: user?.id.orEmpty()
    val prefersManualPasteFlow = remember { shouldUseManualPasteFlow() }
    val selectedTabIndex = spaces.indexOfFirst { it.id == selectedSpaceId }
        .takeIf { it >= 0 }
        ?: 0
    var isActionsMenuExpanded by remember { mutableStateOf(false) }
    var isSaveDialogVisible by remember { mutableStateOf(false) }
    var isManageSpacesDialogVisible by remember { mutableStateOf(false) }
    val saveUrlFocusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isSaveDialogVisible) {
        if (isSaveDialogVisible) {
            if (prefersManualPasteFlow) {
                delay(180)
            }
            saveUrlFocusRequester.requestFocus()
        }
    }

    DisposableEffect(selectedSpaceId, isBusy, isSaveDialogVisible) {
        val disposeListener = registerGlobalUrlPasteListener { pastedUrl ->
            if (isBusy) {
                return@registerGlobalUrlPasteListener
            }

            onPendingUrlChange(pastedUrl)
            if (selectedSpaceId != null && !isSaveDialogVisible) {
                onSaveLink(pastedUrl)
            } else {
                isSaveDialogVisible = true
            }
        }

        onDispose(disposeListener)
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isBusy) {
                        return@ExtendedFloatingActionButton
                    }
                    if (prefersManualPasteFlow) {
                        isSaveDialogVisible = true
                        return@ExtendedFloatingActionButton
                    }
                    scope.launch {
                        val clipboardUrl = readClipboardTextOrNull()
                            ?.takeIf { it.isHttpUrl() }
                            ?.trim()

                        if (selectedSpaceId != null && clipboardUrl != null) {
                            onPendingUrlChange(clipboardUrl)
                            onSaveLink(clipboardUrl)
                        } else {
                            isSaveDialogVisible = true
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text("Paste URL")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LinkStash",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (displayName.isNotBlank()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(onClick = onRefresh, enabled = !isBusy) {
                        Text("Refresh")
                    }
                    FilledTonalButton(onClick = onExport, enabled = !isBusy) {
                        Text("Copy links")
                    }
                    Box {
                        TextButton(
                            onClick = { isActionsMenuExpanded = true },
                            enabled = !isBusy
                        ) {
                            Text("More")
                        }

                        DropdownMenu(
                            expanded = isActionsMenuExpanded,
                            onDismissRequest = { isActionsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Manage spaces") },
                                onClick = {
                                    isActionsMenuExpanded = false
                                    isManageSpacesDialogVisible = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Log out") },
                                onClick = {
                                    isActionsMenuExpanded = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            }

            if (spaces.isNotEmpty()) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    spaces.forEachIndexed { index, space ->
                        Tab(
                            selected = index == selectedTabIndex,
                            onClick = { onSelectSpace(space) },
                            enabled = !isBusy,
                            text = {
                                Text(
                                    text = space.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedSpace?.title ?: "Links",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${links.size} links",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (links.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Nothing here yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use the floating button to paste a URL into ${selectedSpace?.title ?: "this space"}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(links, key = { it.id }) { link ->
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
    }

    if (isSaveDialogVisible) {
        AlertDialog(
            onDismissRequest = { isSaveDialogVisible = false },
            title = { Text("Paste URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Links save to ${selectedSpace?.title ?: "the selected space"} by default.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pendingUrl,
                        onValueChange = onPendingUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(saveUrlFocusRequester),
                        enabled = !isBusy && selectedSpaceId != null,
                        label = { Text("URL") },
                        placeholder = { Text("https://example.com/article") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            isSaveDialogVisible = false
                            onSaveLink(pendingUrl)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy && selectedSpaceId != null && pendingUrl.isNotBlank()
                    ) {
                        Text("Save link")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isSaveDialogVisible = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (isManageSpacesDialogVisible) {
        AlertDialog(
            onDismissRequest = { isManageSpacesDialogVisible = false },
            title = { Text("Manage spaces") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = newSpaceTitle,
                        onValueChange = onNewSpaceTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                        label = { Text("Create space") },
                        placeholder = { Text("Reading queue") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            isManageSpacesDialogVisible = false
                            onCreateSpace()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy && newSpaceTitle.isNotBlank()
                    ) {
                        Text("Add space")
                    }

                    if (selectedSpaceId != null) {
                        OutlinedTextField(
                            value = renameSpaceTitle,
                            onValueChange = onRenameSpaceTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            label = { Text("Rename selected space") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                isManageSpacesDialogVisible = false
                                onRenameSpace()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy && renameSpaceTitle.isNotBlank()
                        ) {
                            Text("Rename")
                        }
                        OutlinedTextField(
                            value = archiveSpaceTitle,
                            onValueChange = onArchiveSpaceTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                            label = { Text("Archive into new space") },
                            placeholder = { Text("${selectedSpace?.title.orEmpty()} Archive") },
                            singleLine = true
                        )
                        Text(
                            text = "Creates a new space and moves all ${links.size} links from ${selectedSpace?.title ?: "the selected space"} into it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                isManageSpacesDialogVisible = false
                                onArchiveSpace()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy && archiveSpaceTitle.isNotBlank()
                        ) {
                            Text("Archive Space")
                        }
                        TextButton(
                            onClick = {
                                isManageSpacesDialogVisible = false
                                onDeleteSpace()
                            },
                            enabled = !isBusy
                        ) {
                            Text("Delete selected space")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isManageSpacesDialogVisible = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
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
    val compactUrlLabel = remember(link.url) { compactUrlLabel(link.url) }
    val displayTitle = remember(link.title, link.url, compactUrlLabel) {
        link.title
            ?.takeIf { it.isNotBlank() && it != link.url }
            ?: compactUrlLabel
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinkCardPreview(
                previewImageUrl = link.previewImageUrl,
                title = displayTitle,
                compactUrlLabel = compactUrlLabel
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri(link.url) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
                if (!link.createdAt.isNullOrBlank()) {
                    Text(
                        text = compactCreatedAt(link.createdAt.orEmpty()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { uriHandler.openUri(link.url) }) {
                    Text("Open")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (moveTargets.isNotEmpty()) {
                        Box {
                            FilledTonalButton(
                                onClick = { isMenuExpanded = true },
                                enabled = !isBusy
                            ) {
                                Text("Move")
                            }
                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                moveTargets.forEach { space ->
                                    DropdownMenuItem(
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
}

@Composable
private fun LinkCardPreview(
    previewImageUrl: String?,
    title: String,
    compactUrlLabel: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        if (!previewImageUrl.isNullOrBlank()) {
            LandscapistImage(
                imageModel = { previewImageUrl },
                modifier = Modifier.fillMaxSize(),
                component = rememberImageComponent {
                    +CrossfadePlugin()
                },
                imageOptions = ImageOptions(
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    placeholderAspectRatio = 16f / 9f
                )
            )
        } else {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "No preview",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = compactUrlLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
