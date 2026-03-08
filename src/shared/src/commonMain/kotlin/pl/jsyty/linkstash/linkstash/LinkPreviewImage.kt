package pl.jsyty.linkstash.linkstash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun LinkPreviewImage(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
)
