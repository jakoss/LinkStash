package pl.jsyty.linkstash

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import pl.jsyty.linkstash.linkstash.LinkStashApp
import pl.jsyty.linkstash.linkstash.LinkStashViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: LinkStashViewModel by viewModels {
        LinkStashAndroidViewModelFactory(applicationContext)
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
            MaterialTheme {
                LinkStashApp(viewModel = viewModel)
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

private class LinkStashAndroidViewModelFactory(
    context: Context
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LinkStashViewModel(
            repository = LinkStashAndroidRepositoryFactory.create(appContext),
            defaultSpaceTitle = AndroidAppConfig.defaultSpaceTitle
        ) as T
    }
}
