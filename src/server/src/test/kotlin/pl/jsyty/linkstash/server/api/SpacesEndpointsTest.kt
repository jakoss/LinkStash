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
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceArchiveRequest
import pl.jsyty.linkstash.contracts.space.SpaceArchiveResponse
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
    fun archiveSpaceCreatesNewSpaceAndMovesExistingLinks() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var sourceSpaceId: String? = null
        var archivedSpaceId: String? = null
        var linkId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceSpace = createSpace(client, session, "API Space Archive Source $runId")
            sourceSpaceId = sourceSpace.id
            val createdLink = createLink(
                client = client,
                session = session,
                spaceId = sourceSpace.id,
                url = "https://example.com/?linkstash-api-archive-test=$runId"
            )
            linkId = createdLink.id

            val archiveResponse = requestWithUpstreamRetry {
                client.post(archiveSpacePath(sourceSpace.id)) {
                    bearerAuth(session.bearerToken)
                    jsonBody()
                    setBody(SpaceArchiveRequest(title = "API Space Archive Target $runId"))
                }
            }
            assertEquals(HttpStatusCode.OK, archiveResponse.status)
            val archivedSpace = archiveResponse.body<SpaceArchiveResponse>()
            archivedSpaceId = archivedSpace.space.id
            assertEquals("API Space Archive Target $runId", archivedSpace.space.title)
            assertEquals(1, archivedSpace.movedLinksCount)

            val spacesAfterArchive = listSpaces(client, session)
            assertTrue(spacesAfterArchive.spaces.any { it.id == archivedSpace.space.id && it.title == archivedSpace.space.title })

            val archivedLinks = listLinksUntilContains(client, session, archivedSpace.space.id, createdLink.id)
            assertTrue(archivedLinks.links.any { link -> link.id == createdLink.id && link.spaceId == archivedSpace.space.id })

            val sourceLinks = listLinksUntilMissing(client, session, sourceSpace.id, createdLink.id)
            assertTrue(sourceLinks.links.none { it.id == createdLink.id })
            linkId = null
        } finally {
            cleanupLinkIfPresent(client = client, session = session, linkId = linkId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = sourceSpaceId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = archivedSpaceId)
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

    private suspend fun createLink(
        client: io.ktor.client.HttpClient,
        session: BearerSession,
        spaceId: String,
        url: String
    ): LinkDto {
        val response = requestWithUpstreamRetry {
            client.post(spaceLinksPath(spaceId)) {
                bearerAuth(session.bearerToken)
                jsonBody()
                setBody(LinkCreateRequest(url = url))
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<LinkDto>()
    }

    private suspend fun listLinks(
        client: io.ktor.client.HttpClient,
        session: BearerSession,
        spaceId: String
    ): LinksListResponse {
        val response = requestWithUpstreamRetry {
            client.get(spaceLinksPath(spaceId)) {
                bearerAuth(session.bearerToken)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return response.body<LinksListResponse>()
    }

    private suspend fun listLinksUntilContains(
        client: io.ktor.client.HttpClient,
        session: BearerSession,
        spaceId: String,
        linkId: String,
        attempts: Int = 5,
        delayMs: Long = 500
    ): LinksListResponse {
        repeat(attempts) { attempt ->
            val links = listLinks(client, session, spaceId)
            if (links.links.any { it.id == linkId }) {
                return links
            }
            if (attempt < attempts - 1) {
                delay(delayMs)
            }
        }

        return listLinks(client, session, spaceId)
    }

    private suspend fun listLinksUntilMissing(
        client: io.ktor.client.HttpClient,
        session: BearerSession,
        spaceId: String,
        linkId: String,
        attempts: Int = 5,
        delayMs: Long = 500
    ): LinksListResponse {
        repeat(attempts) { attempt ->
            val links = listLinks(client, session, spaceId)
            if (links.links.none { it.id == linkId }) {
                return links
            }
            if (attempt < attempts - 1) {
                delay(delayMs)
            }
        }

        return listLinks(client, session, spaceId)
    }
}
