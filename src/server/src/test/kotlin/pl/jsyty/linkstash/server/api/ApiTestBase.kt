package pl.jsyty.linkstash.server.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlin.io.path.createTempFile
import kotlin.io.path.pathString
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.contracts.auth.AuthExchangeResponse
import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.server.config.AppConfig
import pl.jsyty.linkstash.server.linkStashModule

abstract class ApiTestBase {
    protected companion object {
        const val HEALTHZ_PATH = "/healthz"
        const val V1_PATH = "/v1"
        const val AUTH_RAINDROP_TOKEN_PATH = "$V1_PATH/auth/raindrop/token"
        const val ME_PATH = "$V1_PATH/me"
        const val SPACES_PATH = "$V1_PATH/spaces"
        const val LINKS_PATH = "$V1_PATH/links"
        const val LOGOUT_PATH = "$V1_PATH/auth/logout"
    }

    protected data class BearerSession(
        val userId: String,
        val bearerToken: String
    )

    protected fun spacePath(spaceId: String): String = "$SPACES_PATH/$spaceId"

    protected fun spaceLinksPath(spaceId: String): String = "${spacePath(spaceId)}/links"

    protected fun linkPath(linkId: String): String = "$LINKS_PATH/$linkId"

    protected fun withApiTestClient(testBody: suspend (HttpClient) -> Unit) = testApplication {
        application {
            linkStashModule(config = testAppConfig())
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(LinkStashJson.instance)
            }
            install(HttpCookies)
        }

        testBody(client)
    }

    protected suspend fun authenticateBearerSession(client: HttpClient): BearerSession {
        val exchangeResponse = requestWithUpstreamRetry {
            client.post(AUTH_RAINDROP_TOKEN_PATH) {
                jsonBody()
                setBody(
                    AuthRaindropTokenExchangeRequest(
                        accessToken = requireRaindropToken(),
                        sessionMode = AuthSessionMode.BEARER
                    )
                )
            }
        }

        assertEquals(HttpStatusCode.OK, exchangeResponse.status)
        val exchangeBody = exchangeResponse.body<AuthExchangeResponse>()
        assertTrue(exchangeBody.user.id.isNotBlank())
        val bearerToken = assertNotNull(exchangeBody.bearerToken)
        assertTrue(bearerToken.isNotBlank())

        return BearerSession(
            userId = exchangeBody.user.id,
            bearerToken = bearerToken
        )
    }

    protected suspend fun cleanupLinkIfPresent(
        client: HttpClient,
        session: BearerSession,
        linkId: String?
    ) {
        if (linkId == null) return
        requestWithUpstreamRetry {
            client.delete(linkPath(linkId)) {
                bearerAuth(session.bearerToken)
            }
        }
    }

    protected suspend fun cleanupSpaceIfPresent(
        client: HttpClient,
        session: BearerSession,
        spaceId: String?
    ) {
        if (spaceId == null) return
        requestWithUpstreamRetry {
            client.delete(spacePath(spaceId)) {
                bearerAuth(session.bearerToken)
            }
        }
    }

    protected fun HttpRequestBuilder.jsonBody() {
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }

    protected suspend fun requestWithUpstreamRetry(
        maxAttempts: Int = 3,
        retryDelayMs: Long = 500,
        request: suspend () -> HttpResponse
    ): HttpResponse {
        var attempt = 0
        var lastResponse: HttpResponse? = null
        while (attempt < maxAttempts) {
            attempt += 1
            val response = request()
            lastResponse = response
            if (response.status != HttpStatusCode.BadGateway) {
                return response
            }
            if (attempt < maxAttempts) {
                delay(retryDelayMs)
            }
        }

        assumeTrue(
            "Raindrop upstream returned 502 for $maxAttempts attempt(s); skipping flaky integration test run.",
            false
        )
        return lastResponse ?: error("No response captured")
    }

    private fun testAppConfig(): AppConfig {
        val dbPath = createTempFile(prefix = "linkstash-api-test-", suffix = ".sqlite").pathString
        return AppConfig.fromEnv(
            mapOf(
                "DB_URL" to "jdbc:sqlite:$dbPath",
                "SESSION_SECRET" to "test-session-secret",
                "TOKEN_HASHING_SECRET" to "test-token-hashing-secret",
                "RAINDROP_TOKEN_ENCRYPTION_KEY" to "test-token-encryption-secret",
                "SESSION_COOKIE_SECURE" to "false",
                "LINKSTASH_ROOT_COLLECTION_TITLE" to "LinkStash",
                "LINKSTASH_DEFAULT_SPACE_TITLE" to "Inbox"
            )
        )
    }

    private fun requireRaindropToken(): String {
        val token = System.getProperty("api.test.raindrop.token")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: System.getenv("API_TEST_RAINDROP_TOKEN")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        assumeTrue(
            "Set API_TEST_RAINDROP_TOKEN in src/server/.env.api-test or use -PapiTestEnvFile=/path/to/env",
            token != null
        )

        return token
            ?: error("API_TEST_RAINDROP_TOKEN was not provided")
    }
}
