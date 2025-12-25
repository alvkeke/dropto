package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.core.graphics.withTranslation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class NoteItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var index: Int = -1
    var text: String = ""
    var createTime: Long = 0L
    @JvmField
    var selected: Boolean = false

    val images: ArrayList<Bitmap> = ArrayList()


    private var backgroundRect: RectF = RectF()
    private var backgroundPaint: Paint = Paint().apply {
        color = BACKGROUND_COLOR
        style = Paint.Style.FILL
    }

    private var imageRect: RectF = RectF()

    private lateinit var textLayout: StaticLayout
    private val textPaint = TextPaint().apply {
        color = Color.WHITE
        textSize = TEXT_SIZE_CONTENT
        isAntiAlias = true
    }
    private lateinit var timeLayout: StaticLayout
    private val timePaint: TextPaint = TextPaint().apply {
        color = Color.LTGRAY
        textSize = TEXT_SIZE_TIME
        isAntiAlias = true
    }

    /*
    group the images with specific layout
    2: A | B

    3:   | B
       A | -
         | C

    4:   | B
       A | C
         | D

    5: A | B
       -----
       C D E

    6: A B C
       D E F

    7:  A B
       C D E
        F G

    8: A B C
       D E F
        G H

    9: A B C
       D E F
       G H I

    9+:  A B C
        D E F G
         H I J/+
     */
    private val imageSizeMap = HashMap<Int, Size>()

    private fun measureImageHeight(contentWidth: Int): Int {
        val maxHeight = contentWidth * 3 / 4

        var desiredHeight = 0
        when (images.size) {
            0 -> return 0
            1 -> {
                val e = images[0]
                val imageHeight = (e.height * contentWidth / e.width).coerceAtMost(maxHeight)
                val imageWidth = (e.width * imageHeight / e.height)
                imageSizeMap[1] = Size(imageWidth, imageHeight)
                desiredHeight += imageHeight + MARGIN_IMAGE
            }
            2 -> {
                val e1 = images[0]
                val e2 = images[1]

                var alignHeight = maxOf(e1.height, e2.height)
                var width1 = e1.width * alignHeight / e1.height
                var width2 = e2.width * alignHeight / e2.height
                val combineWidth = width1 + width2 + MARGIN_IMAGE

                if (combineWidth > contentWidth) {
                    alignHeight = alignHeight * contentWidth / combineWidth
                    width1 = e1.width * alignHeight / e1.height
                    width2 = e2.width * alignHeight / e2.height
                }

                if (alignHeight > maxHeight) {
                    alignHeight = maxHeight
                    width1 = e1.width * alignHeight / e1.height
                    width2 = e2.width * alignHeight / e2.height
                }

                imageSizeMap[1] = Size(width1, alignHeight)
                imageSizeMap[2] = Size(width2, alignHeight)

                desiredHeight += alignHeight + MARGIN_IMAGE
            }
            3 -> {
                var height = images[0].height
                var width = images[0].width

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                imageSizeMap[0] = Size(width, height)
                val heightRight = (height - MARGIN_IMAGE) / 2
                imageSizeMap[1] = Size(width, heightRight)
                imageSizeMap[2] = Size(width, heightRight)


                desiredHeight += height + MARGIN_IMAGE
            }
            4 -> {
                var height = images[0].height
                var width = images[0].width

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                imageSizeMap[0] = Size(width, height)
                val heightRight = (height - MARGIN_IMAGE * 2) / 3
                imageSizeMap[1] = Size(width, heightRight)
                imageSizeMap[2] = Size(width, heightRight)
                imageSizeMap[3] = Size(width, heightRight)

                desiredHeight += height + MARGIN_IMAGE
            }
            else -> {
                Log.e(TAG, "measureImageHeight: not implement yet for ${images.size} images")
            }
        }
        return desiredHeight
    }

    private fun measureHeight(width: Int) : Int {
        val contentWidth =
            width - MARGIN_BACKGROUND_START - MARGIN_BACKGROUND_END - MARGIN_BORDER * 2
        var desiredHeight = MARGIN_BORDER * 2

        desiredHeight += measureImageHeight(contentWidth)

        if (!text.isEmpty()) {
            textLayout = StaticLayout.Builder
                .obtain(
                    text, 0, text.length, textPaint,
                    contentWidth - (MARGIN_TEXT * 2)
                )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            desiredHeight += textLayout.height + MARGIN_TEXT * 2
        }

        val timeText = createTime.format()
        timeLayout = StaticLayout.Builder
            .obtain(
                timeText, 0, timeText.length, timePaint,
                contentWidth - (MARGIN_TIME * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        desiredHeight += timeLayout.height + MARGIN_TIME

        return desiredHeight
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        Log.e(TAG, "onMeasure: $index")
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        Log.e(TAG, "onMeasure: widthMode=$widthMode, widthSize=$widthSize")
        val desiredWidth = 200
        val width: Int = when (widthMode) {
            MeasureSpec.AT_MOST -> desiredWidth.coerceAtMost(widthSize)
            MeasureSpec.UNSPECIFIED -> desiredWidth
            MeasureSpec.EXACTLY -> widthSize
            else -> {
                Log.e(TAG, "onMeasure: widthMode is UNSPECIFIED")
                0
            }
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        Log.e(TAG, "onMeasure: heightMode=$heightMode, heightSize=$heightSize")
        val height: Int = when (heightMode) {
            MeasureSpec.AT_MOST -> measureHeight(width).coerceAtMost(heightSize)
            MeasureSpec.UNSPECIFIED -> measureHeight(width)
            MeasureSpec.EXACTLY -> heightSize
            else -> 0
        }

        Log.e(TAG, "onMeasure: width=$width, height=$height")
        setMeasuredDimension(width, height)

        backgroundRect.set(
            MARGIN_BACKGROUND_START.toFloat(),
            MARGIN_BACKGROUND_Y.toFloat(),
            (width - MARGIN_BACKGROUND_END).toFloat(),
            (height - MARGIN_BACKGROUND_Y).toFloat()
        )

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.e(TAG, "onDraw: $index")

        backgroundPaint.color = if (selected) Color.LTGRAY else BACKGROUND_COLOR
        canvas.drawRoundRect(
            backgroundRect,
            BACKGROUND_RADIUS.toFloat(),
            BACKGROUND_RADIUS.toFloat(),
            backgroundPaint
        )
        canvas.translate(MARGIN_BACKGROUND_START.toFloat(), MARGIN_BACKGROUND_Y.toFloat())
        canvas.translate(MARGIN_BORDER.toFloat(), MARGIN_BORDER.toFloat())

//        for (e in images) {
//            val imageWidth = width - MARGIN_BACKGROUND_START - MARGIN_BACKGROUND_END - MARGIN_BORDER * 2
//            val imageHeight = e.height * imageWidth / e.width
//            imageRect.set(
//                0F,
//                0F,
//                imageWidth.toFloat(),
//                imageHeight.toFloat()
//            )
//            canvas.drawBitmap(e, null, imageRect, null)
//            canvas.translate(0F, (imageHeight + MARGIN_IMAGE).toFloat())
//        }

        if (!text.isEmpty()) {
            canvas.translate(0F, MARGIN_TEXT.toFloat())
            canvas.withTranslation(MARGIN_TEXT.toFloat(), 0F) {
                textLayout.draw(this)
            }
            canvas.translate(0F, (textLayout.height + MARGIN_TEXT).toFloat())
        }
        canvas.withTranslation(MARGIN_TIME.toFloat(), 0F) {
            timeLayout.draw(this)
        }
    }

    private fun Long.format(): String {
        return sdf.format(Date(this))
    }

    companion object {
        const val TAG: String = "NoteItemView"

        const val MARGIN_BACKGROUND_Y = 4
        const val MARGIN_BACKGROUND_START = 128
        const val MARGIN_BACKGROUND_END = 64
        const val BACKGROUND_RADIUS = 16
        const val BACKGROUND_COLOR = 0x33FFFFFF

        const val MARGIN_BORDER = 4
        const val MARGIN_IMAGE = 4

        const val MARGIN_TEXT = 16
        const val MARGIN_TIME = 8

        const val TEXT_SIZE_CONTENT = 48f
        const val TEXT_SIZE_TIME = 30f

        var sdf: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
    }

}