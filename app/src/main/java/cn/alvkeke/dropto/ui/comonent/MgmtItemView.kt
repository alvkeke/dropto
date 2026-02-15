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
        imageView.layoutParams.height = iconSize
        imageView.layoutParams.width = iconSize
        addView(imageView)

        textView.textSize = 20f
        textView.setTextColor(ContextCompat.getColor(context, R.color.color_text_main))
        addView(textView)

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val iconSpec = MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY)
        imageView.measure(iconSpec, iconSpec)
        measureChildWithMargins(textView, widthMeasureSpec, 0, heightMeasureSpec, 0)

        val imageWidth = iconSize
        val imageHeight = iconSize
        val textWidth = textView.measuredWidth
        val textHeight = textView.measuredHeight

        val width = imageWidth + margin + textWidth + margin * 2
        val height = maxOf(imageHeight, textHeight) + margin * 2

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val parentLeft = paddingLeft
        val parentTop = paddingTop
        val parentBottom = b - t - paddingBottom

        val imageWidth = iconSize
        val imageHeight = iconSize
        val textWidth = textView.measuredWidth
        val textHeight = textView.measuredHeight

        val totalHeight = maxOf(imageHeight, textHeight)
        val topOffset = parentTop + (parentBottom - parentTop - totalHeight) / 2

        val imageTop = topOffset + (totalHeight - imageHeight) / 2
        imageView.layout(
            parentLeft,
            imageTop,
            parentLeft + imageWidth + margin,
            imageTop + imageHeight + margin
        )

        val textLeft = parentLeft + imageWidth + margin
        val textTop = topOffset + (totalHeight - textHeight) / 2
        textView.layout(
            textLeft,
            textTop,
            textLeft + textWidth,
            textTop + textHeight
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