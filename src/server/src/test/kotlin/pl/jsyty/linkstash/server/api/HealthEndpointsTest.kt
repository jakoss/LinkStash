package pl.jsyty.linkstash.server.api

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthEndpointsTest : ApiTestBase() {
    @Test
    fun healthEndpointReturnsOk() = withApiTestClient { client ->
        val healthResponse = client.get(HEALTHZ_PATH)
        assertEquals(HttpStatusCode.OK, healthResponse.status)
    }
}
