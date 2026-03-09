package pl.jsyty.linkstash.server.api

import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebUiEndpointsTest : ApiTestBase() {
    @Test
    fun rootServesBundledWebUi() = withApiTestClient { client ->
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("text/html") == true)
        assertTrue(response.bodyAsText().contains("<script src=\"linkstash-web.js\"></script>"))
    }

    @Test
    fun headOnRootReturnsOk() = withApiTestClient { client ->
        val response = client.head("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("text/html") == true)
    }
}
