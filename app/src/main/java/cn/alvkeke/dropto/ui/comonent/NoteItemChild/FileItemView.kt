package cn.alvkeke.dropto.ui.comonent.NoteItemChild

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.alvkeke.dropto.R

class FileItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

    var fileName: String by mutableStateOf("")

    @Composable
    override fun Content() {
        val iconSize = dimensionResource(R.dimen.size_note_item_file_icon)
        val iconPadding = dimensionResource(R.dimen.padding_note_item_file_icon)
        val iconRadius = dimensionResource(R.dimen.radius_note_item_file_icon)
        val margin = dimensionResource(R.dimen.margin_note_item_element)

        val cardShape = RoundedCornerShape(dimensionResource(R.dimen.radius_note_item_file))
        val cardPadding = dimensionResource(R.dimen.padding_note_item_file)
        val cardSpacing = dimensionResource(R.dimen.margin_note_item_file)
        val cardBorderColor = colorResource(R.color.color_text_sub).copy(CARD_BORDER_ALPHA)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = cardSpacing)
                .background(colorResource(R.color.note_bubble_background), cardShape)
                .border(CARD_BORDER_WIDTH, cardBorderColor, cardShape)
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
        private val CARD_BORDER_WIDTH = 1.dp
        private const val CARD_BORDER_ALPHA = 0.3f
    }
}