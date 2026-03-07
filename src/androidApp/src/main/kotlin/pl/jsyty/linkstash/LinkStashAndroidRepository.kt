package pl.jsyty.linkstash

import android.content.Context
import pl.jsyty.linkstash.linkstash.LinkStashClientConfig
import pl.jsyty.linkstash.linkstash.LinkStashRepository
import pl.jsyty.linkstash.linkstash.createPendingLinkQueueStore
import pl.jsyty.linkstash.linkstash.createLinkStashRepository

object AndroidAppConfig {
    const val apiBaseUrl = "http://10.0.2.2:8080/v1/"
    const val defaultSpaceTitle = "Inbox"
}

object LinkStashAndroidRepositoryFactory {
    fun create(context: Context): LinkStashRepository {
        val appContext = context.applicationContext
        return createLinkStashRepository(
            context = appContext,
            pendingQueueStore = createPendingLinkQueueStore(appContext),
            config = LinkStashClientConfig(
                apiBaseUrl = AndroidAppConfig.apiBaseUrl,
                defaultSpaceTitle = AndroidAppConfig.defaultSpaceTitle
            )
        )
    }
}
