package pl.jsyty.linkstash.server.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.jetbrains.exposed.v1.jdbc.Database
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.contracts.auth.AuthExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthExchangeResponse
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.error.ApiError
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.error.ApiErrorEnvelope
import pl.jsyty.linkstash.server.config.AppConfig

fun Application.configureAuthModule(config: AppConfig, database: Database) {
    val raindropHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(LinkStashJson.instance)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        raindropHttpClient.close()
    }

    val raindropClient = RaindropClient(config, raindropHttpClient)
    val linkStashConfigRepository = LinkStashConfigRepository(database)
    val linkStashBootstrapService = LinkStashBootstrapService(
        config = config,
        linkStashConfigRepository = linkStashConfigRepository,
        raindropClient = raindropClient
    )

    val authService = AuthService(
        config = config,
        oauthStateRepository = OauthStateRepository(database),
        sessionRepository = SessionRepository(database),
        userRepository = UserRepository(database),
        raindropTokenRepository = RaindropTokenRepository(database),
        tokenGenerator = TokenGenerator(),
        tokenHasher = TokenHasher(config.tokenHashingSecret),
        tokenCipher = TokenCipher(config.raindropTokenEncryptionKey),
        raindropClient = raindropClient,
        linkStashBootstrapService = linkStashBootstrapService
    )

    install(StatusPages) {
        exception<AuthValidationException> { call, error ->
            call.respondApiError(
                status = HttpStatusCode.BadRequest,
                code = ApiErrorCode.VALIDATION_ERROR,
                message = error.message ?: "Request validation failed"
            )
        }
        exception<ReauthRequiredException> { call, error ->
            call.respondApiError(
                status = HttpStatusCode.Unauthorized,
                code = ApiErrorCode.UNAUTHORIZED,
                message = error.message ?: "Authentication is required"
            )
        }
        exception<RaindropUnauthorizedException> { call, error ->
            call.respondApiError(
                status = HttpStatusCode.Unauthorized,
                code = ApiErrorCode.UNAUTHORIZED,
                message = error.message ?: "Authentication is required"
            )
        }
        exception<RaindropUpstreamException> { call, error ->
            call.respondApiError(
                status = HttpStatusCode.BadGateway,
                code = ApiErrorCode.UPSTREAM_ERROR,
                message = error.message ?: "Raindrop upstream request failed"
            )
        }
        exception<Throwable> { call, error ->
            call.respondApiError(
                status = HttpStatusCode.InternalServerError,
                code = ApiErrorCode.INTERNAL_ERROR,
                message = error.message ?: "Internal server error"
            )
        }
    }

    install(Sessions) {
        cookie<LinkStashCookieSession>(name = config.sessionCookieName) {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = config.sessionCookieSecure
            cookie.extensions["SameSite"] = "lax"
            transform(
                SessionTransportTransformerMessageAuthentication(
                    MessageDigest.getInstance("SHA-256")
                        .digest(config.sessionSecret.toByteArray(StandardCharsets.UTF_8))
                )
            )
        }
    }

    install(Authentication) {
        session<LinkStashCookieSession>("auth-session") {
            validate { session ->
                authService.resolvePrincipal(session.token)
            }
        }

        bearer("auth-bearer") {
            authenticate { credential ->
                authService.resolvePrincipal(credential.token)
            }
        }
    }

    routing {
        route("/v1") {
            get("/auth/raindrop/start") {
                val redirectUri = call.request.queryParameters["redirectUri"]
                val codeVerifier = call.request.queryParameters["codeVerifier"]
                call.respond(authService.startAuth(redirectUri, codeVerifier))
            }

            post("/auth/raindrop/start") {
                val redirectUri = call.request.queryParameters["redirectUri"]
                val codeVerifier = call.request.queryParameters["codeVerifier"]
                call.respond(authService.startAuth(redirectUri, codeVerifier))
            }

            post("/auth/raindrop/exchange") {
                val request = call.receive<AuthExchangeRequest>()
                val result = authService.exchangeCode(request)

                if (result.sessionMode == AuthSessionMode.COOKIE) {
                    call.sessions.set(LinkStashCookieSession(result.sessionToken))
                }

                call.respond(
                    AuthExchangeResponse(
                        user = result.user,
                        bearerToken = if (result.sessionMode == AuthSessionMode.BEARER) result.sessionToken else null,
                        bearerTokenExpiresAtEpochSeconds = result.sessionExpiresAtEpochSeconds
                    )
                )
            }

            authenticate("auth-session", "auth-bearer", strategy = AuthenticationStrategy.FirstSuccessful) {
                get("/me") {
                    val principal = call.principal<LinkStashPrincipal>()
                        ?: return@get call.respondApiError(
                            status = HttpStatusCode.Unauthorized,
                            code = ApiErrorCode.UNAUTHORIZED,
                            message = "Authentication required"
                        )

                    val currentUser = authService.getCurrentUser(principal.userId)
                        ?: return@get call.respondApiError(
                            status = HttpStatusCode.Unauthorized,
                            code = ApiErrorCode.UNAUTHORIZED,
                            message = "User no longer exists"
                        )

                    call.respond(currentUser)
                }

                get("/spaces") {
                    val principal = call.principal<LinkStashPrincipal>()
                        ?: return@get call.respondApiError(
                            status = HttpStatusCode.Unauthorized,
                            code = ApiErrorCode.UNAUTHORIZED,
                            message = "Authentication required"
                        )

                    call.respond(authService.listSpaces(principal.userId))
                }

                post("/auth/logout") {
                    val principal = call.principal<LinkStashPrincipal>()
                        ?: return@post call.respondApiError(
                            status = HttpStatusCode.Unauthorized,
                            code = ApiErrorCode.UNAUTHORIZED,
                            message = "Authentication required"
                        )

                    authService.revokeSession(principal.sessionId)
                    call.sessions.clear<LinkStashCookieSession>()
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondApiError(
    status: HttpStatusCode,
    code: ApiErrorCode,
    message: String
) {
    respond(
        status = status,
        message = ApiErrorEnvelope(
            error = ApiError(
                code = code,
                message = message
            )
        )
    )
}
