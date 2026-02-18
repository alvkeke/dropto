package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import cn.alvkeke.dropto.R

class MgmtItemView(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private fun Int.dp(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private val imageView: ImageView = ImageView(context)
    private val textView: TextView = TextView(context)
    private val margin = 8.dp()
    private val iconSize = 48.dp()

    init {
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        addView(imageView)

        textView.textSize = 20f
        textView.setTextColor(ContextCompat.getColor(context, R.color.color_text_main))
        addView(textView)

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val iconSpec = MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY)
        imageView.measure(iconSpec, iconSpec)
        measureChildWithMargins(
            textView,
            widthMeasureSpec,
            0,
            heightMeasureSpec,
            0
        )

        val textWidth = textView.measuredWidth
        val textHeight = textView.measuredHeight

        val width = margin + iconSize + margin + textWidth + margin
        val height = maxOf(iconSize + margin * 2, textHeight + margin * 2)

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val viewHeight = b - t

        // icon
        val iconLeft = margin
        val iconTop = margin
        imageView.layout(
            iconLeft,
            iconTop,
            iconLeft + iconSize,
            iconTop + iconSize
        )

        // textView
        val textLeft = margin + iconSize + margin
        val textTop = (viewHeight - textView.measuredHeight) / 2
        textView.layout(
            textLeft,
            textTop,
            textLeft + textView.measuredWidth,
            textTop + textView.measuredHeight
        )
    }

    fun setIcon(drawable: Drawable) {
        imageView.setImageDrawable(drawable)
        requestLayout()
    }
    fun setIcon(resId: Int) {
        imageView.setImageResource(resId)
        requestLayout()
    }

    fun setTitle(text: CharSequence) {
        textView.text = text
        requestLayout()
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }
    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }
    override fun checkLayoutParams(p: LayoutParams?): Boolean {
        return p is MarginLayoutParams
    }
}