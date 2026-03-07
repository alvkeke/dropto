package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.graphics.withClip
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader
import kotlin.math.hypot

class PopupAttachmentCard @JvmOverloads constructor(
    val attachment: AttachmentFile,
    private val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PopupWindow(context, attrs, defStyleAttr) {

    private fun Int.dp() = (this * context.resources.displayMetrics.density).toInt()
    private val contentMargin = 3.dp().toFloat()
    private val bgRect = RectF()
    private val imageMargin = 2.dp().toFloat()
    private val buttonMargin = 8.dp().toFloat()
    private val buttonHeight = 32.dp().toFloat()
    private val buttonCount = 4
    private val btnRects = Array(buttonCount) { RectF() }
    private val btnImgIds = arrayOf(
        R.drawable.icon_common_remove,
        R.drawable.icon_common_share,
        R.drawable.icon_common_more,
        R.drawable.icon_common_save_file,
    )
    private val btnColorIds = arrayOf(
        R.color.attachment_card_button_foreground_warn,
        R.color.attachment_card_button_foreground_normal,
        R.color.attachment_card_button_foreground_normal,
        R.color.attachment_card_button_foreground_normal,
    )

    private fun measureHeight(contentWidth: Int): Int {

        var contentHeight = when (attachment.type) {
            AttachmentFile.Type.MEDIA -> {
                when (attachment.isVideo) {
                    true -> {
                        when (val size = ImageLoader.getVideoSize(attachment.md5file)) {
                            null -> 0
                            else -> {
                                val imageWidth = size.first
                                val imageHeight = size.second
                                if (imageWidth == 0 || imageHeight == 0) {
                                    0
                                } else {
                                    val scale = contentWidth.toFloat() / imageWidth.toFloat()
                                    (imageHeight * scale).toInt()
                                }
                            }
                        }
                    }
                    false -> {
                        when (val size = ImageLoader.getImageSize(attachment.md5file)) {
                            null -> 0
                            else -> {
                                val imageWidth = size.first
                                val imageHeight = size.second
                                if (imageWidth == 0 || imageHeight == 0) {
                                    0
                                } else {
                                    val scale = contentWidth.toFloat() / imageWidth.toFloat()
                                    (imageHeight * scale).toInt()
                                }
                            }
                        }
                    }
                }
            }
            AttachmentFile.Type.FILE -> {
                contentWidth / 2
            }
        }

        contentHeight = contentHeight
            .coerceIn(contentWidth / 2, contentWidth * 4 / 3)

        val totalHeight = contentHeight + contentMargin * 2 + buttonHeight + buttonMargin
        return totalHeight.toInt()
    }

    private val view = object : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            Log.v(TAG, "onMeasure: widthMode=$widthMode, widthSize=$widthSize")
            val desiredWidth = context.resources.displayMetrics.widthPixels / 2
            val width: Int = when (widthMode) {
                MeasureSpec.AT_MOST -> desiredWidth.coerceAtMost(widthSize)
                MeasureSpec.UNSPECIFIED -> desiredWidth
                MeasureSpec.EXACTLY -> widthSize
                else -> {
                    Log.e(TAG, "onMeasure: no widthMode got, return 0")
                    0
                }
            }

            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            Log.v(TAG, "onMeasure: heightMode=$heightMode, heightSize=$heightSize")
            val height: Int = when (heightMode) {
                MeasureSpec.AT_MOST -> measureHeight(width).coerceAtMost(heightSize)
                MeasureSpec.UNSPECIFIED -> measureHeight(width)
                MeasureSpec.EXACTLY -> heightSize
                else -> {
                    Log.e(TAG, "onMeasure: no heightMode got, return 0")
                    0
                }
            }

            setMeasuredDimension(width, height)

            this@PopupAttachmentCard.width = width
            this@PopupAttachmentCard.height = height
        }

        private val bgPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.note_bubble_background)
            setShadowLayer(
                8f, 0f, 0f,
                ContextCompat.getColor(context, R.color.note_bubble_shadow)
            )
        }
        private val imagePaint = Paint().apply {

        }
        private val imageSrcRect = Rect()
        private val imageRect = RectF()
        private val imagePath = Path()
        private val btnPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.attachment_card_button_background)
            setShadowLayer(
                2f, 0f, 0f,
                ContextCompat.getColor(context, R.color.attachment_card_button_background_shadow)
            )
        }

        override fun onDraw(canvas: Canvas) {

            val contentWidth = width - contentMargin * 2f
            val contentHeight = height - contentMargin * 2f - buttonHeight - buttonMargin
            bgRect.set(
                0f, 0f,
                contentWidth, contentHeight
            )
            bgRect.offset(contentMargin, contentMargin)
            canvas.drawRoundRect(bgRect, 16f, 16f, bgPaint)
            cachedBitmap?.let {
                imageRect.set(bgRect)
                imageRect.inset(imageMargin, imageMargin)
                imagePath.reset()
                imagePath.addRoundRect(
                    imageRect,
                    16f, 16f,
                    Path.Direction.CW
                )
                val bmpW = it.width
                val bmpH = it.height
                val rectW = imageRect.width()
                val rectH = imageRect.height()
                val bmpRatio = bmpW / bmpH
                val rectRatio = rectW / rectH
                if (bmpRatio > rectRatio) {
                    val srcW = (bmpH * rectRatio).toInt()
                    val left = ((bmpW - srcW) / 2f).toInt()
                    imageSrcRect.set(
                        left,
                        0,
                        left + srcW,
                        bmpH
                    )
                } else {
                    val srcH = (bmpW / rectRatio).toInt()
                    val top = (bmpH - srcH) / 2
                    imageSrcRect.set(
                        0,
                        top,
                        bmpW,
                        top + srcH
                    )
                }

                canvas.withClip(imagePath) {
                    drawBitmap(
                        it,
                        imageSrcRect,
                        imageRect,
                        imagePaint
                    )
                }
            }

            val buttonWidth = (contentWidth - buttonMargin * (buttonCount - 1)) / buttonCount
            val offsetY = bgRect.bottom + buttonMargin
            val buttonBottom = offsetY + buttonHeight

            for (i in 0 until buttonCount) {
                val offsetX = contentMargin + i * (buttonWidth + buttonMargin)
                btnRects[i].set(
                    offsetX,
                    offsetY,
                    offsetX + buttonWidth,
                    buttonBottom
                )
                canvas.drawRoundRect(
                    btnRects[i], 16f, 16f, btnPaint
                )
                val img = ContextCompat.getDrawable(context, btnImgIds[i]) ?: return
                img.setTint(ContextCompat.getColor(context, btnColorIds[i]))
                val imgSize = buttonHeight * 0.6f
                val imgLeft = offsetX + (buttonWidth - imgSize) / 2f
                val imgTop = offsetY + (buttonHeight - imgSize) / 2f
                img.setBounds(
                    imgLeft.toInt(), imgTop.toInt(),
                    (imgLeft + imgSize).toInt(), (imgTop + imgSize).toInt()
                )
                img.draw(canvas)
            }

            super.onDraw(canvas)
        }
    }

    private var cachedBitmap: Bitmap? = null
    init {
        isFocusable = true
        isOutsideTouchable = true
        if (attachment.md5file.exists()) {
            if (attachment.isImage) {
                ImageLoader.loadOriginalImageAsync(attachment.md5file) {
                    cachedBitmap = it ?: ImageLoader.errorBitmap
                    view.invalidate()
                }
            } else if (attachment.isVideo) {
                ImageLoader.loadVideoThumbnailAsync(attachment.md5file) {
                    cachedBitmap = it ?: ImageLoader.errorBitmap
                    view.invalidate()
                }
            } else {
                cachedBitmap = ImageLoader.iconFile
            }
        } else {
            cachedBitmap = ImageLoader.errorBitmap
        }
        contentView = view
        view.measure(this.width, this.height)
        Log.e(TAG, "init: contentView measured width=${view.measuredWidth}, height=${view.measuredHeight}")
        contentView.setOnTouchListener(CardTouchListener())
    }

    private enum class TouchStatus {
        IDLE,
        STARTED,
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private inner class CardTouchListener : View.OnTouchListener {
        private var touchStatus = TouchStatus.IDLE
        private var downX = 0f
        private var downY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent): Boolean {

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    touchStatus = TouchStatus.STARTED
                }
                MotionEvent.ACTION_MOVE -> {
                    if (touchStatus == TouchStatus.STARTED) {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        if (hypot(deltaX.toDouble(), deltaY.toDouble()) > touchSlop) {
                            touchStatus = TouchStatus.IDLE
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (touchStatus == TouchStatus.STARTED) {
                        val x = event.x
                        val y = event.y
                        for (i in 0 until buttonCount) {
                            if (btnRects[i].contains(x, y)) {
                                val imgId = btnImgIds[i]
                                when (imgId) {
                                    R.drawable.icon_common_remove -> actionListener?.onRemove(attachment)
                                    R.drawable.icon_common_share -> actionListener?.onShare(attachment)
                                    R.drawable.icon_common_more -> actionListener?.onOpen(attachment)
                                    R.drawable.icon_common_save_file -> actionListener?.onSave(attachment)
                                }
                                break
                            }
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    touchStatus = TouchStatus.IDLE
                }
            }

            return true
        }
    }

    interface ActionListener {
        fun onRemove(attachment: AttachmentFile)
        fun onShare(attachment: AttachmentFile)
        fun onOpen(attachment: AttachmentFile)
        fun onSave(attachment: AttachmentFile)
    }
    private var actionListener: ActionListener? = null
    fun setActionListener(listener: ActionListener) {
        actionListener = listener
    }

}