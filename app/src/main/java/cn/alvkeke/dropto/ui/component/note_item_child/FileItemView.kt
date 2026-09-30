package cn.alvkeke.dropto.ui.component.note_item_child

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cn.alvkeke.dropto.R

class FileItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val nameView = TextView(context).apply {
        setTextColor(context.getColor(R.color.color_text_main))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, FILE_NAME_TEXT_SIZE)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.margin_note_item_element)
        }
    }

    init {
        val cardPadding = resources.getDimensionPixelSize(R.dimen.padding_note_item_file)
        val iconSize = resources.getDimensionPixelSize(R.dimen.size_note_item_file_icon)
        val iconPadding = resources.getDimensionPixelSize(R.dimen.padding_note_item_file_icon)

        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
        background = roundedRect(R.color.note_bubble_background, R.dimen.radius_note_item_file)

        val iconBox = FrameLayout(context).apply {
            layoutParams = LayoutParams(iconSize, iconSize)
            background =
                roundedRect(R.color.file_icon_background, R.dimen.radius_note_item_file_icon)
        }
        iconBox.addView(ImageView(context).apply {
            setImageResource(R.drawable.icon_common_file)
            imageTintList = ColorStateList.valueOf(context.getColor(R.color.color_text_main))
            contentDescription = context.getString(R.string.description_of_note_item_file_icon)
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
            )
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
        })

        addView(iconBox)
        addView(nameView)
    }

    var fileName: String
        get() = nameView.text.toString()
        set(value) {
            nameView.text = value
        }

    private fun roundedRect(colorId: Int, radiusId: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(context.getColor(colorId))
            cornerRadius = resources.getDimension(radiusId)
        }
    }

    companion object {
        private const val FILE_NAME_TEXT_SIZE = 14f    // in sp
    }
}
