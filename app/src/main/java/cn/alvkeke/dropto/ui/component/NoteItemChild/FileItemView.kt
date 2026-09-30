package cn.alvkeke.dropto.ui.component.NoteItemChild

import android.content.Context
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import cn.alvkeke.dropto.R

class FileItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

    init {
        val cardRadius = resources.getDimension(R.dimen.radius_note_item_file)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cardRadius)
            }
        }
    }

    var fileName: String by mutableStateOf("")

    @Composable
    override fun Content() {
        val iconSize = dimensionResource(R.dimen.size_note_item_file_icon)
        val iconPadding = dimensionResource(R.dimen.padding_note_item_file_icon)
        val iconRadius = dimensionResource(R.dimen.radius_note_item_file_icon)
        val margin = dimensionResource(R.dimen.margin_note_item_element)

        val cardShape = RoundedCornerShape(dimensionResource(R.dimen.radius_note_item_file))
        val cardPadding = dimensionResource(R.dimen.padding_note_item_file)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorResource(R.color.note_bubble_background), cardShape)
                .padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .background(
                        color = colorResource(R.color.file_icon_background),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(iconRadius),
                    ),
            ) {
                Image(
                    painter = painterResource(R.drawable.icon_common_file),
                    contentDescription = stringResource(
                        R.string.description_of_note_item_file_icon
                    ),
                    colorFilter = ColorFilter.tint(colorResource(R.color.color_text_main)),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(iconPadding),
                )
            }

            Text(
                text = fileName,
                color = colorResource(R.color.color_text_main),
                fontSize = FILE_NAME_TEXT_SIZE,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = margin),
            )
        }
    }

    companion object {
        private val FILE_NAME_TEXT_SIZE = 14.sp
    }
}