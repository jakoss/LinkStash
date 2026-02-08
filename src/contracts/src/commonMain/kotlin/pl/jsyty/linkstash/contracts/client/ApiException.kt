package pl.jsyty.linkstash.contracts.client

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.jsyty.linkstash.contracts.LinkStashJson
import pl.jsyty.linkstash.contracts.error.ApiError
import pl.jsyty.linkstash.contracts.error.ApiErrorCode
import pl.jsyty.linkstash.contracts.error.ApiErrorEnvelope

class ApiException(
    val error: ApiError,
    val statusCode: HttpStatusCode
) : RuntimeException(error.message)

suspend fun HttpResponse.toApiError(json: Json = LinkStashJson.instance): ApiError {
    val bodyText = bodyAsText()

    if (bodyText.isBlank()) {
        return ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status ${status.value}",
            details = buildJsonObject {
                put("status", status.value)
            }
        )
    }

    return runCatching {
        json.decodeFromString(ApiErrorEnvelope.serializer(), bodyText).error
    }.recoverCatching {
        json.decodeFromString(ApiError.serializer(), bodyText)
    }.getOrElse {
        ApiError(
            code = status.toApiErrorCode(),
            message = "Request failed with status ${status.value}",
            details = buildJsonObject {
                put("status", status.value)
                put("body", bodyText)
            }
        )
    }
}

private fun HttpStatusCode.toApiErrorCode(): ApiErrorCode {
    return when (value) {
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
