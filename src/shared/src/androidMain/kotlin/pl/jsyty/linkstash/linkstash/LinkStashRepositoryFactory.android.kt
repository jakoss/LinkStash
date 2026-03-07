package pl.jsyty.linkstash.linkstash

import android.content.Context

fun createLinkStashRepository(
    context: Context,
    pendingQueueStore: LinkStashPendingQueueStore,
    config: LinkStashClientConfig
): LinkStashRepository {
    return createLinkStashRepository(
        sessionStore = createLinkStashSessionStore(context),
        pendingQueueStore = pendingQueueStore,
        config = config
    )
}
