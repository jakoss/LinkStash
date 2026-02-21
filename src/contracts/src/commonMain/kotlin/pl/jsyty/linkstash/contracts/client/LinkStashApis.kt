package pl.jsyty.linkstash.contracts.client

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.PATCH
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import pl.jsyty.linkstash.contracts.auth.AuthExchangeResponse
import pl.jsyty.linkstash.contracts.auth.AuthRaindropTokenExchangeRequest
import pl.jsyty.linkstash.contracts.link.LinkCreateRequest
import pl.jsyty.linkstash.contracts.link.LinkDto
import pl.jsyty.linkstash.contracts.link.LinkMoveRequest
import pl.jsyty.linkstash.contracts.link.LinksListResponse
import pl.jsyty.linkstash.contracts.space.SpaceCreateRequest
import pl.jsyty.linkstash.contracts.space.SpaceDto
import pl.jsyty.linkstash.contracts.space.SpaceRenameRequest
import pl.jsyty.linkstash.contracts.space.SpacesListResponse
import pl.jsyty.linkstash.contracts.user.UserDto

interface AuthApi {
    @POST("auth/raindrop/token")
    suspend fun exchangeRaindropToken(@Body request: AuthRaindropTokenExchangeRequest): AuthExchangeResponse

    @POST("auth/logout")
    suspend fun logout(): Unit

    @GET("me")
    suspend fun me(): UserDto
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
    @GET("spaces/{spaceId}/links")
    suspend fun list(
        @Path("spaceId") spaceId: String,
        @Query("cursor") cursor: String? = null
    ): LinksListResponse

    @POST("spaces/{spaceId}/links")
    suspend fun create(
        @Path("spaceId") spaceId: String,
        @Body request: LinkCreateRequest
    ): LinkDto

    @PATCH("links/{linkId}")
    suspend fun move(
        @Path("linkId") linkId: String,
        @Body request: LinkMoveRequest
    ): LinkDto

    @DELETE("links/{linkId}")
    suspend fun delete(@Path("linkId") linkId: String): Unit
}
