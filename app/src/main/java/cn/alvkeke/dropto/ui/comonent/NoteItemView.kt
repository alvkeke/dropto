package cn.alvkeke.dropto.ui.comonent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.withTranslation
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class NoteItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = context.resources.displayMetrics.density
    private fun Int.dp(): Int = (this * density).toInt()

    var index: Int = -1
    var text: String = ""
    var createTime: Long = 0L
    @JvmField
    var selected: Boolean = false
    @JvmField
    var asyncImageLoad: Boolean = true

    private val _images: MutableList<AttachmentFile> = mutableListOf()
    private val imageLoadedMap: HashMap<AttachmentFile, Boolean> = HashMap()
    val images: MutableList<AttachmentFile> = object : MutableList<AttachmentFile> by _images {
        override fun clear() {
            _images.clear()
            imageLoadedMap.clear()
        }

        override fun remove(element: AttachmentFile): Boolean {
            val result = _images.remove(element)
            if (result) {
                imageLoadedMap.remove(element)
            }
            return result
        }

        override fun add(element: AttachmentFile): Boolean {
            imageLoadedMap[element] = false
            return _images.add(element)
        }
    }
    val files: MutableList<AttachmentFile> = mutableListOf()

    private var backgroundRect: RectF = RectF()
    private var backgroundPaint: Paint = Paint().apply {
        color = BACKGROUND_COLOR
        style = Paint.Style.FILL
    }

    private var imageRect: RectF = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePath = Path()

    private val fileIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
    }
    private val fileNamePaint = TextPaint().apply {
        color = Color.LTGRAY
        textSize = TEXT_SIZE_FILENAME
        isAntiAlias = true
    }

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
    private enum class InfoStatus{
        NONE,
        SET,
    }
    private class ImageInfo(var rect: RectF = RectF(), var status: InfoStatus = InfoStatus.NONE) {
        fun set() {
            this.status = InfoStatus.SET
        }
    }
    private val imageInfoMap = HashMap<Int, ImageInfo>()

    private fun getImageInfo(key: Int): ImageInfo {
        var info = imageInfoMap[key]
        if (info == null) {
            info = ImageInfo()
            imageInfoMap[key] = info
        }

        return info
    }

    private fun measureImageHeight(contentWidth: Int): Int {
        val maxHeight = contentWidth * 3 / 2

        val options:ArrayList<BitmapFactory.Options> = ArrayList()
        for (a in images) {
            val f = a.md5file
            if (!f.exists()) {
                Log.e(TAG, "measureImageHeight: image file not exists: ${f.absolutePath}")
                continue
            }
            val option = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(f.absolutePath, option)
            options.add(option)
        }

        var desiredHeight = 0
        when (options.size) {
            0 -> return 0
            1 -> {
                val e = options[0]
                val imageHeight = (e.outHeight * contentWidth / e.outWidth).coerceAtMost(maxHeight)
                val imageWidth = (e.outWidth * imageHeight / e.outHeight)
                val info = getImageInfo(0)
                info.rect.set(
                    0F,
                    0F,
                    imageWidth.toFloat(),
                    imageHeight.toFloat()
                )
                info.status = InfoStatus.SET
                desiredHeight += imageHeight + MARGIN_IMAGE
            }
            2 -> {
                val e1 = options[0]
                val e2 = options[1]

                var alignHeight = maxOf(e1.outHeight, e2.outHeight)
                var width1 = e1.outWidth * alignHeight / e1.outHeight
                var width2 = e2.outWidth * alignHeight / e2.outHeight
                val combineWidth = width1 + width2 + MARGIN_IMAGE

                if (combineWidth > contentWidth) {
                    alignHeight = alignHeight * contentWidth / combineWidth
                    width1 = e1.outWidth * alignHeight / e1.outHeight
                    width2 = e2.outWidth * alignHeight / e2.outHeight
                }

                if (alignHeight > maxHeight) {
                    alignHeight = maxHeight
                    width1 = e1.outWidth * alignHeight / e1.outHeight
                    width2 = e2.outWidth * alignHeight / e2.outHeight
                }

                var info = getImageInfo(0)
                info.rect.set(
                    0F,
                    0F,
                    width1.toFloat(),
                    alignHeight.toFloat()
                )
                info.status = InfoStatus.SET

                val offsetX = (width1 + MARGIN_IMAGE)
                info = getImageInfo(1)
                info.rect.set(
                    offsetX.toFloat(),
                    0F,
                    (offsetX + width2).toFloat(),
                    alignHeight.toFloat()
                )
                info.status = InfoStatus.SET

                desiredHeight += alignHeight + MARGIN_IMAGE
            }
            3 -> {
                var height = options[0].outHeight
                var width = options[0].outWidth

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                var info = getImageInfo(0)
                info.rect.set(
                    0F,
                    0F,
                    width.toFloat(),
                    height.toFloat(),
                )
                info.status = InfoStatus.SET

                val heightRight = (height - MARGIN_IMAGE) / 2
                info = getImageInfo(1)
                val offsetX = width + MARGIN_IMAGE
                info.rect.set(
                    offsetX.toFloat(),
                    0f,
                    (offsetX + width).toFloat(),
                    heightRight.toFloat()
                )
                info.status = InfoStatus.SET

                val offsetY = heightRight + MARGIN_IMAGE
                info = getImageInfo(2)
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat()
                )
                info.status = InfoStatus.SET

                desiredHeight += height + MARGIN_IMAGE
            }
            4 -> {
                var height = options[0].outHeight
                var width = options[0].outWidth

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                var info = getImageInfo(0)
                info.rect.set(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat()
                )
                info.set()

                val heightRight = (height - MARGIN_IMAGE * 2) / 3
                val offsetX = width + MARGIN_IMAGE
                info = getImageInfo(1)
                info.rect.set(
                    offsetX.toFloat(),
                    0f,
                    (offsetX + width).toFloat(),
                    heightRight.toFloat()
                )
                info.set()

                var offsetY = heightRight + MARGIN_IMAGE
                info = getImageInfo(2)
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat(),
                )
                info.set()

                offsetY += heightRight + MARGIN_IMAGE
                info = getImageInfo(3)
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat(),
                )
                info.set()

                desiredHeight += height + MARGIN_IMAGE
            }
            5 -> {
                val widthTop = (contentWidth - MARGIN_IMAGE) / 2
                val widthBottom = (contentWidth - MARGIN_IMAGE * 2) / 3
                val heightTop = widthTop * 4 / 5
                val heightBottom = widthBottom * 4 / 5

                for (i in 0..1) {
                    val offsetX = i * (widthTop + MARGIN_IMAGE)
                    val info = getImageInfo(i)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + widthTop).toFloat(),
                        heightTop.toFloat()
                    )
                    info.set()
                }

                val offsetY = heightTop + MARGIN_IMAGE
                for (i in 2..4) {
                    val offsetX = (i-2) * (widthBottom + MARGIN_IMAGE)
                    val info = getImageInfo(i)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + widthBottom).toFloat(),
                        (offsetY + heightBottom).toFloat()
                    )
                    info.set()
                }

                desiredHeight += heightTop + MARGIN_IMAGE + heightBottom + MARGIN_IMAGE
            }
            6 -> {
                val width = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height = width * 4 / 5

                var offsetX: Int
                var offsetY: Int
                for (i in 0 until 6) {
                    val info = getImageInfo(i)
                    offsetX = (i % 3) * (width + MARGIN_IMAGE)
                    offsetY = when(i) {
                        2, 5 -> height + MARGIN_IMAGE
                        else -> 0
                    }
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width).toFloat(),
                        (offsetY + height).toFloat(),
                    )
                    info.set()
                }

                desiredHeight += (height + MARGIN_IMAGE) * 2
            }
            7 -> {
                val width2 = (contentWidth - MARGIN_IMAGE) / 2
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3

                val height2 = width2 * 4 / 5
                val height3 = width3 * 4 / 5

                var offsetX: Int
                for (i in 0..1) {
                    val info = getImageInfo(i)
                    offsetX = i * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width2).toFloat(),
                        height2.toFloat()
                    )
                    info.set()
                }
                var offsetY: Int = height2 + MARGIN_IMAGE
                for (i in 2..4) {
                    val info = getImageInfo(i)
                    offsetX = (i - 2) * (width3 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat(),
                    )
                    info.set()
                }
                offsetY += height3 + MARGIN_IMAGE
                for (i in 5..6) {
                    val info = getImageInfo(i)
                    offsetX = (i - 5) * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat(),
                    )
                    info.set()
                }

                desiredHeight += height2 * 2 + height3 + MARGIN_IMAGE * 3
            }
            8 -> {
                val width2 = (contentWidth - MARGIN_IMAGE) / 2
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3

                val height2 = width2 * 4 / 5
                val height3 = width3 * 4 / 5

                for (i in 0..1) {
                    val info = getImageInfo(i)
                    val offsetX = i * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width2).toFloat(),
                        height2.toFloat()
                    )
                    info.set()
                }
                var offsetY = height2 + MARGIN_IMAGE
                for (i in 2..7) {
                    val info = getImageInfo(i)
                    val idx = i - 2
                    val offsetX = (idx % 3) * (width3 + MARGIN_IMAGE)
                    if (i == 5) {
                        offsetY += height3 + MARGIN_IMAGE
                    }

                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                    info.set()
                }

                desiredHeight += height2 + height3 * 2 + MARGIN_IMAGE * 3
            }
            9 -> {
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height3 = width3 * 4 / 5

                var offsetY = 0
                for (i in 0..8) {
                    val offsetX = (i % 3) * (width3 + MARGIN_IMAGE)
                    if ((i % 3) == 0) {
                        offsetY += height3 + MARGIN_IMAGE
                    }
                    val info = getImageInfo(i)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                    info.set()
                }

                desiredHeight += height3 * 3 + MARGIN_IMAGE * 3
            }
            10 -> {
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height3 = width3 * 4 / 5
                val width4 = (contentWidth - MARGIN_IMAGE * 3) / 4
                val height4 = width4 * 4 / 5
                for (i in 0..2) {
                    val info = getImageInfo(i)
                    val offsetX = i * (width3 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width3).toFloat(),
                        height3.toFloat()
                    )
                    info.set()
                }
                var offsetY = height3 + MARGIN_IMAGE
                for (i in 3..6) {
                    val info = getImageInfo(i)
                    val offsetX = (i-3) * (width4 + MARGIN_IMAGE)

                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                    info.set()
                }
                offsetY += height4 + MARGIN_IMAGE
                for (i in 6..9) {
                    val offsetX = (i - 6) * (width3 + MARGIN_IMAGE)
                    val info = getImageInfo(i)

                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                    info.set()
                }
                desiredHeight += height3 * 2 + height4 + MARGIN_IMAGE * 3
            }
            else -> {
                Log.e(TAG, "measureImageHeight: not implement yet for ${options.size} options")
            }
        }
        return desiredHeight
    }

    private fun measureFilesHeight(): Int {
        return MARGIN_FILE + files.size * (FILE_ICON_SIZE.dp() + MARGIN_FILE)
    }

    private fun measureHeight(width: Int) : Int {
        val contentWidth =
            width - MARGIN_BACKGROUND_START - MARGIN_BACKGROUND_END - MARGIN_BORDER * 2
        var desiredHeight = MARGIN_BORDER * 2

        desiredHeight += measureImageHeight(contentWidth)
        desiredHeight += measureFilesHeight()

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
        Log.v(TAG, "onMeasure: $index")
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        Log.v(TAG, "onMeasure: widthMode=$widthMode, widthSize=$widthSize")
        val desiredWidth = 200
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

        Log.v(TAG, "onMeasure: width=$width, height=$height")
        setMeasuredDimension(width, height)

        backgroundRect.set(
            MARGIN_BACKGROUND_START.toFloat(),
            MARGIN_BACKGROUND_Y.toFloat(),
            (width - MARGIN_BACKGROUND_END).toFloat(),
            (height - MARGIN_BACKGROUND_Y).toFloat()
        )

    }

    private fun Canvas.drawBitmapNullable(bitmap: Bitmap?, src: Rect?, dst: RectF, paint: Paint) {
        if (bitmap == null) {
            Log.v(TAG, "drawBitmapNullable: bitmap is null, draw error bitmap")
            this.drawBitmap(ImageLoader.errorBitmap, src, dst, paint)
        } else {
            Log.v(TAG, "drawBitmapNullable: bitmap is valid, draw it: $bitmap")
            this.drawBitmap(bitmap, src, dst, paint)
        }
    }

    private fun Canvas.drawImageFile(attachment: AttachmentFile, dst: RectF, paint: Paint) {
        val file = attachment.md5file
        Log.v(TAG, "drawImageFile: async=$asyncImageLoad, file=${file.absolutePath}")
        val bitmap:Bitmap = if (asyncImageLoad) {
            val bitmapAsync = ImageLoader.loadImageAsync(file, false) { b ->
                Log.d(TAG, "drawImageFile.loadImageAsync callback: file=${file.absolutePath}, bitmap=$b")
                imageLoadedMap[attachment] = true
                if (imageLoadedMap.values.all { it }) {
                    this@NoteItemView.invalidate()  // have cache now, just invalidate to redraw
                }
            }
            if (bitmapAsync == null) {
                imageLoadedMap[attachment] = false
                drawBitmapNullable(ImageLoader.loadingBitmap, null, dst, paint)
                return
            } else {
                imageLoadedMap[attachment] = true
            }
            bitmapAsync
        } else {
            ImageLoader.loadImage(file) ?: ImageLoader.errorBitmap
        }

        val ratioBitmap = bitmap.width.toFloat() / bitmap.height.toFloat()
        val ratioDst = dst.width() / dst.height()

        // Calculate src rect with same ratio as dst, matching bitmap's width or height
        val src = Rect()
        if (ratioBitmap > ratioDst) {
            // Bitmap is wider - use full height, crop width
            val srcWidth = (bitmap.height * ratioDst).toInt()
            val srcLeft = (bitmap.width - srcWidth) / 2
            src.set(srcLeft, 0, srcLeft + srcWidth, bitmap.height)
        } else {
            // Bitmap is taller - use full width, crop height
            val srcHeight = (bitmap.width / ratioDst).toInt()
            val srcTop = (bitmap.height - srcHeight) / 2
            src.set(0, srcTop, bitmap.width, srcTop + srcHeight)
        }

        drawBitmapNullable(bitmap, src, dst, paint)
    }

    private fun drawImages(canvas: Canvas): Float {
        Log.v(TAG, "drawImages: total size: ${images.size}")
        for (i in 0 until images.size) {
            Log.v(TAG, "drawImages.for $i")
            val info = getImageInfo(i)
            if (info.status == InfoStatus.NONE) {
                Log.e(TAG, "Trying to draw image without setting the image rect")
                continue
            }
            imageRect.set(info.rect)

            // Draw bitmap with rounded corners
            val saveCount = canvas.saveLayer(imageRect, null)

            // Draw rounded rect as mask
            imagePath.reset()
            imagePath.addRoundRect(
                imageRect,
                IMAGE_RADIUS.toFloat(),
                IMAGE_RADIUS.toFloat(),
                Path.Direction.CW
            )
            canvas.drawPath(imagePath, imagePaint)

            // Set xfermode to only draw bitmap where the rounded rect was drawn
            imagePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawImageFile(images[i], imageRect, imagePaint)

            // Reset xfermode
            imagePaint.xfermode = null
            canvas.restoreToCount(saveCount)
        }

        return getImageInfo(images.lastIndex).rect.bottom + MARGIN_IMAGE
    }

    private fun drawFiles(canvas: Canvas, contentWidth: Int): Float {
        var offsetY: Float = MARGIN_FILE.toFloat()
        for (file in files) {
            Log.v(TAG, "drawFiles: file=${file.name}, md5=${file.md5}")

            val rect = RectF(
                MARGIN_FILE.toFloat(),
                offsetY,
                (MARGIN_FILE + FILE_ICON_SIZE.dp()).toFloat(),
                offsetY + FILE_ICON_SIZE.dp()
            )
            canvas.drawRoundRect(
                rect,
                FILE_ICON_RADIUS.toFloat(),
                FILE_ICON_RADIUS.toFloat(),
                fileIconPaint
            )

            canvas.drawBitmap(
                ImageLoader.iconFile,
                null,
                rect,
                fileIconPaint
            )

            // draw filename
            // Calculate max lines based on icon height and text size
            val maxLines = ((FILE_ICON_SIZE.dp() - MARGIN_FILENAME * 2) / TEXT_SIZE_FILENAME)
                .toInt().coerceAtLeast(1)
            val fileNameLayout = StaticLayout.Builder
                .obtain(
                    file.name, 0, file.name.length, fileNamePaint,
                    contentWidth - MARGIN_FILE * 2 - MARGIN_FILENAME - FILE_ICON_SIZE.dp()
                )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

            canvas.withTranslation(
                (MARGIN_FILE + MARGIN_FILENAME + FILE_ICON_SIZE.dp()).toFloat(),
                offsetY + MARGIN_FILENAME
            ) {
                fileNameLayout.draw(this)
            }

            offsetY += FILE_ICON_SIZE.dp() + MARGIN_FILE
        }

        return offsetY
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.v(TAG, "onDraw: $index")

        var contentWidth = width
        backgroundPaint.color = if (selected) Color.LTGRAY else BACKGROUND_COLOR
        canvas.drawRoundRect(
            backgroundRect,
            BACKGROUND_RADIUS.toFloat(),
            BACKGROUND_RADIUS.toFloat(),
            backgroundPaint
        )
        canvas.translate(MARGIN_BACKGROUND_START.toFloat(), MARGIN_BACKGROUND_Y.toFloat())
        contentWidth -= (MARGIN_BACKGROUND_START + MARGIN_BACKGROUND_END)
        canvas.translate(MARGIN_BORDER.toFloat(), MARGIN_BORDER.toFloat())
        contentWidth -= MARGIN_BORDER * 2
        // draw images with the info got from measure
        canvas.translate(0F, drawImages(canvas))
        canvas.translate(0F, drawFiles(canvas, contentWidth))

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

    class ClickedContent(val type: Type, val data: AttachmentFile? = null, val index: Int = -1) {
        enum class Type {
            BACKGROUND,
            IMAGE,
            FILE,
        }
    }

    fun checkClickedContent(x: Float, y: Float): ClickedContent {
        Log.v(TAG, "index-$index, checkClickedItem: x=$x, y=$y")
        imageInfoMap.iterator().forEach { entry ->
            val info = entry.value
            if (info.status == InfoStatus.SET) {
                val rect = RectF(
                    info.rect.left + MARGIN_BACKGROUND_START + MARGIN_BORDER,
                    info.rect.top + MARGIN_BACKGROUND_Y + MARGIN_BORDER,
                    info.rect.right + MARGIN_BACKGROUND_START + MARGIN_BORDER,
                    info.rect.bottom + MARGIN_BACKGROUND_Y + MARGIN_BORDER
                )
                if (rect.contains(x, y)) {
                    Log.v(TAG, "checkClickedItem: image $entry clicked")
                    val attachment = images[entry.key]
                    return ClickedContent(
                        ClickedContent.Type.IMAGE,
                        attachment,
                        images.indexOf(attachment)
                    )
                }
            }
        }

        return ClickedContent(ClickedContent.Type.BACKGROUND)
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

        const val MARGIN_BORDER = 8
        const val MARGIN_IMAGE = 8
        const val IMAGE_RADIUS = 14

        const val MARGIN_FILE = 8
        const val FILE_ICON_SIZE = 48  // in dp
        const val FILE_ICON_RADIUS = 36
        const val MARGIN_FILENAME = 16
        const val TEXT_SIZE_FILENAME = 40f

        const val MARGIN_TEXT = 16
        const val MARGIN_TIME = 8

        const val TEXT_SIZE_CONTENT = 48f
        const val TEXT_SIZE_TIME = 30f

        var sdf: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
    }

}