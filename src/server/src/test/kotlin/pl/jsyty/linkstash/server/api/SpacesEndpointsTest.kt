package pl.jsyty.linkstash.server.api

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse

class SpacesEndpointsTest : ApiTestBase() {
    @Test
    fun listSpacesReturnsAtLeastDefaultSpace() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        val spacesResponse = listSpaces(client, session)
        assertTrue(spacesResponse.spaces.isNotEmpty())
    }

    @Test
    fun createSpaceAddsNewSpaceToList() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var createdSpaceId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val title = "API Space Create $runId"
            val createdSpace = createSpace(client, session, title)
            createdSpaceId = createdSpace.id

            val spacesAfterCreate = listSpaces(client, session)
            assertTrue(spacesAfterCreate.spaces.any { it.id == createdSpace.id && it.title == title })
        } finally {
            cleanupSpaceIfPresent(client = client, session = session, spaceId = createdSpaceId)
        }
    }

    @Test
    fun renameSpaceUpdatesTitle() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var createdSpaceId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceTitle = "API Space Rename $runId"
            val renamedTitle = "API Space Renamed $runId"
            val createdSpace = createSpace(client, session, sourceTitle)
            createdSpaceId = createdSpace.id

            val renameSpaceResponse = requestWithUpstreamRetry {
                client.patch(spacePath(createdSpace.id)) {
                    bearerAuth(session.bearerToken)
                    jsonBody()
                    setBody(SpaceRenameRequest(title = renamedTitle))
                }
            }
            assertEquals(HttpStatusCode.OK, renameSpaceResponse.status)
            val renamedSpace = renameSpaceResponse.body<SpaceDto>()
            assertEquals(createdSpace.id, renamedSpace.id)
            assertEquals(renamedTitle, renamedSpace.title)

            val spacesAfterRename = listSpaces(client, session)
            assertTrue(spacesAfterRename.spaces.any { it.id == createdSpace.id && it.title == renamedTitle })
        } finally {
            cleanupSpaceIfPresent(client = client, session = session, spaceId = createdSpaceId)
        }
    }

    @Test
    fun deleteSpaceRemovesSpaceFromList() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var createdSpaceId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val createdSpace = createSpace(client, session, "API Space Delete $runId")
            createdSpaceId = createdSpace.id

            val deleteSpaceResponse = requestWithUpstreamRetry {
                client.delete(spacePath(createdSpace.id)) {
                    bearerAuth(session.bearerToken)
                }
            }
            assertEquals(HttpStatusCode.NoContent, deleteSpaceResponse.status)
            createdSpaceId = null

            val spacesAfterDelete = listSpaces(client, session)
            assertFalse(spacesAfterDelete.spaces.any { it.id == createdSpace.id })
        } finally {
            cleanupSpaceIfPresent(client = client, session = session, spaceId = createdSpaceId)
        }
    }

    private suspend fun listSpaces(client: io.ktor.client.HttpClient, session: BearerSession): SpacesListResponse {
        val response = requestWithUpstreamRetry {
            client.get(SPACES_PATH) {
                bearerAuth(session.bearerToken)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<SpacesListResponse>()
    }

    private suspend fun createSpace(
        client: io.ktor.client.HttpClient,
        session: BearerSession,
        title: String
    ): SpaceDto {
        val response = requestWithUpstreamRetry {
            client.post(SPACES_PATH) {
                bearerAuth(session.bearerToken)
                jsonBody()
                setBody(SpaceCreateRequest(title = title))
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val createdSpace = response.body<SpaceDto>()
        assertEquals(title, createdSpace.title)
        return createdSpace
    }
}
