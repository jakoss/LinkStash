package pl.jsyty.linkstash.web

import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

internal data class WebWorkspaceState(
    val user: UserDto? = null,
    val spaces: List<SpaceDto> = emptyList(),
    val selectedSpaceId: String? = null,
    val linksBySpaceId: Map<String, List<WebLinkItem>> = emptyMap(),
    val operations: WebWorkspaceOperations = WebWorkspaceOperations()
)

internal data class WebWorkspaceOperations(
    val initialLoad: Boolean = false,
    val sessionRestore: Boolean = false,
    val refreshing: Boolean = false,
    val savingLink: Boolean = false,
    val movingLinkIds: Set<String> = emptySet(),
    val deletingLinkIds: Set<String> = emptySet(),
    val loadingSpaceIds: Set<String> = emptySet(),
    val creatingSpace: Boolean = false,
    val renamingSpaceId: String? = null,
    val archivingSpaceId: String? = null,
    val deletingSpaceId: String? = null,
    val exporting: Boolean = false,
    val loggingOut: Boolean = false
)

internal data class WebLinkItem(
    val key: String,
    val link: LinkDto,
    val syncState: WebLinkSyncState = WebLinkSyncState.Synced,
    val failureMessage: String? = null,
    val retryUrl: String? = null
)

internal enum class WebLinkSyncState {
    Synced,
    Saving,
    Failed
}

internal data class WebMetadataPollingTarget(
    val spaceId: String,
    val attempts: Int = 0
)

@Serializable
internal data class WebWorkspaceCache(
    val apiBaseUrl: String,
    val user: UserDto? = null,
    val spaces: List<SpaceDto> = emptyList(),
    val selectedSpaceId: String? = null,
    val linksBySpaceId: Map<String, List<LinkDto>> = emptyMap()
)

internal fun LinkDto.toWebLinkItem(): WebLinkItem {
    return WebLinkItem(
        key = id,
        link = this
    )
}

internal fun WebWorkspaceState.visibleLinks(): List<WebLinkItem> {
    return selectedSpaceId?.let { linksBySpaceId[it].orEmpty() }.orEmpty()
}

internal fun WebWorkspaceState.selectedSpace(): SpaceDto? {
    return spaces.firstOrNull { it.id == selectedSpaceId }
}

internal fun WebWorkspaceState.hasVisibleWorkspaceContent(): Boolean {
    return user != null || spaces.isNotEmpty() || linksBySpaceId.isNotEmpty()
}

internal fun List<SpaceDto>.sortedByTitle(): List<SpaceDto> {
    return sortedBy { it.title.lowercase() }
}

internal fun resolveSelectedSpaceId(
    spaces: List<SpaceDto>,
    preferredSpaceId: String?
): String? {
    return preferredSpaceId
        ?.takeIf { preferredId -> spaces.any { it.id == preferredId } }
        ?: spaces.firstOrNull()?.id
}

internal fun mergeRemoteLinks(
    existing: List<WebLinkItem>,
    remoteLinks: List<LinkDto>
): List<WebLinkItem> {
    val failedItems = existing.filter { it.syncState == WebLinkSyncState.Failed }
    val savingItems = existing.filter { item ->
        item.syncState == WebLinkSyncState.Saving &&
            remoteLinks.none { remoteLink -> remoteLink.url == item.link.url }
    }

    return failedItems + savingItems + remoteLinks.map(LinkDto::toWebLinkItem)
}

internal fun resolveMetadataPollingTargets(
    currentTargets: Map<String, WebMetadataPollingTarget>,
    linksBySpaceId: Map<String, List<WebLinkItem>>
): Map<String, WebMetadataPollingTarget> {
    return buildMap {
        currentTargets.forEach { (linkId, target) ->
            val matchingItem = linksBySpaceId[target.spaceId]
                ?.firstOrNull { it.link.id == linkId && it.syncState == WebLinkSyncState.Synced }

            if (matchingItem != null && shouldPollMetadata(matchingItem.link)) {
                put(linkId, target)
            }
        }
    }
}

internal fun shouldPollMetadata(link: LinkDto): Boolean {
    return link.previewImageUrl.isNullOrBlank() &&
        link.excerpt.isNullOrBlank() &&
        (link.title.isNullOrBlank() || link.title == link.url)
}

internal fun WebWorkspaceState.toCache(apiBaseUrl: String): WebWorkspaceCache? {
    val cachedLinks = linksBySpaceId
        .mapValues { (_, items) ->
            items.filter { it.syncState == WebLinkSyncState.Synced }.map(WebLinkItem::link)
        }
        .filterValues { it.isNotEmpty() }

    if (user == null && spaces.isEmpty() && cachedLinks.isEmpty()) {
        return null
    }

    return WebWorkspaceCache(
        apiBaseUrl = apiBaseUrl,
        user = user,
        spaces = spaces,
        selectedSpaceId = selectedSpaceId,
        linksBySpaceId = cachedLinks
    )
}

internal fun WebWorkspaceCache.toWorkspaceState(): WebWorkspaceState {
    return WebWorkspaceState(
        user = user,
        spaces = spaces,
        selectedSpaceId = resolveSelectedSpaceId(
            spaces = spaces,
            preferredSpaceId = selectedSpaceId
        ),
        linksBySpaceId = linksBySpaceId.mapValues { (_, links) ->
            links.map(LinkDto::toWebLinkItem)
        }
    )
}
