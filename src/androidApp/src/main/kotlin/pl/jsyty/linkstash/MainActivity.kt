package pl.jsyty.linkstash

import android.content.Context
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
    private var hasStartedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
        if (hasStartedOnce) {
            viewModel.refreshIfAuthenticated()
        }
        hasStartedOnce = true
    }

    override fun onStop() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
        super.onStop()
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
