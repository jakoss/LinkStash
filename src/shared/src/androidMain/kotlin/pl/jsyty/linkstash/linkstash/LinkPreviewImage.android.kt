package pl.jsyty.linkstash.linkstash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.skydoves.landscapist.ImageOptions
import com.skydoves.landscapist.crossfade.CrossfadePlugin
import com.skydoves.landscapist.components.rememberImageComponent
import com.skydoves.landscapist.image.LandscapistImage

@Composable
actual fun LinkPreviewImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier
) {
    LandscapistImage(
        imageModel = { imageUrl },
        modifier = modifier,
        component = rememberImageComponent {
            +CrossfadePlugin()
        },
        imageOptions = ImageOptions(
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            placeholderAspectRatio = 16f / 9f
        )
    )
}
