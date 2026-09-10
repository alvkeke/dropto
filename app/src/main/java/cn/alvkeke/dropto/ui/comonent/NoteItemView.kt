package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.ui.comonent.NoteItemViewOld.Companion.dataFormatCommon
import cn.alvkeke.dropto.ui.comonent.NoteItemViewOld.Companion.dataFormatToday
import cn.alvkeke.dropto.ui.comonent.NoteItemViewOld.Companion.dataFormatWeekly
import java.util.Date


class NoteItemView(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
): AbstractComposeView(
    context, attrs, defStyleAttr
) {

    var note: NoteItem? = null

    @Composable
    override fun Content() {
        if (note != null) {
            NoteItemViewContent(note!!, context)
        } else {
            Text(
                text = "NO NOTE",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }

}


private val CONTENT_MARGIN_H = 12.dp
private val CONTENT_MARGIN_V = 6.dp
private val AVATAR_MARGIN_END = 8.dp
private val HEADER_MARGIN = 4.dp

@Composable
fun NoteItemViewContent(note: NoteItem, context: Context) {
    Row(
        modifier = Modifier.padding(
            start = CONTENT_MARGIN_H,
            end = CONTENT_MARGIN_H,
            top = CONTENT_MARGIN_V,
            bottom = CONTENT_MARGIN_V,
        )
    ) {
        NoteItemAvatar()
        Spacer(Modifier.width(AVATAR_MARGIN_END))
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                NoteItemSenderName(note.sender)
                NoteItemDate(note.createTime)
                NoteItemStatusIcon(note, context)
            }
            Spacer(Modifier.width(10.dp))
            NoteItemText(note.text)
            NoteItemMedia(note.medias)
            NoteItemFile(note.files)
        }
    }
}

@Composable
fun NoteItemAvatar() {
    val avatarSize = 40.dp
    Box {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_background),
            contentDescription = "sender",
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
        )
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "sender",
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
        )
    }
}

@Composable
fun NoteItemSenderName(sender: String?) {
    val senderText = sender ?: "You"
    Text(
        senderText,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
    )
}

private fun Long.format(): String {
    val noteDate = Date(this)
    val now = Date()
    val calNote = java.util.Calendar.getInstance().apply { time = noteDate }
    val calNow = java.util.Calendar.getInstance().apply { time = now }

    val sameYear = calNote.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR)
    val dayOfYearNote = calNote.get(java.util.Calendar.DAY_OF_YEAR)
    val dayOfYearNow = calNow.get(java.util.Calendar.DAY_OF_YEAR)
    val diffDays = dayOfYearNow - dayOfYearNote

    return when {
        sameYear && diffDays == 0 -> dataFormatToday.format(noteDate) // Today
        sameYear && diffDays == 1 -> "Yesterday " + dataFormatToday.format(noteDate)
        sameYear && diffDays in 2..6 -> dataFormatWeekly.format(noteDate)
        else -> dataFormatCommon.format(noteDate)
    }
}

@Composable
fun NoteItemDate(timeStamp: Long) {
    Text(
        text = timeStamp.format(),
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = HEADER_MARGIN)
    )
}


private fun cachedStatusIcon(context: Context, resId: Int, tintColor: Int): Bitmap {
    return ImageLoader.loadDrawable(context, resId, tintColor)
}

@Composable
fun NoteItemStatusIcon(note: NoteItem, context: Context) {
    val color = Color.Gray.toArgb()
    val icons = buildList {
        if (note.isDeleted) {
            add(cachedStatusIcon(context, R.drawable.icon_common_remove, color))
        }
        if (!note.isSynced) {
            add(cachedStatusIcon(context, R.drawable.icon_common_not_sync, color))
        }
        if (note.isEdited) {
            add(cachedStatusIcon(context, R.drawable.icon_common_edit, color))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = HEADER_MARGIN)
    ) {
        icons.forEach {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "",
                modifier = Modifier
                    .padding(start = HEADER_MARGIN)
                    .size(14.dp)
            )
        }
    }
}

@Composable
fun NoteItemText(text: String) {
    if (text.isNotEmpty())
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
}

@Composable
fun NoteItemMedia(medias: ArrayList<AttachmentFile>) {
    if (medias.isEmpty()) return

    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        medias.forEachIndexed { index, media ->
            MediaTile(media, index)
        }
    }
}

@Composable
private fun MediaTile(
    file: AttachmentFile,
    index: Int,
) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        if (file.isImage) {
            val b: Bitmap = ImageLoader.loadImage(file.md5file) ?: ImageLoader.errorBitmap
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val b = ImageLoader.loadVideoThumbnail(file.md5file) ?: ImageLoader.errorBitmap
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // TODO: video overlay
        }
    }
}

@Composable
fun NoteItemFile(files: ArrayList<AttachmentFile>) {
    if (files.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        files.forEachIndexed { index, file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = colorResource(R.color.color_action_bar),
                        RoundedCornerShape(8.dp),
                    )
                    .border(
                        1.dp,
                        colorResource(R.color.note_bubble_shadow),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorResource(R.color.file_icon_background)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↗",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (file.mimeType.isNotEmpty()) {
                        Text(
                            text = file.mimeType,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
