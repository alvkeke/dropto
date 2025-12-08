package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.createBitmap


const val TAG: String = "CountableImageButton"

class CountableImageButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs){

    private var originalImage: Drawable? = null
    private var bitmap: Bitmap? = null
    private var countNumber: Int = 0

    init {
        Log.e(TAG, "init: $attrs")
        attrs?.let {
            context.withStyledAttributes(it, intArrayOf(android.R.attr.src)) {
                val drawable = getDrawable(0)
                originalImage = drawable
                Log.d("TAG", "drawable class: ${drawable?.javaClass?.name}")
                refreshImage()
            }
        }
    }

    private fun refreshImage() {
        originalImage?.let { d -> bitmap = getBitmapFromDrawable(d)}
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.e(TAG, "onSizeChanged: $w, $h, $oldw, $oldh")
        refreshImage()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.e(TAG, "onDetachedFromWindow: ")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        Log.d(TAG, "onDraw: BTN draw $bitmap")
        bitmap?.let {
            val left = (width - it.width) / 2f
            val top = (height - it.height) / 2f
            canvas.drawBitmap(it, left, top, null)

            // draw the count in right-bottom corner in 1/2
            val numLeft = width / 2f
            val numTop = height / 2f
            val numSize = (width / 2f).coerceAtMost(height / 2f)
            Log.e(this.toString(), "onDraw: draw count $countNumber at $numLeft, $numTop, size $numSize")
            drawCountNumber(canvas, numLeft, numTop, numSize)
        }
    }

    private fun drawCountNumber(canvas: Canvas, startX: Float, startY: Float, size: Float) {
        if (countNumber < 1) return

        val text = if (countNumber < 10) {
            countNumber.toString()
        } else {
            "9+"
        }

        // Calculate circle center and radius
        val centerX = startX + size / 2f
        val centerY = startY + size / 2f
        var radius = size / 2f

        val circleAlpha = 150
        // Draw circle background
        val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.RED
            alpha = circleAlpha // Add transparency (0-255, where 255 is fully opaque)
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Draw white border around the circle
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            alpha = circleAlpha
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = size * 0.08f
            radius -= strokeWidth / 2
        }
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // Draw the number text
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            style = android.graphics.Paint.Style.FILL
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Set text size to fit within the circle (approximately 60% of diameter)
        textPaint.textSize = size * 0.6f
        val textWidth = textPaint.measureText(text)
        var fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        if (textWidth <= 0f || textHeight <= 0f) {
            Log.e(TAG, "drawCountNumber: invalid text dimensions: $textWidth x $textHeight")
            return
        }

        // Scale down if needed to fit in circle (80% of diameter for safety margin)
        val maxDimension = size * 0.8f
        val scale = minOf(maxDimension / textWidth, maxDimension / textHeight)
        if (scale < 1f) {
            textPaint.textSize *= scale
            fm = textPaint.fontMetrics
        }

        // Draw text centered in the circle
        val baseline = centerY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, centerX, baseline, textPaint)
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        // force the size if width and height are provided
        val w = if (width > 0) width else drawable.intrinsicWidth
        val h = if (height > 0) height else drawable.intrinsicHeight

        assert(w != 0 && h != 0)

        if (drawable is BitmapDrawable) {
            drawable.bitmap?.let {
                if (it.width == w && it.height == h) {
                    return it
                }
            }
        }

        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun setCount(number: Int) {
        countNumber = number
        invalidate()
    }

}