package pl.jsyty.linkstash.server.auth

import java.time.Clock
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpacesListResponse
import pl.jsyty.linkstash.server.config.AppConfig

data class LinkStashBootstrapResult(
    val rootCollectionId: String,
    val defaultSpaceCollectionId: String,
    val rootCollectionTitle: String,
    val defaultSpaceTitle: String
)

class LinkStashBootstrapService(
    private val config: AppConfig,
    private val linkStashConfigRepository: LinkStashConfigRepository,
    private val raindropClient: RaindropClient,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun ensureBootstrap(userId: String, accessToken: String): LinkStashBootstrapResult {
        val existingConfig = linkStashConfigRepository.findByUserId(userId)
        val rootTitle = existingConfig?.rootCollectionTitle
            ?.takeIf { it.isNotBlank() }
            ?: config.linkstashRootCollectionTitle
        val defaultSpaceTitle = existingConfig?.defaultSpaceTitle
            ?.takeIf { it.isNotBlank() }
            ?: config.linkstashDefaultSpaceTitle

        val rootCollection = resolveRootCollection(
            accessToken = accessToken,
            persistedRootCollectionId = existingConfig?.rootCollectionId,
            expectedRootTitle = rootTitle
        )

        val defaultSpaceCollection = resolveDefaultSpaceCollection(
            accessToken = accessToken,
            rootCollectionId = rootCollection.id,
            persistedDefaultSpaceCollectionId = existingConfig?.defaultSpaceCollectionId,
            expectedDefaultSpaceTitle = defaultSpaceTitle
        )

        val now = clock.instant().epochSecond
        linkStashConfigRepository.upsert(
            userId = userId,
            rootCollectionId = rootCollection.id,
            defaultSpaceCollectionId = defaultSpaceCollection.id,
            rootCollectionTitle = rootTitle,
            defaultSpaceTitle = defaultSpaceTitle,
            nowEpochSeconds = now
        )

        return LinkStashBootstrapResult(
            rootCollectionId = rootCollection.id,
            defaultSpaceCollectionId = defaultSpaceCollection.id,
            rootCollectionTitle = rootTitle,
            defaultSpaceTitle = defaultSpaceTitle
        )
    }

    suspend fun listSpaces(userId: String, accessToken: String): SpacesListResponse {
        val bootstrapResult = ensureBootstrap(userId = userId, accessToken = accessToken)
        val childCollections = raindropClient.listChildCollections(
            accessToken = accessToken,
            parentCollectionId = bootstrapResult.rootCollectionId
        )

        val spaces = childCollections
            .mapNotNull { collection ->
                val id = collection.stableId() ?: return@mapNotNull null
                val title = collection.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SpaceDto(id = id, title = title)
            }
            .distinctBy { it.id }
            .sortedBy { it.title.lowercase() }
            .toMutableList()

        if (spaces.none { it.id == bootstrapResult.defaultSpaceCollectionId }) {
            val ensuredDefaultSpace = resolveDefaultSpaceCollection(
                accessToken = accessToken,
                rootCollectionId = bootstrapResult.rootCollectionId,
                persistedDefaultSpaceCollectionId = bootstrapResult.defaultSpaceCollectionId,
                expectedDefaultSpaceTitle = bootstrapResult.defaultSpaceTitle
            )

            linkStashConfigRepository.upsert(
                userId = userId,
                rootCollectionId = bootstrapResult.rootCollectionId,
                defaultSpaceCollectionId = ensuredDefaultSpace.id,
                rootCollectionTitle = bootstrapResult.rootCollectionTitle,
                defaultSpaceTitle = bootstrapResult.defaultSpaceTitle,
                nowEpochSeconds = clock.instant().epochSecond
            )

            spaces += SpaceDto(
                id = ensuredDefaultSpace.id,
                title = ensuredDefaultSpace.title
            )
        }

        return SpacesListResponse(
            spaces = spaces
                .distinctBy { it.id }
                .sortedBy { it.title.lowercase() }
        )
    }

    private suspend fun resolveRootCollection(
        accessToken: String,
        persistedRootCollectionId: String?,
        expectedRootTitle: String
    ): BootstrapCollection {
        persistedRootCollectionId
            ?.takeIf { it.isNotBlank() }
            ?.let { collectionId ->
                val persistedCollection = raindropClient.getCollectionById(accessToken, collectionId)
                if (persistedCollection != null && persistedCollection.parentCollectionId() == null) {
                    return BootstrapCollection(
                        id = collectionId,
                        title = persistedCollection.title?.takeIf { it.isNotBlank() } ?: expectedRootTitle
                    )
                }
            }

        val matchingRoots = raindropClient.listCollections(accessToken)
            .filter { collection ->
                collection.parentCollectionId() == null &&
                    collection.title?.trim() == expectedRootTitle
            }

        if (matchingRoots.size == 1) {
            return matchingRoots.first().toBootstrapCollection(
                fallbackTitle = expectedRootTitle,
                context = "resolved root collection"
            )
        }

        return raindropClient.createCollection(
            accessToken = accessToken,
            title = expectedRootTitle,
            parentCollectionId = null
        ).toBootstrapCollection(
            fallbackTitle = expectedRootTitle,
            context = "created root collection"
        )
    }

    private suspend fun resolveDefaultSpaceCollection(
        accessToken: String,
        rootCollectionId: String,
        persistedDefaultSpaceCollectionId: String?,
        expectedDefaultSpaceTitle: String
    ): BootstrapCollection {
        persistedDefaultSpaceCollectionId
            ?.takeIf { it.isNotBlank() }
            ?.let { collectionId ->
                val persistedCollection = raindropClient.getCollectionById(accessToken, collectionId)
                if (persistedCollection != null && persistedCollection.parentCollectionId() == rootCollectionId) {
                    return BootstrapCollection(
                        id = collectionId,
                        title = persistedCollection.title?.takeIf { it.isNotBlank() } ?: expectedDefaultSpaceTitle
                    )
                }
            }

        val matchingDefaults = raindropClient.listChildCollections(accessToken, rootCollectionId)
            .filter { collection -> collection.title?.trim() == expectedDefaultSpaceTitle }

        if (matchingDefaults.isNotEmpty()) {
            return matchingDefaults.first().toBootstrapCollection(
                fallbackTitle = expectedDefaultSpaceTitle,
                context = "resolved default space collection"
            )
        }

        return raindropClient.createCollection(
            accessToken = accessToken,
            title = expectedDefaultSpaceTitle,
            parentCollectionId = rootCollectionId
        ).toBootstrapCollection(
            fallbackTitle = expectedDefaultSpaceTitle,
            context = "created default space collection"
        )
    }

    private fun RaindropCollectionPayload.toBootstrapCollection(
        fallbackTitle: String,
        context: String
    ): BootstrapCollection {
        val collectionId = stableId()
            ?: throw RaindropUpstreamException("Raindrop $context returned a collection without id")
        val title = title?.takeIf { it.isNotBlank() } ?: fallbackTitle
        return BootstrapCollection(id = collectionId, title = title)
    }

    private data class BootstrapCollection(
        val id: String,
        val title: String
    )
}
