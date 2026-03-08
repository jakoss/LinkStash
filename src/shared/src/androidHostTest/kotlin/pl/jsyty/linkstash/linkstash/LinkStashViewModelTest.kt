package pl.jsyty.linkstash.linkstash

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

@OptIn(ExperimentalCoroutinesApi::class)
class LinkStashViewModelTest {
    @Test
    fun syncingIncompleteLinksAddsThemToPendingMetadataIds() = runViewModelTest { repository, viewModel ->
        val incompleteLink = incompleteLink()

        repository.enqueueFlushResult(listOf(incompleteLink))
        repository.enqueueListLinks(DEFAULT_SPACE.id, LinksListResponse(links = listOf(incompleteLink)))

        viewModel.syncPendingQueue()
        advanceUntilIdle()

        assertEquals(setOf(incompleteLink.id), viewModel.uiState.value.pendingMetadataLinkIds)

        viewModel.logout()
        advanceUntilIdle()
    }

    @Test
    fun pollingRemovesPendingIdsWhenMetadataArrives() = runViewModelTest { repository, viewModel ->
        val incompleteLink = incompleteLink()
        val completedLink = completedLink()

        repository.enqueueFlushResult(listOf(incompleteLink))
        repository.enqueueListLinks(
            DEFAULT_SPACE.id,
            LinksListResponse(links = listOf(incompleteLink)),
            LinksListResponse(links = listOf(completedLink))
        )

        viewModel.syncPendingQueue()
        advanceUntilIdle()
        assertEquals(setOf(incompleteLink.id), viewModel.uiState.value.pendingMetadataLinkIds)

        advanceTimeBy(2_000)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingMetadataLinkIds.isEmpty())
        assertEquals(completedLink.previewImageUrl, viewModel.uiState.value.links.single().previewImageUrl)

        viewModel.logout()
        advanceUntilIdle()
    }

    @Test
    fun pollingStopsAfterAttemptBudgetAndLeavesTextOnlyCard() = runViewModelTest { repository, viewModel ->
        val incompleteLink = incompleteLink()

        repository.enqueueFlushResult(listOf(incompleteLink))
        repository.enqueueListLinks(DEFAULT_SPACE.id, LinksListResponse(links = listOf(incompleteLink)))

        viewModel.syncPendingQueue()
        advanceUntilIdle()

        advanceTimeBy(20_000)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingMetadataLinkIds.isEmpty())
        assertTrue(needsMetadataPolling(viewModel.uiState.value.links.single()))
        assertEquals(12, repository.listLinksCalls.count { it.first == DEFAULT_SPACE.id && it.second == null })

        viewModel.logout()
        advanceUntilIdle()
    }

    @Test
    fun selectingAnotherSpaceCancelsMetadataPolling() = runViewModelTest(
        spaces = listOf(DEFAULT_SPACE, ARCHIVE_SPACE)
    ) { repository, viewModel ->
        val incompleteLink = incompleteLink()

        repository.enqueueFlushResult(listOf(incompleteLink))
        repository.enqueueListLinks(
            DEFAULT_SPACE.id,
            LinksListResponse(links = listOf(incompleteLink))
        )
        repository.enqueueListLinks(
            ARCHIVE_SPACE.id,
            LinksListResponse(links = emptyList())
        )

        viewModel.syncPendingQueue()
        advanceUntilIdle()
        assertEquals(setOf(incompleteLink.id), viewModel.uiState.value.pendingMetadataLinkIds)

        viewModel.selectSpace(ARCHIVE_SPACE.id)
        advanceUntilIdle()

        val callsAfterSelection = repository.listLinksCalls.size
        advanceTimeBy(4_000)
        advanceUntilIdle()

        assertEquals(ARCHIVE_SPACE.id, viewModel.uiState.value.selectedSpaceId)
        assertTrue(viewModel.uiState.value.pendingMetadataLinkIds.isEmpty())
        assertEquals(callsAfterSelection, repository.listLinksCalls.size)
    }

    @Test
    fun logoutCancelsMetadataPolling() = runViewModelTest { repository, viewModel ->
        val incompleteLink = incompleteLink()

        repository.enqueueFlushResult(listOf(incompleteLink))
        repository.enqueueListLinks(DEFAULT_SPACE.id, LinksListResponse(links = listOf(incompleteLink)))

        viewModel.syncPendingQueue()
        advanceUntilIdle()
        assertEquals(setOf(incompleteLink.id), viewModel.uiState.value.pendingMetadataLinkIds)

        viewModel.logout()
        advanceUntilIdle()

        val callsAfterLogout = repository.listLinksCalls.size
        advanceTimeBy(4_000)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertTrue(viewModel.uiState.value.pendingMetadataLinkIds.isEmpty())
        assertEquals(callsAfterLogout, repository.listLinksCalls.size)
    }

    private fun runViewModelTest(
        spaces: List<SpaceDto> = listOf(DEFAULT_SPACE),
        block: suspend TestScope.(FakeLinkStashRepository, LinkStashViewModel) -> Unit
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        try {
            val repository = FakeLinkStashRepository(spaces = spaces)
            val viewModel = LinkStashViewModel(
                repository = repository,
                defaultSpaceTitle = DEFAULT_SPACE.title
            )

            advanceUntilIdle()
            block(repository, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeLinkStashRepository(
        private val spaces: List<SpaceDto>
    ) : LinkStashRepository(
        sessionStore = NoOpSessionStore,
        pendingQueueStore = NoOpPendingQueueStore,
        config = LinkStashClientConfig(apiBaseUrl = "http://localhost/")
    ) {
        val listLinksCalls = mutableListOf<Pair<String, String?>>()

        private val listLinksResponses = mutableMapOf<String, ArrayDeque<LinksListResponse>>()
        private val latestListLinksResponse = mutableMapOf<String, LinksListResponse>()
        private val flushResults = ArrayDeque<List<LinkDto>>()

        private var authenticated = true

        override suspend fun hydrateSessionToken() {
        }

        override fun hasSessionToken(): Boolean = authenticated

        override suspend fun fetchCurrentUser(): UserDto {
            return UserDto(id = "user-1", displayName = "Tester")
        }

        override suspend fun exchangeRaindropToken(rawRaindropToken: String): UserDto {
            authenticated = true
            return fetchCurrentUser()
        }

        override suspend fun listSpaces(): List<SpaceDto> = spaces

        override suspend fun listLinks(spaceId: String, cursor: String?): LinksListResponse {
            listLinksCalls += spaceId to cursor
            val queuedResponses = listLinksResponses[spaceId]
            if (queuedResponses != null && queuedResponses.isNotEmpty()) {
                return queuedResponses.removeFirst().also {
                    latestListLinksResponse[spaceId] = it
                }
            }

            return latestListLinksResponse[spaceId] ?: LinksListResponse(links = emptyList())
        }

        override suspend fun flushPendingToDefaultSpace(spaces: List<SpaceDto>): List<LinkDto> {
            return if (flushResults.isEmpty()) {
                emptyList()
            } else {
                flushResults.removeFirst()
            }
        }

        override suspend fun pendingCount(): Int = 0

        override suspend fun clearSession() {
            authenticated = false
        }

        override fun close() {
        }

        fun enqueueListLinks(spaceId: String, vararg responses: LinksListResponse) {
            val queue = listLinksResponses.getOrPut(spaceId) { ArrayDeque() }
            responses.forEach { response ->
                queue.addLast(response)
                latestListLinksResponse[spaceId] = response
            }
        }

        fun enqueueFlushResult(result: List<LinkDto>) {
            flushResults.addLast(result)
        }
    }

    private companion object {
        val DEFAULT_SPACE = SpaceDto(id = "space-inbox", title = "Inbox")
        val ARCHIVE_SPACE = SpaceDto(id = "space-archive", title = "Archive")

        fun incompleteLink(): LinkDto {
            return LinkDto(
                id = "link-1",
                url = "https://example.com/story",
                title = "https://example.com/story",
                excerpt = null,
                previewImageUrl = null,
                createdAt = "2026-03-08T10:00:00Z",
                spaceId = DEFAULT_SPACE.id
            )
        }

        fun completedLink(): LinkDto {
            return LinkDto(
                id = "link-1",
                url = "https://example.com/story",
                title = "Example story",
                excerpt = "A parsed description.",
                previewImageUrl = "https://images.example.com/story.jpg",
                createdAt = "2026-03-08T10:00:00Z",
                spaceId = DEFAULT_SPACE.id
            )
        }

        object NoOpSessionStore : LinkStashSessionStore {
            override suspend fun readBearerToken(): String? = "bearer-token"

            override suspend fun writeBearerToken(token: String) {
            }

            override suspend fun clearBearerToken() {
            }
        }

        object NoOpPendingQueueStore : LinkStashPendingQueueStore {
            override suspend fun enqueue(url: String): Boolean = true

            override suspend fun listOldest(limit: Int): List<PendingQueuedLink> = emptyList()

            override suspend fun deleteById(id: Long) {
            }

            override suspend fun count(): Int = 0
        }
    }
}
