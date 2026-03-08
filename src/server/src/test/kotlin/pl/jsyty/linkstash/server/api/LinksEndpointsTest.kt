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
import kotlin.test.assertTrue
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto

class LinksEndpointsTest : ApiTestBase() {
    @Test
    fun listLinksForNewSpaceReturnsEmptyList() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var sourceSpaceId: String? = null

        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceSpace = createSpace(client, session, "API Link List Source $runId")
            sourceSpaceId = sourceSpace.id

            val sourceLinksInitial = listLinks(client, session, sourceSpace.id)
            assertTrue(sourceLinksInitial.links.isEmpty())
        } finally {
            cleanupSpaceIfPresent(client = client, session = session, spaceId = sourceSpaceId)
        }
    }

    @Test
    fun createLinkInSpaceReturnsPayload() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var sourceSpaceId: String? = null
        var linkId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceSpace = createSpace(client, session, "API Link Create Source $runId")
            sourceSpaceId = sourceSpace.id

            val linkUrl = "https://example.com/?linkstash-api-test=$runId"
            val createdLink = createLink(client, session, sourceSpace.id, linkUrl)
            linkId = createdLink.id
            assertEquals(sourceSpace.id, createdLink.spaceId)
            assertEquals(linkUrl, createdLink.url)
            assertTrue(createdLink.previewImageUrl?.isNotBlank() ?: true)

            val sourceLinks = listLinksUntilContains(client, session, sourceSpace.id, createdLink.id)
            assertTrue(
                sourceLinks.links.any { link ->
                    link.id == createdLink.id &&
                        link.spaceId == sourceSpace.id &&
                        (link.previewImageUrl?.isNotBlank() ?: true)
                }
            )
        } finally {
            cleanupLinkIfPresent(client = client, session = session, linkId = linkId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = sourceSpaceId)
        }
    }

    @Test
    fun moveLinkToAnotherSpaceReturnsOkOrNotFound() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var sourceSpaceId: String? = null
        var targetSpaceId: String? = null
        var linkId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceSpace = createSpace(client, session, "API Link Move Source $runId")
            sourceSpaceId = sourceSpace.id
            val targetSpace = createSpace(client, session, "API Link Move Target $runId")
            targetSpaceId = targetSpace.id

            val createdLink = createLink(
                client = client,
                session = session,
                spaceId = sourceSpace.id,
                url = "https://example.com/?linkstash-api-move-test=$runId"
            )
            linkId = createdLink.id

            val moveLinkResponse = requestWithUpstreamRetry {
                client.patch(linkPath(createdLink.id)) {
                    bearerAuth(session.bearerToken)
                    jsonBody()
                    setBody(LinkMoveRequest(spaceId = targetSpace.id))
                }
            }
            assertEquals(HttpStatusCode.OK, moveLinkResponse.status)
            val movedLink = moveLinkResponse.body<LinkDto>()
            assertEquals(createdLink.id, movedLink.id)
            assertEquals(targetSpace.id, movedLink.spaceId)
            assertTrue(movedLink.previewImageUrl?.isNotBlank() ?: true)

            val targetLinks = listLinksUntilContains(client, session, targetSpace.id, createdLink.id)
            assertTrue(
                targetLinks.links.any { link ->
                    link.id == createdLink.id &&
                        link.spaceId == targetSpace.id &&
                        (link.previewImageUrl?.isNotBlank() ?: true)
                }
            )

            val sourceLinks = listLinksUntilMissing(client, session, sourceSpace.id, createdLink.id)
            assertTrue(sourceLinks.links.none { it.id == createdLink.id })
        } finally {
            cleanupLinkIfPresent(client = client, session = session, linkId = linkId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = sourceSpaceId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = targetSpaceId)
        }
    }

    @Test
    fun deleteLinkReturnsNoContentOrNotFound() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)
        var sourceSpaceId: String? = null
        var linkId: String? = null
        try {
            val runId = UUID.randomUUID().toString().take(8)
            val sourceSpace = createSpace(client, session, "API Link Delete Source $runId")
            sourceSpaceId = sourceSpace.id
            val createdLink = createLink(
                client = client,
                session = session,
                spaceId = sourceSpace.id,
                url = "https://example.com/?linkstash-api-delete-test=$runId"
            )
            linkId = createdLink.id

            val deleteLinkResponse = requestWithUpstreamRetry {
                client.delete(linkPath(createdLink.id)) {
                    bearerAuth(session.bearerToken)
                }
            }
            assertEquals(HttpStatusCode.NoContent, deleteLinkResponse.status)
            linkId = null

            val sourceLinks = listLinksUntilMissing(client, session, sourceSpace.id, createdLink.id)
            assertTrue(sourceLinks.links.none { it.id == createdLink.id })
        } finally {
            cleanupLinkIfPresent(client = client, session = session, linkId = linkId)
            cleanupSpaceIfPresent(client = client, session = session, spaceId = sourceSpaceId)
        }
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
        return response.body<SpaceDto>()
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
