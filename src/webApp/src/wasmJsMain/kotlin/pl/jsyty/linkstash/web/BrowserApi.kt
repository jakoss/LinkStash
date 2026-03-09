@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package pl.jsyty.linkstash.web

import io.ktor.http.HttpStatusCode
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.JsFun
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlin.js.unsafeCast
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestCredentials
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.contracts.auth.AuthCsrfTokenResponse
import pl.jsyty.linkstash.contracts.auth.AuthExchangeResponse
import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthSessionMode
import pl.jsyty.linkstash.contracts.client.ApiException
import pl.jsyty.linkstash.contracts.error.ApiError
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.error.ApiErrorEnvelope
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse
import pl.jsyty.linkstash.contracts.user.UserDto

internal class BrowserApi(
    private val baseUrl: String,
    private val csrfTokenProvider: () -> String?
) {
    suspend fun exchangeRaindropToken(rawToken: String): AuthExchangeResponse {
        val token = rawToken.trim().requireNonBlank("Raindrop token is required")
        return request(
            path = "/v1/auth/raindrop/token",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(
                AuthRaindropTokenExchangeRequest(
                    accessToken = token,
                    sessionMode = AuthSessionMode.COOKIE
                )
            )
        )
    }

    suspend fun fetchCsrfToken(): String {
        return request<AuthCsrfTokenResponse>(
            path = "/v1/auth/csrf",
            method = "GET",
            includeCsrf = false
        ).csrfToken
    }

    suspend fun me(): UserDto = request(path = "/v1/me", method = "GET", includeCsrf = false)

    suspend fun logout() {
        requestUnit(path = "/v1/auth/logout", method = "POST")
    }

    suspend fun listSpaces(): List<SpaceDto> {
        return request<SpacesListResponse>(path = "/v1/spaces", method = "GET", includeCsrf = false).spaces
    }

    suspend fun createSpace(title: String): SpaceDto {
        return request(
            path = "/v1/spaces",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(SpaceCreateRequest(title = title))
        )
    }

    suspend fun renameSpace(spaceId: String, title: String): SpaceDto {
        return request(
            path = "/v1/spaces/$spaceId",
            method = "PATCH",
            bodyText = LinkStashJson.instance.encodeToString(SpaceRenameRequest(title = title))
        )
    }

    suspend fun deleteSpace(spaceId: String) {
        requestUnit(path = "/v1/spaces/$spaceId", method = "DELETE")
    }

    suspend fun listLinks(spaceId: String): List<LinkDto> {
        return request<LinksListResponse>(
            path = "/v1/spaces/$spaceId/links",
            method = "GET",
            includeCsrf = false
        ).links
    }

    suspend fun createLink(spaceId: String, url: String): LinkDto {
        return request(
            path = "/v1/spaces/$spaceId/links",
            method = "POST",
            bodyText = LinkStashJson.instance.encodeToString(LinkCreateRequest(url = url))
        )
    }

    suspend fun moveLink(linkId: String, targetSpaceId: String): LinkDto {
        return request(
            path = "/v1/links/$linkId",
            method = "PATCH",
            bodyText = LinkStashJson.instance.encodeToString(LinkMoveRequest(spaceId = targetSpaceId))
        )
    }

    suspend fun deleteLink(linkId: String) {
        requestUnit(path = "/v1/links/$linkId", method = "DELETE")
    }

    private suspend inline fun <reified T> request(
        path: String,
        method: String,
        bodyText: String? = null,
        includeCsrf: Boolean = true
    ): T {
        val headers = Headers().apply {
            append("Accept", "application/json")
            if (bodyText != null) {
                append("Content-Type", "application/json")
            }
            if (includeCsrf) {
                csrfTokenProvider()?.takeIf { it.isNotBlank() }?.let {
                    append(csrfHeaderName, it)
                }
            }
        }

        val requestInit = createRequestInit()
        requestInit.method = method
        requestInit.headers = headers
        requestInit.credentials = "include".toJsString().unsafeCast<RequestCredentials>()
        if (bodyText != null) {
            requestInit.body = bodyText.toJsString()
        }

        val response = window.fetch(
            "${baseUrl.trimEnd('/')}$path",
            requestInit
        ).await<Response>()
        val responseText = response.text().await<JsString>().toString()
        if (!response.ok) {
            throw ApiException(
                error = parseApiError(status = response.status.toInt(), bodyText = responseText),
                statusCode = HttpStatusCode.fromValue(response.status.toInt())
            )
        }
        return LinkStashJson.instance.decodeFromString(responseText)
    }

    private suspend fun requestUnit(
        path: String,
        method: String,
        bodyText: String? = null,
        includeCsrf: Boolean = true
    ) {
        val headers = Headers().apply {
            append("Accept", "application/json")
            if (bodyText != null) {
                append("Content-Type", "application/json")
            }
            if (includeCsrf) {
                csrfTokenProvider()?.takeIf { it.isNotBlank() }?.let {
                    append(csrfHeaderName, it)
                }
            }
        }

        val requestInit = createRequestInit()
        requestInit.method = method
        requestInit.headers = headers
        requestInit.credentials = "include".toJsString().unsafeCast<RequestCredentials>()
        if (bodyText != null) {
            requestInit.body = bodyText.toJsString()
        }

        val response = window.fetch(
            "${baseUrl.trimEnd('/')}$path",
            requestInit
        ).await<Response>()
        val responseText = response.text().await<JsString>().toString()
        if (!response.ok) {
            throw ApiException(
                error = parseApiError(status = response.status.toInt(), bodyText = responseText),
                statusCode = HttpStatusCode.fromValue(response.status.toInt())
            )
        }
    }
}

@JsFun("() => ({})")
private external fun createJsObject(): JsAny

private fun createRequestInit(): RequestInit = createJsObject().unsafeCast<RequestInit>()

private fun parseApiError(status: Int, bodyText: String): ApiError {
    if (bodyText.isBlank()) {
        return ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status $status"
        )
    }

    return runCatching {
        LinkStashJson.instance.decodeFromString<ApiErrorEnvelope>(bodyText).error
    }.recoverCatching {
        LinkStashJson.instance.decodeFromString<ApiError>(bodyText)
    }.getOrElse {
        ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status $status"
        )
    }
}

private fun Int.toApiErrorCode(): ApiErrorCode {
    return when (this) {
        401 -> ApiErrorCode.UNAUTHORIZED
        403 -> ApiErrorCode.FORBIDDEN
        404 -> ApiErrorCode.NOT_FOUND
        409 -> ApiErrorCode.CONFLICT
        422 -> ApiErrorCode.VALIDATION_ERROR
        429 -> ApiErrorCode.RATE_LIMITED
        in 500..599 -> ApiErrorCode.UPSTREAM_ERROR
        else -> ApiErrorCode.UNKNOWN
    }
}
