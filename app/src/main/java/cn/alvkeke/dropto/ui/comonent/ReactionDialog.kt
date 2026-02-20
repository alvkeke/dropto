package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import cn.alvkeke.dropto.R

class ReactionDialog(
    val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PopupWindow(context, attrs, defStyleAttr) {

    companion object {
        const val MARGIN_REACTION_DP = 16
        const val RADIUS_BACKGROUND_DP = 24
        const val MAX_WIDTH_DP = 320
        const val TEXT_SIZE_SP = 20
    }

    val reactionList = mutableListOf<String>()
    var maxWidthPx: Int = context.resources.displayMetrics.density.times(MAX_WIDTH_DP).toInt()
        set(value) {
            field = value
            scrollView.layoutParams = scrollView.layoutParams.apply {
                width = value
            }
        }

    private val scrollView: HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        layoutParams = ViewGroup.LayoutParams(maxWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        setPadding(0, 0, 0, 0)
    }
    private val linearLayout: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, 0)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    init {
        scrollView.addView(linearLayout)

        val bg = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.popup_menu_background))
            cornerRadius = context.resources.displayMetrics.density * RADIUS_BACKGROUND_DP
        }
        scrollView.background = bg
        scrollView.clipToOutline = true

        contentView = scrollView
        isOutsideTouchable = true
    }

    var onReactionClick: ((String) -> Unit)? = null
    fun setReactions(reactions: List<String>) {
        reactionList.clear()
        reactionList.addAll(reactions)
        linearLayout.removeAllViews()
        val marginPx = context.resources.displayMetrics.density.times(MARGIN_REACTION_DP).toInt()
        reactions.forEach { reaction ->
            val tv = TextView(context).apply {
                text = reaction
                setTextColor(ContextCompat.getColor(context, R.color.color_text_main))
                textSize = TEXT_SIZE_SP.toFloat()
                background = null
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(marginPx, 0, 0, 0)
                }
                gravity = Gravity.CENTER
            }
            tv.setOnClickListener {
                onReactionClick?.invoke(reaction)
                this@ReactionDialog.dismiss()
            }
            linearLayout.addView(tv)
        }
        val marginView = View(context).apply {
            background = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginPx, 0, 0, 0)
            }
        }
        linearLayout.addView(marginView)
    }

    fun setMaxWidth(width: Int) {
        scrollView.layoutParams = scrollView.layoutParams.apply {
            this.width = width
        }
    }

}