package pl.jsyty.linkstash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import linkstash.shared.generated.resources.Res
import linkstash.shared.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource

private data object RouteA
private data class RouteB(val id: String)

@Composable
@Preview
fun App() {
    MaterialTheme {
        Scaffold { paddingValues ->
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                val backStack = remember { mutableStateListOf<Any>(RouteA) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is RouteA -> NavEntry(key) {
                                Column(Modifier.fillMaxSize()){
                                    Text("This is Route A", Modifier.align(Alignment.CenterHorizontally))
                                    Button(
                                        onClick = { backStack.add(RouteB("42")) },
                                        Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text("Go to Route B")
                                    }
                                }
                            }

                            is RouteB -> NavEntry(key) {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .safeContentPadding()
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Text(
                                        "This is Route B with id = ${key.id}",
                                        Modifier.align(Alignment.CenterHorizontally)
                                    )
                                    Image(
                                        painter = painterResource(Res.drawable.compose_multiplatform),
                                        contentDescription = "Compose Logo",
                                        modifier = Modifier
                                            .align(Alignment.CenterHorizontally)
                                            .fillMaxWidth()
                                    )
                                }
                            }

                            else -> {
                                error("Unknown route: $key")
                            }
                        }
                    }
                )
            }
        }
    }
}