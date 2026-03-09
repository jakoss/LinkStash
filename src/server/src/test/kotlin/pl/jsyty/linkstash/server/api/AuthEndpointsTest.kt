package pl.jsyty.linkstash.server.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.user.UserDto

class AuthEndpointsTest : ApiTestBase() {
    @Test
    fun bearerAuthFlowExchangesSessionLoadsMeAndRevokesOnLogout() = withApiTestClient { client ->
        val session = authenticateBearerSession(client)

        val meResponse = requestWithUpstreamRetry {
            client.get(ME_PATH) {
                bearerAuth(session.bearerToken)
            }
        }
        assertEquals(HttpStatusCode.OK, meResponse.status)
        val meBody = meResponse.body<UserDto>()
        assertEquals(session.userId, meBody.id)

        val logoutResponse = requestWithUpstreamRetry {
            client.post(LOGOUT_PATH) {
                bearerAuth(session.bearerToken)
            }
        }
        assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

        val meAfterLogoutResponse = client.get(ME_PATH) {
            bearerAuth(session.bearerToken)
        }
        assertEquals(HttpStatusCode.Unauthorized, meAfterLogoutResponse.status)
    }

    @Test
    fun cookieAuthFlowReturnsCsrfRejectsMissingTokenAndAllowsWritesWithToken() = withApiTestClient { client ->
        val session = authenticateCookieSession(client)
        var createdSpaceId: String? = null

        try {
            val meResponse = client.get(ME_PATH)
            assertEquals(HttpStatusCode.OK, meResponse.status)
            val meBody = meResponse.body<UserDto>()
            assertEquals(session.userId, meBody.id)

            val csrfResponse = client.get(AUTH_CSRF_PATH)
            assertEquals(HttpStatusCode.OK, csrfResponse.status)
            assertEquals(session.csrfToken, csrfResponse.body<pl.jsyty.linkstash.contracts.auth.AuthCsrfTokenResponse>().csrfToken)

            val missingCsrfResponse = client.post(SPACES_PATH) {
                jsonBody()
                setBody(SpaceCreateRequest(title = "Missing CSRF Should Fail"))
            }
            assertEquals(HttpStatusCode.BadRequest, missingCsrfResponse.status)

            val createResponse = requestWithUpstreamRetry {
                client.post(SPACES_PATH) {
                    jsonBody()
                    csrf(session.csrfToken)
                    setBody(SpaceCreateRequest(title = "Cookie CSRF ${System.currentTimeMillis()}"))
                }
            }
            assertEquals(HttpStatusCode.OK, createResponse.status)
            val createdSpace = createResponse.body<SpaceDto>()
            createdSpaceId = createdSpace.id
            assertTrue(createdSpace.title.startsWith("Cookie CSRF "))

            val logoutResponse = client.post(LOGOUT_PATH) {
                csrf(session.csrfToken)
            }
            assertEquals(HttpStatusCode.NoContent, logoutResponse.status)

            val meAfterLogoutResponse = client.get(ME_PATH)
            assertEquals(HttpStatusCode.Unauthorized, meAfterLogoutResponse.status)
        } finally {
            cleanupSpaceIfPresent(
                client = client,
                session = authenticateBearerSession(client),
                spaceId = createdSpaceId
            )
        }
    }
}
