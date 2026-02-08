package pl.jsyty.linkstash.contracts.space

import kotlinx.serialization.Serializable

@Serializable
data class SpaceDto(
    val id: String,
    val title: String,
    val createdAt: String? = null
)

@Serializable
data class SpacesListResponse(
    val spaces: List<SpaceDto>
)

@Serializable
data class SpaceCreateRequest(
    val title: String
)

@Serializable
data class SpaceRenameRequest(
    val title: String
)
