package pl.jsyty.linkstash.server.auth

import java.time.Clock
import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse
import pl.jsyty.linkstash.contracts.user.UserDto

class ReauthRequiredException(message: String) : RuntimeException(message)

class AuthValidationException(message: String) : RuntimeException(message)

data class LinkStashPrincipal(
    val sessionId: String,
    val userId: String,
    val token: String
)

@Serializable
data class LinkStashCookieSession(val token: String)

class AuthService(
    private val sessionTtlSeconds: Long,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val raindropTokenRepository: RaindropTokenRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val tokenCipher: TokenCipher,
    private val raindropClient: RaindropClient,
    private val linkStashBootstrapService: LinkStashBootstrapService,
    private val linkStashDomainService: LinkStashDomainService,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun exchangeRaindropToken(request: AuthRaindropTokenExchangeRequest): ExchangeResult {
        val accessToken = request.accessToken.trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()

        if (accessToken.isBlank()) {
            throw AuthValidationException("Raindrop access token is required")
        }

        val now = nowEpochSeconds()
        val raindropUser = raindropClient.fetchCurrentUser(accessToken)
        val raindropUserId = raindropUser.stableId()
            ?: throw RaindropUpstreamException("Raindrop user response missing user id")

        val user = userRepository.upsertByRaindropUserId(
            raindropUserId = raindropUserId,
            displayName = raindropUser.displayName(),
            nowEpochSeconds = now
        )

        persistRaindropTokens(
            userId = user.id,
            tokenResponse = RaindropTokenResponse(accessToken = accessToken),
            nowEpochSeconds = now
        )

        withFreshRaindropAccessToken(user.id) { freshAccessToken ->
            linkStashBootstrapService.ensureBootstrap(
                userId = user.id,
                accessToken = freshAccessToken
            )
        }

        val session = createSession(user.id, now)
        return ExchangeResult(
            user = user,
            sessionMode = request.sessionMode,
            sessionToken = session.token,
            sessionExpiresAtEpochSeconds = session.expiresAtEpochSeconds
        )
    }

    suspend fun getCurrentUser(userId: String): UserDto? {
        val localUser = userRepository.findById(userId) ?: return null

        return try {
            val remoteUser = withFreshRaindropAccessToken(userId) { accessToken ->
                linkStashBootstrapService.ensureBootstrap(
                    userId = userId,
                    accessToken = accessToken
                )
                raindropClient.fetchCurrentUser(accessToken)
            }

            userRepository.updateDisplayName(
                userId = localUser.id,
                displayName = remoteUser.displayName(),
                nowEpochSeconds = nowEpochSeconds()
            ) ?: localUser
        } catch (_: RaindropUpstreamException) {
            localUser
        }
    }

    suspend fun listSpaces(userId: String): SpacesListResponse {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashBootstrapService.listSpaces(
                userId = userId,
                accessToken = accessToken
            )
        }
    }

    suspend fun createSpace(userId: String, request: SpaceCreateRequest): SpaceDto {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.createSpace(
                userId = userId,
                accessToken = accessToken,
                request = request
            )
        }
    }

    suspend fun renameSpace(userId: String, spaceId: String, request: SpaceRenameRequest): SpaceDto {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.renameSpace(
                userId = userId,
                accessToken = accessToken,
                spaceId = spaceId,
                request = request
            )
        }
    }

    suspend fun deleteSpace(userId: String, spaceId: String) {
        withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.deleteSpace(
                userId = userId,
                accessToken = accessToken,
                spaceId = spaceId
            )
        }
    }

    suspend fun listLinks(userId: String, spaceId: String, cursor: String?): LinksListResponse {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.listLinks(
                userId = userId,
                accessToken = accessToken,
                spaceId = spaceId,
                cursor = cursor
            )
        }
    }

    suspend fun createLink(userId: String, spaceId: String, request: LinkCreateRequest): LinkDto {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.createLink(
                userId = userId,
                accessToken = accessToken,
                spaceId = spaceId,
                request = request
            )
        }
    }

    suspend fun moveLink(userId: String, linkId: String, request: LinkMoveRequest): LinkDto {
        return withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.moveLink(
                userId = userId,
                accessToken = accessToken,
                linkId = linkId,
                request = request
            )
        }
    }

    suspend fun deleteLink(userId: String, linkId: String) {
        withFreshRaindropAccessToken(userId) { accessToken ->
            linkStashDomainService.deleteLink(
                userId = userId,
                accessToken = accessToken,
                linkId = linkId
            )
        }
    }

    fun resolvePrincipal(token: String): LinkStashPrincipal? {
        if (token.isBlank()) return null

        val session = sessionRepository.findActiveByTokenHash(
            tokenHash = tokenHasher.hash(token),
            nowEpochSeconds = nowEpochSeconds()
        ) ?: return null

        return LinkStashPrincipal(
            sessionId = session.id,
            userId = session.userId,
            token = token
        )
    }

    fun revokeSession(sessionId: String): Boolean {
        return sessionRepository.revokeBySessionId(
            sessionId = sessionId,
            nowEpochSeconds = nowEpochSeconds()
        )
    }

    private fun createSession(userId: String, nowEpochSeconds: Long): CreatedSession {
        val token = tokenGenerator.randomUrlSafeToken(sizeBytes = 32)
        val expiresAtEpochSeconds = nowEpochSeconds + sessionTtlSeconds

        val persistedSession = sessionRepository.create(
            userId = userId,
            tokenHash = tokenHasher.hash(token),
            nowEpochSeconds = nowEpochSeconds,
            expiresAtEpochSeconds = expiresAtEpochSeconds
        )

        return CreatedSession(
            id = persistedSession.id,
            token = token,
            expiresAtEpochSeconds = expiresAtEpochSeconds
        )
    }

    private fun persistRaindropTokens(
        userId: String,
        tokenResponse: RaindropTokenResponse,
        nowEpochSeconds: Long
    ) {
        val expiresAtEpochSeconds = tokenResponse.expiresInSeconds
            ?.let { nowEpochSeconds + it }

        raindropTokenRepository.upsert(
            userId = userId,
            accessTokenEncrypted = tokenCipher.encrypt(tokenResponse.accessToken),
            refreshTokenEncrypted = tokenResponse.refreshToken?.let(tokenCipher::encrypt),
            expiresAtEpochSeconds = expiresAtEpochSeconds,
            scope = tokenResponse.scope,
            nowEpochSeconds = nowEpochSeconds
        )
    }

    private suspend fun <T> withFreshRaindropAccessToken(
        userId: String,
        block: suspend (String) -> T
    ): T {
        val tokens = loadTokens(userId)

        try {
            return block(tokens.accessToken)
        } catch (_: RaindropUnauthorizedException) {
            reauthRequired(userId, "Raindrop access token is no longer valid")
        }
    }

    private suspend fun loadTokens(userId: String): PlainRaindropTokens {
        val storedTokens = raindropTokenRepository.findByUserId(userId)
            ?: reauthRequired(userId, "Raindrop tokens were not found for this user")

        return PlainRaindropTokens(
            accessToken = tokenCipher.decrypt(storedTokens.accessTokenEncrypted)
        )
    }

    private fun reauthRequired(userId: String, reason: String): Nothing {
        val now = nowEpochSeconds()
        raindropTokenRepository.deleteForUser(userId)
        sessionRepository.revokeAllForUser(userId, now)
        throw ReauthRequiredException(reason)
    }

    private fun nowEpochSeconds(): Long = clock.instant().epochSecond

    data class CreatedSession(
        val id: String,
        val token: String,
        val expiresAtEpochSeconds: Long
    )

    data class ExchangeResult(
        val user: UserDto,
        val sessionMode: AuthSessionMode,
        val sessionToken: String,
        val sessionExpiresAtEpochSeconds: Long
    )

    private data class PlainRaindropTokens(
        val accessToken: String
    )
}
