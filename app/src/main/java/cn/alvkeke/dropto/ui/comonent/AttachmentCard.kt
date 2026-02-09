package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withTranslation
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        val size = contentRect.height() - MARGIN_CARD_INFO * 4
        dstRect.set(
            contentRect.left,
            contentRect.top,
            contentRect.left + size,
            contentRect.top + size
        )
        invalidate()
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

    private fun Canvas.drawFile() {
        drawRect(
            contentRect,
            contentPaint
        )
        drawBitmap(cachedBitmap!!, srcRect, dstRect, bitmapPaint)

        val info = attachment.name
        val infoWidth = (contentRect.width() - dstRect.width() - MARGIN_CARD_INFO * 2).toInt()
        val textStatic = StaticLayout.Builder.obtain(
            info, 0, info.length,
            infoTextPaint, infoWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val x = dstRect.right + MARGIN_CARD_INFO
        val y = contentRect.top + contentRect.height() / 2 - textStatic.height / 2
        withTranslation(x, y) {
            textStatic.draw(this)
        }
    }

    private fun Canvas.drawMedia() {
        if (cachedBitmap == null) return

        drawBitmap(cachedBitmap!!, srcRect, dstRect, bitmapPaint)

        val info = attachment.name
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
        drawRect(
            infoBackRect,
            infoBackPaint
        )
        withTranslation(
            infoBackRect.left + MARGIN_CARD_INFO,
            infoBackRect.top + MARGIN_CARD_INFO) {
            textStatic.draw(this)
        }

        // draw play icon at center
        if (contentType == ContentType.VIDEO) {
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
            drawCircle(cx, cy, radius, videoIconPaint)

            videoIconPaint.style = Paint.Style.STROKE
            videoIconPaint.alpha = 255
            videoIconPaint.color = Color.WHITE
            videoIconPaint.strokeWidth = (iconSize / 6f).coerceAtMost(6f)
            drawCircle(cx, cy, radius, videoIconPaint)
            videoIconPaint.style = Paint.Style.FILL

            videoIconPath.reset()
            videoIconPath.moveTo(cx - radius / 3f, cy - radius / 2f)
            videoIconPath.lineTo(cx - radius / 3f, cy + radius / 2f)
            videoIconPath.lineTo(cx + radius * 2f / 3f, cy)
            videoIconPath.close()
            drawPath(videoIconPath, videoIconPaint)
        }

    }

    private var contentOffset = 0f
    private var contentRect = RectF()
    private val contentPath = Path()
    private var contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
    }

    private val buttonRect = RectF()
    private val buttonPaint = Paint()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (cachedBitmap == null) {
            when (contentType) {
                ContentType.IMAGE -> loadImageBitmap()
                ContentType.VIDEO -> loadVideoBitmap()
                ContentType.FILE -> loadFileBitmap()
            }
            return
        }

        if (animateRatio > 0f) {
            contentOffset = contentRect.width() / 4 * animateRatio

            val buttonHeight = contentRect.height() / 3
            val offX = contentRect.left
            var offY = contentRect.top
            val btnWidth = contentOffset * 5 / 4 + offX

            buttonRect.set(
                offX, offY,
                btnWidth, offY + buttonHeight
            )
            buttonPaint.color = Color.RED
            canvas.drawRect(buttonRect, buttonPaint)
            var icon = ImageLoader.iconDeleted
            var ix = (buttonRect.width()) / 2 - icon.width / 2
            var iy = (buttonRect.height()) / 2 - icon.height / 2
            canvas.drawBitmap(
                icon,
                buttonRect.left + ix,
                buttonRect.top + iy,
                bitmapPaint
            )

            offY += buttonHeight
            buttonRect.set(
                offX, offY,
                btnWidth, offY + buttonHeight
            )
            buttonPaint.color = Color.GRAY
            canvas.drawRect(buttonRect, buttonPaint)
            icon = ImageLoader.iconShare
            ix = (buttonRect.width()) / 2 - icon.width / 2
            iy = (buttonRect.height()) / 2 - icon.height / 2
            canvas.drawBitmap(
                icon,
                buttonRect.left + ix,
                buttonRect.top + iy,
                bitmapPaint
            )


            offY += buttonHeight
            buttonRect.set(
                offX, offY,
                btnWidth, offY + buttonHeight
            )
            buttonPaint.color = Color.LTGRAY
            canvas.drawRect(buttonRect, buttonPaint)
            icon = ImageLoader.iconMore
            ix = (buttonRect.width()) / 2 - icon.width / 2
            iy = (buttonRect.height()) / 2 - icon.height / 2
            canvas.drawBitmap(
                icon,
                buttonRect.left + ix,
                buttonRect.top + iy,
                bitmapPaint
            )

            canvas.translate(contentOffset, 0f)
        }

        contentPath.addRoundRect(
            contentRect,
            RADIUS_CARD_CONTENT.toFloat(),
            RADIUS_CARD_CONTENT.toFloat(),
            Path.Direction.CW
        )
        canvas.clipPath(contentPath)

        when (contentType) {
            ContentType.IMAGE, ContentType.VIDEO -> canvas.drawMedia()
            ContentType.FILE -> canvas.drawFile()
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
        fun onShare(card: AttachmentCard, attachment: AttachmentFile)
        fun onOpen(card: AttachmentCard, attachment: AttachmentFile)
        fun onClick(card: AttachmentCard, attachment: AttachmentFile)
    }

    private var canClick = false
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
                downX = event.rawX
                downY = event.rawY
                canClick = true
                longClickFired = false
                longClickHandler.postDelayed(longClickRunnable, longClickTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    longClickHandler.removeCallbacks(longClickRunnable)
                    canClick = false
                }
            }
            MotionEvent.ACTION_UP -> {
                longClickHandler.removeCallbacks(longClickRunnable)
                if (canClick && !longClickFired) {
                    clickAt(event.x.toInt(), event.y.toInt())
                }
                canClick = false
            }
            MotionEvent.ACTION_CANCEL -> {
                longClickHandler.removeCallbacks(longClickRunnable)
                canClick = false
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private val checkRect = RectF()
    private fun checkClickedContent(x: Int, y: Int): Int {
        checkRect.set(contentRect)
        checkRect.offset(contentOffset, 0f)
        if (checkRect.contains(x.toFloat(), y.toFloat()))
            return 0
        else {
            checkRect.set(
                0f, 0f,
                contentOffset, contentRect.height()
            )
            if (checkRect.contains(x.toFloat(), y.toFloat())) {
                val div = checkRect.height() / 3
                return when {
                    y < div -> 1
                    y < div * 2 -> 2
                    else -> 3
                }
            }
            return 0
        }
    }

    private fun clickAt(x: Int, y: Int) {
        performClick()
        if (animateRatio == 0f) {
            listener.onClick(this, attachment)
        } else {
            val clicked = checkClickedContent(x, y)
            when (clicked) {
                1 -> listener.onRemove(this, attachment)
                2 -> listener.onShare(this, attachment)
                3 -> listener.onOpen(this, attachment)
                else -> animateStart(false)
            }
        }
    }

    private fun longClickAt() {
        this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (animationRunning) return
        if (animateRatio == 0f) {
            animateStart()
        } else {
            animateStart(false)
        }
    }

    private var _scope: CoroutineScope? = null
    private val scope : CoroutineScope
        get() {
            if (_scope?.isActive != true) {
                _scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            }
            return _scope!!
        }

    private var animateJob: Job? = null
    @Volatile private var animateRatio = 0f
    @Volatile private var animationRunning = false
    @Volatile private var animationIsEnter = true
    private fun animateStart(
        isEnter: Boolean = true
    ) {
        if (animationRunning && isEnter == animationIsEnter) return

        val totalFrames = ANIMATION_DURATION / ANIMATION_INTERVAL
        val step: Float
        val isEnd: (Float) -> Boolean
        when (isEnter) {
            true -> {
                step = 1f / totalFrames
                isEnd = { ratio -> ratio >= 1f }
            }
            false -> {
                step = -1f / totalFrames
                isEnd = { ratio -> ratio <= 0f }
            }
        }

        if (animateJob?.isActive != true) {
            animateRatio = if (isEnter) { 0f } else { 1f }
        } else {
            animateJob?.cancel()
        }
        animationIsEnter = isEnter
        animateJob = scope.launch {
            animationRunning = true
            while(true) {
                animateRatio = (animateRatio + step)
                    .coerceIn(0f, 1f)
                invalidate()
                if (isEnd(animateRatio)) break
                kotlinx.coroutines.delay(PopupMenu.ANIMATION_INTERVAL)
            }
            animationRunning = false
        }
    }

    override fun onDetachedFromWindow() {
        _scope?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        const val TAG: String = "AttachmentCard"

        const val MARGIN_CARD_CONTENT_X = 16    // dp
        const val MARGIN_CARD_CONTENT_Y = 8     // dp
        const val RADIUS_CARD_CONTENT = 16

        const val MARGIN_CARD_INFO = 16

        const val ANIMATION_DURATION = 300L
        const val ANIMATION_INTERVAL = 15L
    }

}
