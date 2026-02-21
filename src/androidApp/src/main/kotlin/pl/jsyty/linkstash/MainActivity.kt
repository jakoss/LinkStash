package pl.jsyty.linkstash

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.linkstash.LinkStashUiState

class MainActivity : ComponentActivity() {
    private val viewModel: LinkStashViewModel by viewModels {
        LinkStashViewModel.factory(applicationContext)
    }
    private val connectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModel.onNetworkAvailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MaterialTheme {
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
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onStop() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        super.onStop()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val sharedUrl = extractFirstHttpUrl(sharedText)
            if (sharedUrl != null) {
                viewModel.onSharedUrlReceived(sharedUrl)
            }
        }
    }

    private fun extractFirstHttpUrl(raw: String): String? {
        return raw.split("\n", "\t", " ")
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("http://") || it.startsWith("https://")
            }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var manualUrl by remember { mutableStateOf("") }
    var manualBearerToken by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("LinkStash")
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!uiState.isAuthenticated) {
                    Text("Paste your Raindrop API token to continue")
                } else {
                    Button(onClick = onRefresh) {
                        Text("Refresh")
                    }
                    Button(onClick = onSync) {
                        Text("Sync Queue")
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            if (!uiState.isAuthenticated) {
                OutlinedTextField(
                    value = manualBearerToken,
                    onValueChange = { manualBearerToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Raindrop Token")
                    },
                    singleLine = true
                )
                Button(
                    onClick = {
                        onUseBearerToken(manualBearerToken)
                        manualBearerToken = ""
                    },
                    enabled = manualBearerToken.isNotBlank()
                ) {
                    Text("Use Token")
                }
            }

            Text(
                text = "Pending queue: ${uiState.pendingQueueCount}",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = manualUrl,
                onValueChange = { manualUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Paste URL")
                },
                singleLine = true
            )
            Button(
                onClick = {
                    onSaveManualUrl(manualUrl)
                    manualUrl = ""
                },
                enabled = manualUrl.isNotBlank()
            ) {
                Text("Save")
            }

            if (uiState.isAuthenticated) {
                if (uiState.spaces.isEmpty()) {
                    Text("No spaces available")
                } else {
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

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.links, key = { it.id }) { link ->
                        LinkCard(
                            link = link,
                            spaces = uiState.spaces,
                            onMoveLink = onMoveLink,
                            onDeleteLink = onDeleteLink
                        )
                    }

                    if (uiState.nextCursor != null) {
                        item {
                            Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.selectedSpaceId) {
        if (uiState.selectedSpaceId == null && uiState.spaces.isNotEmpty()) {
            onSpaceSelected(uiState.spaces.first().id)
        }
    }
}

@Composable
private fun LinkCard(
    link: LinkDto,
    spaces: List<SpaceDto>,
    onMoveLink: (String, String) -> Unit,
    onDeleteLink: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = link.title ?: link.url,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = link.url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val destinationSpaces = spaces.filter { it.id != link.spaceId }
            if (destinationSpaces.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    destinationSpaces.forEach { destinationSpace ->
                        TextButton(onClick = { onMoveLink(link.id, destinationSpace.id) }) {
                            Text("Move to ${destinationSpace.title}")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onDeleteLink(link.id) }) {
                    Text("Delete")
                }
            }
        }
    }
}
