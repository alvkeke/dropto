package cn.alvkeke.dropto.ui.component.NoteItemChild

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import cn.alvkeke.dropto.R

class MediaItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val thumbnailView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = context.getString(R.string.content_description_image_content)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val playView = ImageView(context).apply {
        val iconSize = resources.getDimensionPixelSize(R.dimen.size_note_item_media_play_icon)
        val iconPadding = iconSize / 4

        setImageResource(R.drawable.icon_common_video_play)
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.video_play_icon_foreground))
        contentDescription =
            context.getString(R.string.content_description_note_item_media_play_icon)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.video_play_icon_background))
        }
        layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
        setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        isVisible = false
    }

    init {
        val cardRadius = resources.getDimension(R.dimen.radius_note_item_media)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cardRadius)
            }
        }
        clipToOutline = true

        addView(thumbnailView)
        addView(playView)
    }

    var boundPath: String = ""

    var thumbnail: Bitmap? = null
        set(value) {
            field = value
            thumbnailView.setImageBitmap(value)
        }

    var isVideo: Boolean = false
        set(value) {
            field = value
            playView.isVisible = value
        }
}
