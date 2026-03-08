package pl.jsyty.linkstash

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
    private var lastHandledSharedPayload: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lastHandledSharedPayload = savedInstanceState?.getString(LAST_HANDLED_SHARED_PAYLOAD_KEY)

        setContent {
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(this)
                } else {
                    dynamicLightColorScheme(this)
                }
            } else if (isSystemInDarkTheme()) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(LAST_HANDLED_SHARED_PAYLOAD_KEY, lastHandledSharedPayload)
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
        val sharedUrls = extractSharedUrls(intent)
        if (sharedUrls.isEmpty()) return

        val payloadSignature = sharedUrls.joinToString(separator = "\n")
        if (payloadSignature == lastHandledSharedPayload) {
            return
        }

        lastHandledSharedPayload = payloadSignature
        sharedUrls.forEach(viewModel::onSharedUrlReceived)
    }

    private fun extractSharedUrls(intent: Intent): List<String> {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val rawCandidates = buildList {
            intent.dataString?.let(::add)
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::add)
            intent.getStringExtra(Intent.EXTRA_HTML_TEXT)?.let(::add)
            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.let(::add)

            intent.clipData?.let { clipData ->
                repeat(clipData.itemCount) { index ->
                    val item = clipData.getItemAt(index)
                    item.uri?.toString()?.let(::add)
                    item.coerceToText(this@MainActivity)?.toString()?.let(::add)
                }
            }

            intent.extras?.keySet().orEmpty().forEach { key ->
                val value = intent.extras?.get(key)
                when (value) {
                    is String -> add(value)
                    is CharSequence -> add(value.toString())
                    is ArrayList<*> -> value.filterIsInstance<CharSequence>().mapTo(this) { it.toString() }
                }
            }
        }

        return rawCandidates
            .flatMap(::extractHttpUrls)
            .distinct()
    }

    private fun extractHttpUrls(raw: String): List<String> {
        return HTTP_URL_REGEX.findAll(raw)
            .map { match -> match.value.trimEnd { it in TRAILING_URL_PUNCTUATION } }
            .filter { it.startsWith(prefix = "http://", ignoreCase = true) || it.startsWith(prefix = "https://", ignoreCase = true) }
            .toList()
    }

    private companion object {
        const val LAST_HANDLED_SHARED_PAYLOAD_KEY = "lastHandledSharedPayload"
        val HTTP_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
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
