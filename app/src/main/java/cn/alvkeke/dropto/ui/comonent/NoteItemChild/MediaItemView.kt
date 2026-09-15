package cn.alvkeke.dropto.ui.comonent.NoteItemChild

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import cn.alvkeke.dropto.R

class MediaItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

    var boundPath: String = ""

    var thumbnail: ImageBitmap? by mutableStateOf(null)
    var isVideo: Boolean by mutableStateOf(false)

    @Composable
    override fun Content() {
        val image = thumbnail

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(dimensionResource(R.dimen.radius_note_item_media))),
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.content_description_image_content),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (isVideo) {
                val iconSize = dimensionResource(R.dimen.size_note_item_media_play_icon)
                Image(
                    painter = painterResource(R.drawable.icon_common_video_play),
                    contentDescription = stringResource(
                        R.string.content_description_note_item_media_play_icon
                    ),
                    colorFilter = ColorFilter.tint(
                        colorResource(R.color.video_play_icon_foreground)
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(iconSize)
                        .background(colorResource(R.color.video_play_icon_background), CircleShape)
                        .padding(iconSize / 4),
                )
            }
        }
    }
}