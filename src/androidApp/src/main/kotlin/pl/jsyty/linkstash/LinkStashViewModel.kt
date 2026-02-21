package pl.jsyty.linkstash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import pl.jsyty.linkstash.linkstash.LinkStashController
import pl.jsyty.linkstash.linkstash.LinkStashRepository
import pl.jsyty.linkstash.linkstash.LinkStashUiState

class LinkStashViewModel(
    repository: LinkStashRepository
) : ViewModel() {

    private val controller = LinkStashController(
        repository = repository,
        defaultSpaceTitle = AndroidAppConfig.defaultSpaceTitle,
        scope = viewModelScope
    )

    val uiState: StateFlow<LinkStashUiState> = controller.uiState

    init {
        controller.initialize()
    }

    fun useBearerToken(rawToken: String) = controller.useBearerToken(rawToken)

    fun onSharedUrlReceived(rawUrl: String) = controller.onSharedUrlReceived(rawUrl)

    fun saveManualUrl(rawUrl: String) = controller.onSharedUrlReceived(rawUrl)

    fun refresh() = controller.refresh()

    fun onNetworkAvailable() = controller.onNetworkAvailable()

    fun syncPendingQueue() = controller.syncPendingQueue()

    fun selectSpace(spaceId: String) = controller.selectSpace(spaceId)

    fun loadMoreLinks() = controller.loadMoreLinks()

    fun moveLink(linkId: String, targetSpaceId: String) = controller.moveLink(linkId, targetSpaceId)

    fun deleteLink(linkId: String) = controller.deleteLink(linkId)

    fun logout() = controller.logout()

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LinkStashViewModel(
                        repository = LinkStashAndroidRepositoryFactory.create(appContext)
                    ) as T
                }
            }
        }
    }
}
