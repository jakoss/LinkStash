package pl.jsyty.linkstash.server.api

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
