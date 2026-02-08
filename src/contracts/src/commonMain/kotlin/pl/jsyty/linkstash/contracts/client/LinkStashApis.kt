package pl.jsyty.linkstash.contracts.client

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import pl.jsyty.linkstash.contracts.auth.AuthExchangeRequest
import pl.jsyty.linkstash.contracts.auth.AuthStartResponse
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse

interface AuthApi {
    @GET("auth/start")
    suspend fun start(): AuthStartResponse

    @POST("auth/exchange")
    suspend fun exchange(@Body request: AuthExchangeRequest): Unit
}

interface SpacesApi {
    @GET("spaces")
    suspend fun list(): SpacesListResponse

    @POST("spaces")
    suspend fun create(@Body request: SpaceCreateRequest): SpaceDto

    @PATCH("spaces/{spaceId}")
    suspend fun rename(
        @Path("spaceId") spaceId: String,
        @Body request: SpaceRenameRequest
    ): SpaceDto

    @DELETE("spaces/{spaceId}")
    suspend fun delete(@Path("spaceId") spaceId: String): Unit
}

interface LinksApi {
    @GET("links")
    suspend fun list(@Query("cursor") cursor: String? = null): LinksListResponse

    @POST("links")
    suspend fun create(@Body request: LinkCreateRequest): LinkDto

    @PATCH("links/{linkId}/move")
    suspend fun move(
        @Path("linkId") linkId: String,
        @Body request: LinkMoveRequest
    ): LinkDto

    @DELETE("links/{linkId}")
    suspend fun delete(@Path("linkId") linkId: String): Unit
}
