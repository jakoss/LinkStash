package pl.jsyty.linkstash.contracts.link

import kotlinx.serialization.Serializable

@Serializable
data class LinkDto(
    val id: String,
    val url: String,
    val title: String? = null,
    val excerpt: String? = null,
    val createdAt: String? = null,
    val spaceId: String
)

@Serializable
data class LinksListResponse(
    val links: List<LinkDto>,
    val nextCursor: String? = null
)

@Serializable
data class LinkCreateRequest(
    val url: String,
    val spaceId: String? = null
)

@Serializable
data class LinkMoveRequest(
    val spaceId: String
)
