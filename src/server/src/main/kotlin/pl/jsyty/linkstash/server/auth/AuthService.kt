package pl.jsyty.linkstash.server.auth

import java.time.Clock
import kotlinx.serialization.Serializable
import pl.jsyty.linkstash.contracts.auth.AuthExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.auth.AuthStartResponse
import pl.jsyty.linkstash.contracts.user.UserDto
import pl.jsyty.linkstash.server.config.AppConfig

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
    private val config: AppConfig,
    private val oauthStateRepository: OauthStateRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val raindropTokenRepository: RaindropTokenRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val tokenCipher: TokenCipher,
    private val raindropClient: RaindropClient,
    private val clock: Clock = Clock.systemUTC()
) {
    fun startAuth(redirectUriOverride: String?, codeVerifier: String?): AuthStartResponse {
        val redirectUri = redirectUriOverride?.takeIf { it.isNotBlank() }
            ?: config.raindropDefaultRedirectUri
        val codeVerifierHash = codeVerifier
            ?.takeIf { it.isNotBlank() }
            ?.let(tokenHasher::hash)

        val now = nowEpochSeconds()
        val state = tokenGenerator.randomUrlSafeToken(sizeBytes = 24)
        oauthStateRepository.create(
            state = state,
            redirectUri = redirectUri,
            codeVerifierHash = codeVerifierHash,
            nowEpochSeconds = now,
            expiresAtEpochSeconds = now + config.oauthStateTtlSeconds
        )

        return AuthStartResponse(
            url = raindropClient.buildAuthorizeUrl(state = state, redirectUri = redirectUri)
        )
    }

    suspend fun exchangeCode(request: AuthExchangeRequest): ExchangeResult {
        if (request.code.isBlank()) throw AuthValidationException("OAuth code is required")
        if (request.state.isBlank()) throw AuthValidationException("OAuth state is required")
        if (request.redirectUri.isBlank()) throw AuthValidationException("OAuth redirectUri is required")

        val codeVerifierHash = request.codeVerifier
            ?.takeIf { it.isNotBlank() }
            ?.let(tokenHasher::hash)

        val isStateValid = oauthStateRepository.consumeIfValid(
            state = request.state,
            redirectUri = request.redirectUri,
            codeVerifierHash = codeVerifierHash,
            nowEpochSeconds = nowEpochSeconds()
        )

        if (!isStateValid) {
            throw AuthValidationException("OAuth state is invalid or expired")
        }

        val tokenResponse = raindropClient.exchangeCodeForTokens(
            code = request.code,
            redirectUri = request.redirectUri,
            codeVerifier = request.codeVerifier
        )

        val now = nowEpochSeconds()
        val raindropUser = raindropClient.fetchCurrentUser(tokenResponse.accessToken)
        val raindropUserId = raindropUser.stableId()
            ?: throw RaindropUpstreamException("Raindrop user response missing user id")
        val user = userRepository.upsertByRaindropUserId(
            raindropUserId = raindropUserId,
            displayName = raindropUser.displayName(),
            nowEpochSeconds = now
        )

        persistRaindropTokens(
            userId = user.id,
            tokenResponse = tokenResponse,
            nowEpochSeconds = now
        )

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
        val expiresAtEpochSeconds = nowEpochSeconds + config.sessionTtlSeconds

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
        var tokens = loadAndMaybeRefreshTokens(userId)

        try {
            return block(tokens.accessToken)
        } catch (_: RaindropUnauthorizedException) {
            val refreshToken = tokens.refreshToken
                ?: reauthRequired(userId, "Raindrop access token expired and no refresh token is available")

            val refreshed = refreshTokens(userId, refreshToken)
            tokens = refreshed
            return block(tokens.accessToken)
        }
    }

    private suspend fun loadAndMaybeRefreshTokens(userId: String): PlainRaindropTokens {
        val storedTokens = raindropTokenRepository.findByUserId(userId)
            ?: reauthRequired(userId, "Raindrop tokens were not found for this user")

        val plainTokens = PlainRaindropTokens(
            accessToken = tokenCipher.decrypt(storedTokens.accessTokenEncrypted),
            refreshToken = storedTokens.refreshTokenEncrypted?.let(tokenCipher::decrypt),
            expiresAtEpochSeconds = storedTokens.expiresAtEpochSeconds,
            scope = storedTokens.scope
        )

        val now = nowEpochSeconds()
        val isNearExpiry = plainTokens.expiresAtEpochSeconds
            ?.let { it <= now + 60L }
            ?: false

        if (!isNearExpiry) return plainTokens

        val refreshToken = plainTokens.refreshToken
            ?: reauthRequired(userId, "Raindrop access token expired and no refresh token is available")

        return refreshTokens(userId, refreshToken)
    }

    private suspend fun refreshTokens(userId: String, refreshToken: String): PlainRaindropTokens {
        val refreshedTokens = try {
            raindropClient.refreshAccessToken(refreshToken)
        } catch (error: Throwable) {
            reauthRequired(userId, "Raindrop token refresh failed: ${error.message ?: "unknown error"}")
        }

        val now = nowEpochSeconds()
        persistRaindropTokens(userId, refreshedTokens, now)

        return PlainRaindropTokens(
            accessToken = refreshedTokens.accessToken,
            refreshToken = refreshedTokens.refreshToken ?: refreshToken,
            expiresAtEpochSeconds = refreshedTokens.expiresInSeconds?.let { now + it },
            scope = refreshedTokens.scope
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
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long?,
        val scope: String?
    )
}
