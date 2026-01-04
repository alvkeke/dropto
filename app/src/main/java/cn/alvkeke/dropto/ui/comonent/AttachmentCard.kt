package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withTranslation
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader

@SuppressLint("ViewConstructor")
class AttachmentCard @JvmOverloads constructor(
    context: Context, val attachment: AttachmentFile, var listener: CardListener, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = context.resources.displayMetrics.density
    private fun Int.dp(): Int = (this * density).toInt()

    private enum class ContentType {
        IMAGE,
        VIDEO,
        FILE,
    }
    private var contentType = when (attachment.type) {
        AttachmentFile.Type.MEDIA -> when (attachment.mimeType.substringBefore('/')) {
            "image" -> ContentType.IMAGE
            "video" -> ContentType.VIDEO
            else -> ContentType.FILE
        }
        AttachmentFile.Type.FILE -> ContentType.FILE
    }

    private var contentRect = RectF()   // updated in measureHeight
    private var contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
    }
    private var bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var cachedBitmap: Bitmap? = null
    private var srcRect: Rect? = null
    private val dstRect = RectF()

    // Helper function to calculate center crop src rect
    private fun calculateCenterCropSrcRect(bitmap: Bitmap, dstRect: RectF) {
        val bmpRatio = bitmap.width.toFloat() / bitmap.height
        val dstRatio = dstRect.width() / dstRect.height()
        if (srcRect == null) srcRect = Rect()

        if (bmpRatio > dstRatio) {
            // Bitmap is wider, crop width
            val srcWidth = (bitmap.height * dstRatio).toInt()
            val srcLeft = (bitmap.width - srcWidth) / 2
            srcRect!!.set(srcLeft, 0, srcLeft + srcWidth, bitmap.height)
        } else {
            // Bitmap is taller, crop height
            val srcHeight = (bitmap.width / dstRatio).toInt()
            val srcTop = (bitmap.height - srcHeight) / 2
            srcRect!!.set(0, srcTop, bitmap.width, srcTop + srcHeight)
        }
    }

    private fun loadImageBitmap() {
        ImageLoader.loadImageAsync(attachment.md5file) { bitmap ->
            cachedBitmap = bitmap ?: ImageLoader.errorBitmap
            calculateCenterCropSrcRect(cachedBitmap!!, contentRect)
            dstRect.set(contentRect)
            invalidate()
        }
    }

    private fun loadVideoBitmap() {
        ImageLoader.loadVideoThumbnailAsync(attachment.md5file) { bitmap ->
            cachedBitmap = bitmap ?: ImageLoader.errorBitmap
            calculateCenterCropSrcRect(cachedBitmap!!, contentRect)
            dstRect.set(contentRect)
            invalidate()
        }
    }

    private fun loadFileBitmap() {
        cachedBitmap = ImageLoader.iconFile
        srcRect = null
        dstRect.set(
            contentRect.left,
            contentRect.top,
            contentRect.left + contentRect.height(),
            contentRect.bottom
        )
        invalidate()
    }

    private val contentPath = Path()
    private fun drawCachedBitmap(canvas: Canvas) {
        if (cachedBitmap == null) return

        if (contentType == ContentType.FILE) {
            canvas.drawRoundRect(contentRect,
                RADIUS_CARD_CONTENT.toFloat(),
                RADIUS_CARD_CONTENT.toFloat(),
                contentPaint
            )
        }

        val saveCount = canvas.saveLayer(dstRect, null)
        // Draw rounded rect as mask
        contentPath.reset()
        contentPath.addRoundRect(
            contentRect,
            RADIUS_CARD_CONTENT.toFloat(),
            RADIUS_CARD_CONTENT.toFloat(),
            Path.Direction.CW
        )
        canvas.drawPath(contentPath, bitmapPaint)

        // Set xfermode to only draw bitmap where the rounded rect was drawn
        val oldMode = bitmapPaint.xfermode
        bitmapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(cachedBitmap!!, srcRect, dstRect, bitmapPaint)
        bitmapPaint.xfermode = oldMode
        canvas.restoreToCount(saveCount)
    }

    private val infoTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14.dp().toFloat()
    }
    private val infoBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 180
    }
    private val videoIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val videoIconPath = Path()
    private val infoBackRect = RectF()
    private fun drawAttachmentInfo(canvas: Canvas) {
        val info = attachment.name

        when (contentType) {
            ContentType.FILE -> {
                val infoWidth = (contentRect.width() - dstRect.width() - MARGIN_CARD_INFO * 2).toInt()
                val textStatic = StaticLayout.Builder.obtain(
                    info, 0, info.length,
                    infoTextPaint, infoWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()

                val x = dstRect.right + MARGIN_CARD_INFO
                val y = dstRect.top + MARGIN_CARD_INFO
                canvas.withTranslation(x, y) {
                    textStatic.draw(canvas)
                }
            }
            else -> {
                val textStatic = StaticLayout.Builder.obtain(
                    info, 0, info.length,
                    infoTextPaint,
                    (contentRect.width() - MARGIN_CARD_INFO * 2).toInt())
                    .setMaxLines(2)
                    .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
                infoBackRect.set(
                    contentRect.left,
                    contentRect.bottom - textStatic.height - MARGIN_CARD_INFO * 2,
                    contentRect.right,
                    contentRect.bottom
                )
                canvas.drawRoundRect(
                    infoBackRect,
                    RADIUS_CARD_CONTENT.toFloat(),
                    RADIUS_CARD_CONTENT.toFloat(),
                    infoBackPaint
                )

                if (contentType == ContentType.VIDEO) {
                    // draw play icon at center
                    val dst = contentRect
                    val iconSize = (dst.height() / 3).toInt().coerceAtMost(40.dp())

                    val iconLeft = dst.left + (dst.width() - iconSize) / 2
                    val iconTop = dst.top + (dst.height() - infoBackRect.height() - iconSize) / 2
                    val iconRect = RectF(
                        iconLeft,
                        iconTop,
                        iconLeft + iconSize,
                        iconTop + iconSize
                    )
                    val radius = iconSize / 2f
                    val cx = iconRect.centerX()
                    val cy = iconRect.centerY()
                    videoIconPaint.style = Paint.Style.FILL
                    videoIconPaint.color = Color.BLACK
                    videoIconPaint.alpha = 100
                    canvas.drawCircle(cx, cy, radius, videoIconPaint)

                    videoIconPaint.style = Paint.Style.STROKE
                    videoIconPaint.alpha = 255
                    videoIconPaint.color = Color.WHITE
                    videoIconPaint.strokeWidth = (iconSize / 6f).coerceAtMost(6f)
                    canvas.drawCircle(cx, cy, radius, videoIconPaint)
                    videoIconPaint.style = Paint.Style.FILL

                    videoIconPath.reset()
                    videoIconPath.moveTo(cx - radius / 3f, cy - radius / 2f)
                    videoIconPath.lineTo(cx - radius / 3f, cy + radius / 2f)
                    videoIconPath.lineTo(cx + radius * 2f / 3f, cy)
                    videoIconPath.close()
                    canvas.drawPath(videoIconPath, videoIconPaint)
                }

                canvas.withTranslation(
                    infoBackRect.left + MARGIN_CARD_INFO,
                    infoBackRect.top + MARGIN_CARD_INFO) {
                    textStatic.draw(canvas)
                }
            }
        }
    }

    private var removeButtonRadius = 0f
    private var removeButtonCx = 0f
    private var removeButtonCy = 0f
    private val removeButtonBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val removeButtonCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private fun drawCardOverlay(canvas: Canvas) {
        removeButtonRadius = contentRect.width() / 25
        val offset = removeButtonRadius * 4f / 5
        removeButtonCx = contentRect.left + offset
        removeButtonCy = contentRect.top + offset
        canvas.drawCircle(removeButtonCx, removeButtonCy, removeButtonRadius, removeButtonBackPaint)
        // Draw a white cross (X) inside the circle

        val crossLen = removeButtonRadius
        removeButtonCrossPaint.strokeWidth = removeButtonRadius / 2.5f
        // Line from top-left to bottom-right
        canvas.drawLine(
            removeButtonCx - crossLen / 2,
            removeButtonCy - crossLen / 2,
            removeButtonCx + crossLen / 2,
            removeButtonCy + crossLen / 2,
            removeButtonCrossPaint
        )
        // Line from bottom-left to top-right
        canvas.drawLine(
            removeButtonCx - crossLen / 2,
            removeButtonCy + crossLen / 2,
            removeButtonCx + crossLen / 2,
            removeButtonCy - crossLen / 2,
            removeButtonCrossPaint
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (cachedBitmap != null) {
            drawCachedBitmap(canvas)
            drawAttachmentInfo(canvas)
            drawCardOverlay(canvas)
            return
        }

        when (contentType) {
            ContentType.IMAGE -> loadImageBitmap()
            ContentType.VIDEO -> loadVideoBitmap()
            ContentType.FILE -> loadFileBitmap()
        }
    }

    private val marginCardContentX = MARGIN_CARD_CONTENT_X.dp()
    private val marginCardContentY = MARGIN_CARD_CONTENT_Y.dp()

    private fun measureHeight(width: Int): Int {
        val contentHeight = when (contentType) {
            ContentType.IMAGE, ContentType.VIDEO -> width / 2
            ContentType.FILE -> width / 4
        }

        contentRect.set(
            marginCardContentX.toFloat(),
            marginCardContentY.toFloat(),
            (width - marginCardContentX).toFloat(),
            (marginCardContentY + contentHeight).toFloat()
        )

        return (marginCardContentY * 2 + contentHeight)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val desiredWidth = 200
        val width: Int = when (widthMode) {
            MeasureSpec.AT_MOST -> desiredWidth.coerceAtMost(widthSize)
            MeasureSpec.UNSPECIFIED -> desiredWidth
            MeasureSpec.EXACTLY -> widthSize
            else -> 0
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val height: Int = when (heightMode) {
            MeasureSpec.AT_MOST -> measureHeight(width).coerceAtMost(heightSize)
            MeasureSpec.UNSPECIFIED -> measureHeight(width)
            MeasureSpec.EXACTLY -> heightSize
            else -> 0
        }

        setMeasuredDimension(width, height)
    }

    interface CardListener {
        fun onRemove(card: AttachmentCard, attachment: AttachmentFile)
        fun onClick(card: AttachmentCard, attachment: AttachmentFile)
        fun onLongClick(card: AttachmentCard, attachment: AttachmentFile)
    }

    private var downX = 0f
    private var downY = 0f
    private var longClickFired = false
    private val longClickTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longClickHandler = Handler(Looper.getMainLooper())
    private val longClickRunnable = Runnable {
        longClickFired = true
        longClickAt()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longClickFired = false
                longClickHandler.postDelayed(longClickRunnable, longClickTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    longClickHandler.removeCallbacks(longClickRunnable)
                }
            }
            MotionEvent.ACTION_UP -> {
                longClickHandler.removeCallbacks(longClickRunnable)
                if (!longClickFired) {
                    clickAt(event.x.toInt(), event.y.toInt())
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                longClickHandler.removeCallbacks(longClickRunnable)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun isPointInRemoveButton(x: Int, y: Int): Boolean {
        val dx = x - removeButtonCx
        val dy = y - removeButtonCy
        return dx * dx + dy * dy <= removeButtonRadius * removeButtonRadius
    }

    private fun clickAt(x: Int, y: Int) {
        performClick()
        if (isPointInRemoveButton(x, y)) {
            listener.onRemove(this, attachment)
            return
        }
        listener.onClick(this, attachment)
    }

    private fun longClickAt() {
        listener.onLongClick(this, attachment)
    }

    companion object {
        const val TAG: String = "AttachmentCard"

        const val MARGIN_CARD_CONTENT_X = 16    // dp
        const val MARGIN_CARD_CONTENT_Y = 8     // dp
        const val RADIUS_CARD_CONTENT = 16

        const val MARGIN_CARD_INFO = 16

    }

}
