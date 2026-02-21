package pl.jsyty.linkstash.server.auth

import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest

class DomainValidationException(message: String) : RuntimeException(message)

class DomainNotFoundException(message: String) : RuntimeException(message)

class LinkStashDomainService(
    private val linkStashBootstrapService: LinkStashBootstrapService,
    private val raindropClient: RaindropClient
) {
    suspend fun createSpace(
        userId: String,
        accessToken: String,
        request: SpaceCreateRequest
    ): SpaceDto {
        val title = request.title.trim()
        if (title.isBlank()) throw DomainValidationException("Space title is required")

        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        val createdSpace = raindropClient.createCollection(
            accessToken = accessToken,
            title = title,
            parentCollectionId = bootstrapResult.rootCollectionId
        )

        return createdSpace.toSpaceDto()
    }

    suspend fun renameSpace(
        userId: String,
        accessToken: String,
        spaceId: String,
        request: SpaceRenameRequest
    ): SpaceDto {
        val normalizedSpaceId = spaceId.trim()
        val title = request.title.trim()
        if (title.isBlank()) throw DomainValidationException("Space title is required")

        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireSpaceBelongsToRoot(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            spaceId = normalizedSpaceId
        )

        val renamedSpace = raindropClient.updateCollection(
            accessToken = accessToken,
            collectionId = normalizedSpaceId,
            title = title
        )

        return renamedSpace.toSpaceDto()
    }

    suspend fun deleteSpace(
        userId: String,
        accessToken: String,
        spaceId: String
    ) {
        val normalizedSpaceId = spaceId.trim()
        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireSpaceBelongsToRoot(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            spaceId = normalizedSpaceId
        )

        val wasDeleted = raindropClient.deleteCollection(
            accessToken = accessToken,
            collectionId = normalizedSpaceId
        )

        if (!wasDeleted) {
            throw DomainNotFoundException("Space not found")
        }
    }

    suspend fun listLinks(
        userId: String,
        accessToken: String,
        spaceId: String,
        cursor: String?
    ): LinksListResponse {
        val normalizedSpaceId = spaceId.trim()
        val page = parseCursor(cursor)
        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireSpaceBelongsToRoot(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            spaceId = normalizedSpaceId
        )

        val raindrops = raindropClient.listRaindrops(
            accessToken = accessToken,
            collectionId = normalizedSpaceId,
            page = page,
            perPage = LINKS_PAGE_SIZE
        )

        return LinksListResponse(
            links = raindrops.map { it.toLinkDto(fallbackSpaceId = normalizedSpaceId) },
            nextCursor = if (raindrops.size < LINKS_PAGE_SIZE) null else (page + 1).toString()
        )
    }

    suspend fun createLink(
        userId: String,
        accessToken: String,
        spaceId: String,
        request: LinkCreateRequest
    ): LinkDto {
        val normalizedSpaceId = spaceId.trim()
        val url = request.url.trim()
        if (url.isBlank()) throw DomainValidationException("Link url is required")

        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireSpaceBelongsToRoot(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            spaceId = normalizedSpaceId
        )

        val createdLink = raindropClient.createRaindrop(
            accessToken = accessToken,
            collectionId = normalizedSpaceId,
            url = url
        )

        return createdLink.toLinkDto(fallbackSpaceId = normalizedSpaceId)
    }

    suspend fun moveLink(
        userId: String,
        accessToken: String,
        linkId: String,
        request: LinkMoveRequest
    ): LinkDto {
        val normalizedLinkId = linkId.trim()
        val targetSpaceId = request.spaceId.trim()
        if (targetSpaceId.isBlank()) throw DomainValidationException("Target spaceId is required")

        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireSpaceBelongsToRoot(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            spaceId = targetSpaceId
        )

        requireLinkBelongsToRootSubtree(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            linkId = normalizedLinkId
        )

        val movedLink = raindropClient.moveRaindropToCollection(
            accessToken = accessToken,
            raindropId = normalizedLinkId,
            collectionId = targetSpaceId
        )

        return movedLink.toLinkDto(fallbackSpaceId = targetSpaceId)
    }

    suspend fun deleteLink(
        userId: String,
        accessToken: String,
        linkId: String
    ) {
        val normalizedLinkId = linkId.trim()
        val bootstrapResult = linkStashBootstrapService.ensureBootstrap(userId, accessToken)
        requireLinkBelongsToRootSubtree(
            accessToken = accessToken,
            rootCollectionId = bootstrapResult.rootCollectionId,
            linkId = normalizedLinkId
        )

        val wasDeleted = raindropClient.deleteRaindrop(
            accessToken = accessToken,
            raindropId = normalizedLinkId
        )

        if (!wasDeleted) {
            throw DomainNotFoundException("Link not found")
        }
    }

    private suspend fun requireSpaceBelongsToRoot(
        accessToken: String,
        rootCollectionId: String,
        spaceId: String
    ): RaindropCollectionPayload {
        val normalizedSpaceId = spaceId.trim()
        if (normalizedSpaceId.isBlank()) throw DomainValidationException("spaceId is required")

        val space = raindropClient.getCollectionById(accessToken, normalizedSpaceId)
            ?: throw DomainNotFoundException("Space not found")

        if (space.parentCollectionId() != rootCollectionId) {
            throw DomainNotFoundException("Space not found")
        }

        return space
    }

    private suspend fun requireLinkBelongsToRootSubtree(
        accessToken: String,
        rootCollectionId: String,
        linkId: String
    ): RaindropRaindropPayload {
        val normalizedLinkId = linkId.trim()
        if (normalizedLinkId.isBlank()) throw DomainValidationException("linkId is required")

        val link = raindropClient.getRaindropById(accessToken, normalizedLinkId)
            ?: throw DomainNotFoundException("Link not found")

        val collectionId = link.stableCollectionId()
            ?: throw RaindropUpstreamException("Raindrop link response missing collection id")

        if (!isCollectionInRootSubtree(accessToken, rootCollectionId, collectionId)) {
            throw DomainNotFoundException("Link not found")
        }

        return link
    }

    private suspend fun isCollectionInRootSubtree(
        accessToken: String,
        rootCollectionId: String,
        collectionId: String
    ): Boolean {
        var currentCollectionId = collectionId
        val visitedCollectionIds = mutableSetOf<String>()

        while (visitedCollectionIds.add(currentCollectionId)) {
            if (currentCollectionId == rootCollectionId) {
                return true
            }

            val collection = raindropClient.getCollectionById(accessToken, currentCollectionId)
                ?: return false
            val parentCollectionId = collection.parentCollectionId() ?: return false
            currentCollectionId = parentCollectionId
        }

        return false
    }

    private fun parseCursor(cursor: String?): Int {
        val rawCursor = cursor?.trim()
        if (rawCursor.isNullOrBlank()) return 0

        val page = rawCursor.toIntOrNull()
            ?: throw DomainValidationException("Invalid cursor")

        if (page < 0) {
            throw DomainValidationException("Invalid cursor")
        }

        return page
    }

    private fun RaindropCollectionPayload.toSpaceDto(): SpaceDto {
        val collectionId = stableId()
            ?: throw RaindropUpstreamException("Raindrop collection payload is missing id")
        val collectionTitle = title?.trim()?.takeIf { it.isNotBlank() }
            ?: throw RaindropUpstreamException("Raindrop collection payload is missing title")

        return SpaceDto(
            id = collectionId,
            title = collectionTitle
        )
    }

    private fun RaindropRaindropPayload.toLinkDto(fallbackSpaceId: String? = null): LinkDto {
        val raindropId = stableId()
            ?: throw RaindropUpstreamException("Raindrop link payload is missing id")
        val raindropUrl = link?.trim()?.takeIf { it.isNotBlank() }
            ?: throw RaindropUpstreamException("Raindrop link payload is missing url")
        val raindropSpaceId = stableCollectionId()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackSpaceId
            ?: throw RaindropUpstreamException("Raindrop link payload is missing collection id")

        return LinkDto(
            id = raindropId,
            url = raindropUrl,
            title = title?.trim()?.takeIf { it.isNotBlank() },
            excerpt = excerpt?.trim()?.takeIf { it.isNotBlank() },
            createdAt = created,
            spaceId = raindropSpaceId
        )
    }

    private companion object {
        const val LINKS_PAGE_SIZE = 50
    }
}
