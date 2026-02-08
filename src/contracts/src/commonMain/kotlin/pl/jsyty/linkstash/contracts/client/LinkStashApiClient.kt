package pl.jsyty.linkstash.contracts.client

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pl.jsyty.linkstash.contracts.LinkStashJson

data class LinkStashApiClientConfig(
    val baseUrl: String,
    val bearerTokenProvider: (() -> String?)? = null,
    val json: Json = LinkStashJson.instance
)

class LinkStashApiClient private constructor(
    val authApi: AuthApi,
    val spacesApi: SpacesApi,
    val linksApi: LinksApi,
    private val httpClient: HttpClient,
    private val ownsClient: Boolean
) {
    fun close() {
        if (ownsClient) {
            httpClient.close()
        }
    }

    companion object {
        fun create(
            config: LinkStashApiClientConfig,
            client: HttpClient? = null
        ): LinkStashApiClient {
            val managedClient = client ?: HttpClient {
                install(ContentNegotiation) {
                    json(config.json)
                }
                install(DefaultRequest) {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    config.bearerTokenProvider
                        ?.invoke()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { bearerAuth(it) }
                }
                HttpResponseValidator {
                    validateResponse { response ->
                        if (response.status.value !in 200..299) {
                            throw ApiException(
                                error = response.toApiError(config.json),
                                statusCode = response.status
                            )
                        }
                    }
                }
            }

            val ktorfit = Ktorfit.Builder()
                .baseUrl(config.baseUrl.withTrailingSlash())
                .httpClient(managedClient)
                .build()

            return LinkStashApiClient(
                authApi = ktorfit.createAuthApi(),
                spacesApi = ktorfit.createSpacesApi(),
                linksApi = ktorfit.createLinksApi(),
                httpClient = managedClient,
                ownsClient = client == null
            )
        }
    }
}

private fun String.withTrailingSlash(): String {
    return if (endsWith("/")) this else "$this/"
}
