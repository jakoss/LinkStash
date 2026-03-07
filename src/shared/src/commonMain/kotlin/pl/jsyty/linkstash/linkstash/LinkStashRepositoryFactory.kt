package pl.jsyty.linkstash.linkstash

fun createLinkStashRepository(
    sessionStore: LinkStashSessionStore,
    pendingQueueStore: LinkStashPendingQueueStore,
    config: LinkStashClientConfig
): LinkStashRepository {
    return LinkStashRepository(
        sessionStore = sessionStore,
        pendingQueueStore = pendingQueueStore,
        config = config
    )
}
