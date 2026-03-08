package pl.jsyty.linkstash.server.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaindropPreviewMetadataTest {
    @Test
    fun normalizedPreviewImageUrlPrefersCoverThenFallsBackToFirstImageMedia() {
        val preferredCoverPayload = RaindropRaindropPayload(
            cover = " https://images.example.com/cover.jpg ",
            media = listOf(
                RaindropMediaPayload(type = "image", link = "https://images.example.com/media.jpg")
            )
        )
        val mediaFallbackPayload = RaindropRaindropPayload(
            cover = " ",
            media = listOf(
                RaindropMediaPayload(type = "video", link = "https://images.example.com/video.jpg"),
                RaindropMediaPayload(type = "image", link = " "),
                RaindropMediaPayload(type = "image", link = "https://images.example.com/fallback.jpg"),
                RaindropMediaPayload(type = "image", link = "https://images.example.com/ignored.jpg")
            )
        )
        val noPreviewPayload = RaindropRaindropPayload(
            media = listOf(
                RaindropMediaPayload(type = "video", link = "https://images.example.com/video.jpg"),
                RaindropMediaPayload(type = "image", link = " ")
            )
        )

        assertEquals(
            "https://images.example.com/cover.jpg",
            preferredCoverPayload.normalizedPreviewImageUrl()
        )
        assertEquals(
            "https://images.example.com/fallback.jpg",
            mediaFallbackPayload.normalizedPreviewImageUrl()
        )
        assertNull(noPreviewPayload.normalizedPreviewImageUrl())
    }
}
