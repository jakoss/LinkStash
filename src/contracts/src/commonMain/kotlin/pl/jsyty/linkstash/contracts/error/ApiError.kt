package pl.jsyty.linkstash.contracts.error

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiErrorEnvelope(
    val error: ApiError
)

@Serializable
data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val details: JsonObject? = null
)

@Serializable
enum class ApiErrorCode {
    @SerialName("unauthorized")
    UNAUTHORIZED,

    @SerialName("forbidden")
    FORBIDDEN,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("validation_error")
    VALIDATION_ERROR,

    @SerialName("conflict")
    CONFLICT,

    @SerialName("rate_limited")
    RATE_LIMITED,

    @SerialName("upstream_error")
    UPSTREAM_ERROR,

    @SerialName("internal_error")
    INTERNAL_ERROR,

    @SerialName("unknown")
    UNKNOWN
}
