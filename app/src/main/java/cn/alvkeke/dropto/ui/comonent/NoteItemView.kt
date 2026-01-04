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
import android.media.MediaMetadataRetriever
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.util.Size
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

    private class AttachmentList(
        private val backingList: MutableList<AttachmentInfo>
    ) : MutableList<AttachmentFile> {

        class AttachmentInfo(
            var file: AttachmentFile,
            var rect: RectF = RectF(),
        ) {
            var cachedBitmap: Bitmap? = null
                get() {
                    if (field == null) {
                        return null
                    }
                    if (field!!.isRecycled) {
                        field = null
                    }
                    return field
                }
        }

        override val size: Int get() = backingList.size

        override fun contains(element: AttachmentFile): Boolean {
            return backingList.any { it.file == element }
        }

        override fun containsAll(elements: Collection<AttachmentFile>): Boolean {
            return elements.all { contains(it) }
        }

        override fun get(index: Int): AttachmentFile {
            return backingList[index].file
        }

        override fun indexOf(element: AttachmentFile): Int {
            return backingList.indexOfFirst { it.file == element }
        }

        override fun isEmpty(): Boolean = backingList.isEmpty()

        override fun iterator(): MutableIterator<AttachmentFile> {
            return backingList.map { it.file }.toMutableList().iterator()
        }

        override fun lastIndexOf(element: AttachmentFile): Int {
            return backingList.indexOfLast { it.file == element }
        }

        override fun add(element: AttachmentFile): Boolean {
            return backingList.add(AttachmentInfo(element))
        }

        override fun add(index: Int, element: AttachmentFile) {
            backingList.add(index, AttachmentInfo(element))
        }

        override fun addAll(index: Int, elements: Collection<AttachmentFile>): Boolean {
            return backingList.addAll(index, elements.map { AttachmentInfo(it) })
        }

        override fun addAll(elements: Collection<AttachmentFile>): Boolean {
            return backingList.addAll(elements.map { AttachmentInfo(it) })
        }

        override fun clear() {
            backingList.clear()
        }

        override fun listIterator(): MutableListIterator<AttachmentFile> {
            return backingList.map { it.file }.toMutableList().listIterator()
        }

        override fun listIterator(index: Int): MutableListIterator<AttachmentFile> {
            return backingList.map { it.file }.toMutableList().listIterator(index)
        }

        override fun remove(element: AttachmentFile): Boolean {
            val index = indexOf(element)
            if (index != -1) {
                backingList.removeAt(index)
                return true
            }
            return false
        }

        override fun removeAll(elements: Collection<AttachmentFile>): Boolean {
            var modified = false
            elements.forEach {
                if (remove(it)) modified = true
            }
            return modified
        }

        override fun removeAt(index: Int): AttachmentFile {
            val info = backingList.removeAt(index)
            return info.file
        }

        override fun retainAll(elements: Collection<AttachmentFile>): Boolean {
            val toRemove = backingList.filter { it.file !in elements }
            return backingList.retainAll(toRemove.map { it })
        }

        override fun set(index: Int, element: AttachmentFile): AttachmentFile {
            val old = backingList[index].file
            backingList[index] = AttachmentInfo(element)
            return old
        }

        override fun subList(fromIndex: Int, toIndex: Int): MutableList<AttachmentFile> {
            return backingList.subList(fromIndex, toIndex).map { it.file }.toMutableList()
        }
    }

    private val _medias: MutableList<AttachmentList.AttachmentInfo> = mutableListOf()
    val medias: MutableList<AttachmentFile> = AttachmentList(_medias)
    private val _files: MutableList<AttachmentList.AttachmentInfo> = mutableListOf()
    val files: MutableList<AttachmentFile> = AttachmentList(_files)

    private var backgroundRect: RectF = RectF()
    private var backgroundPaint: Paint = Paint().apply {
        color = BACKGROUND_COLOR
        style = Paint.Style.FILL
    }

    private var imageRect: RectF = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val videoIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }
    private val videoIconPath = Path()
    private val imagePath = Path()
    private val mediaOverlayRect = RectF()
    private val mediaOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 180
        style = Paint.Style.FILL
    }
    private val mediaOverlayFontPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

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

    private fun measureMediasHeight(contentWidth: Int): Int {
        val maxHeight = contentWidth * 3 / 2

        val options:ArrayList<Size> = ArrayList()
        for (a in _medias.take(MAX_IMAGE_COUNT)) {
            val f = a.file.md5file
            if (!f.exists()) {
                Log.e(TAG, "measureImageHeight: image file not exists: ${f.absolutePath}")
                continue
            }

            if (a.file.isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(f.absolutePath)
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    if (width != null && height != null) {
                        val p = Size(width, height)
                        options.add(p)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "measureMediasHeight: failed to get video size for file=${f.absolutePath}", e)
                    options.add(Size(1, 1))
                } finally {
                    retriever.release()
                }
            } else {
                val option = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(f.absolutePath, option)
                options.add(Size(option.outWidth, option.outHeight))
            }
        }

        var desiredHeight = 0
        when (options.size) {
            0 -> return 0
            1 -> {
                val e = options[0]
                val imageHeight = (e.height * contentWidth / e.width).coerceAtMost(maxHeight)
                val imageWidth = (e.width * imageHeight / e.height)
                val info = _medias[0]
                info.rect.set(
                    0F,
                    0F,
                    imageWidth.toFloat(),
                    imageHeight.toFloat()
                )
                desiredHeight += imageHeight + MARGIN_IMAGE
            }
            2 -> {
                val e1 = options[0]
                val e2 = options[1]

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

                var info = _medias[0]
                info.rect.set(
                    0F,
                    0F,
                    width1.toFloat(),
                    alignHeight.toFloat()
                )

                val offsetX = (width1 + MARGIN_IMAGE)
                info = _medias[1]
                info.rect.set(
                    offsetX.toFloat(),
                    0F,
                    (offsetX + width2).toFloat(),
                    alignHeight.toFloat()
                )

                desiredHeight += alignHeight + MARGIN_IMAGE
            }
            3 -> {
                var height = options[0].height
                var width = options[0].width

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                var info = _medias[0]
                info.rect.set(
                    0F,
                    0F,
                    width.toFloat(),
                    height.toFloat(),
                )

                val heightRight = (height - MARGIN_IMAGE) / 2
                info = _medias[1]
                val offsetX = width + MARGIN_IMAGE
                info.rect.set(
                    offsetX.toFloat(),
                    0f,
                    (offsetX + width).toFloat(),
                    heightRight.toFloat()
                )

                val offsetY = heightRight + MARGIN_IMAGE
                info = _medias[2]
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat()
                )

                desiredHeight += height + MARGIN_IMAGE
            }
            4 -> {
                var height = options[0].height
                var width = options[0].width

                if (height > maxHeight) {
                    width = width * maxHeight / height
                    height = maxHeight
                }
                val halfWidth = (contentWidth - MARGIN_IMAGE) / 2
                width = minOf(width, halfWidth)

                var info = _medias[0]
                info.rect.set(
                    0f,
                    0f,
                    width.toFloat(),
                    height.toFloat()
                )

                val heightRight = (height - MARGIN_IMAGE * 2) / 3
                val offsetX = width + MARGIN_IMAGE
                info = _medias[1]
                info.rect.set(
                    offsetX.toFloat(),
                    0f,
                    (offsetX + width).toFloat(),
                    heightRight.toFloat()
                )

                var offsetY = heightRight + MARGIN_IMAGE
                info = _medias[2]
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat(),
                )

                offsetY += heightRight + MARGIN_IMAGE
                info = _medias[3]
                info.rect.set(
                    offsetX.toFloat(),
                    offsetY.toFloat(),
                    (offsetX + width).toFloat(),
                    (offsetY + heightRight).toFloat(),
                )

                desiredHeight += height + MARGIN_IMAGE
            }
            5 -> {
                val widthTop = (contentWidth - MARGIN_IMAGE) / 2
                val widthBottom = (contentWidth - MARGIN_IMAGE * 2) / 3
                val heightTop = widthTop * 4 / 5
                val heightBottom = widthBottom * 5 / 4

                for (i in 0..1) {
                    val offsetX = i * (widthTop + MARGIN_IMAGE)
                    val info = _medias[i]
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + widthTop).toFloat(),
                        heightTop.toFloat()
                    )
                }

                val offsetY = heightTop + MARGIN_IMAGE
                for (i in 2..4) {
                    val offsetX = (i-2) * (widthBottom + MARGIN_IMAGE)
                    val info = _medias[i]
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + widthBottom).toFloat(),
                        (offsetY + heightBottom).toFloat()
                    )
                }

                desiredHeight += heightTop + MARGIN_IMAGE + heightBottom + MARGIN_IMAGE
            }
            6 -> {
                val width = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height = width * 5 / 4

                var offsetX: Int
                var offsetY: Int
                for (i in 0 until 6) {
                    val info = _medias[i]
                    offsetX = (i % 3) * (width + MARGIN_IMAGE)
                    offsetY = if (i >= 3) height + MARGIN_IMAGE else 0
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width).toFloat(),
                        (offsetY + height).toFloat(),
                    )
                }

                desiredHeight += (height + MARGIN_IMAGE) * 2
            }
            7 -> {
                val width2 = (contentWidth - MARGIN_IMAGE) / 2
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3

                val height2 = width2 * 4 / 5
                val height3 = width3 * 5 / 4

                var offsetX: Int
                for (i in 0..1) {
                    val info = _medias[i]
                    offsetX = i * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width2).toFloat(),
                        height2.toFloat()
                    )
                }
                var offsetY: Int = height2 + MARGIN_IMAGE
                for (i in 2..4) {
                    val info = _medias[i]
                    offsetX = (i - 2) * (width3 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat(),
                    )
                }
                offsetY += height3 + MARGIN_IMAGE
                for (i in 5..6) {
                    val info = _medias[i]
                    offsetX = (i - 5) * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width2).toFloat(),
                        (offsetY + height2).toFloat(),
                    )
                }

                desiredHeight += height2 * 2 + height3 + MARGIN_IMAGE * 3
            }
            8 -> {
                val width2 = (contentWidth - MARGIN_IMAGE) / 2
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3

                val height2 = width2 * 4 / 5
                val height3 = width3 * 4 / 5

                for (i in 0..1) {
                    val info = _medias[i]
                    val offsetX = i * (width2 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width2).toFloat(),
                        height2.toFloat()
                    )
                }
                var offsetY = height2 + MARGIN_IMAGE
                for (i in 2..7) {
                    val info = _medias[i]
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
                }

                desiredHeight += height2 + height3 * 2 + MARGIN_IMAGE * 3
            }
            9 -> {
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height3 = width3 * 5 / 4

                var offsetY = 0
                for (i in 0..8) {
                    val offsetX = (i % 3) * (width3 + MARGIN_IMAGE)
                    val info = _medias[i]
                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                    if (((i + 1) % 3) == 0) {
                        offsetY += height3 + MARGIN_IMAGE
                    }
                }

                desiredHeight += height3 * 3 + MARGIN_IMAGE * 3
            }
            // if images have 10 or 10+, then display 10 images only, but show +n indicator on the last image
            else -> {
                val width3 = (contentWidth - MARGIN_IMAGE * 2) / 3
                val height3 = width3 * 5 / 4
                val width4 = (contentWidth - MARGIN_IMAGE * 3) / 4
                val height4 = width4 * 5 / 4
                for (i in 0..2) {
                    val info = _medias[i]
                    val offsetX = i * (width3 + MARGIN_IMAGE)
                    info.rect.set(
                        offsetX.toFloat(),
                        0f,
                        (offsetX + width3).toFloat(),
                        height3.toFloat()
                    )
                }
                var offsetY = height3 + MARGIN_IMAGE
                for (i in 3..6) {
                    val info = _medias[i]
                    val offsetX = (i-3) * (width4 + MARGIN_IMAGE)

                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width4).toFloat(),
                        (offsetY + height4).toFloat()
                    )
                }
                offsetY += height4 + MARGIN_IMAGE
                for (i in 7..9) {
                    val offsetX = (i - 7) * (width3 + MARGIN_IMAGE)
                    val info = _medias[i]

                    info.rect.set(
                        offsetX.toFloat(),
                        offsetY.toFloat(),
                        (offsetX + width3).toFloat(),
                        (offsetY + height3).toFloat()
                    )
                }
                desiredHeight += height3 * 2 + height4 + MARGIN_IMAGE * 3
            }
        }
        return desiredHeight
    }

    private fun measureFilesHeight(contentWidth: Int): Int {
        if (_files.isEmpty()) return 0

        // display max 4 files, if more then 4, then the item 4 for showing the detail fragment
        val fileCount = _files.size.coerceAtMost(MAX_FILE_COUNT)
        for (i in 0 until fileCount) {
            val f = _files[i]
            val top = (MARGIN_FILE * 2 + FILE_ICON_SIZE.dp()) * i.toFloat()
            f.rect.set(
                0F,
                top,
                contentWidth.toFloat(),
                top + MARGIN_FILE * 2 + FILE_ICON_SIZE.dp()
            )
        }
        return fileCount * (FILE_ICON_SIZE.dp() + MARGIN_FILE * 2)
    }

    private fun measureHeight(width: Int) : Int {
        val contentWidth =
            width - MARGIN_BACKGROUND_START - MARGIN_BACKGROUND_END - MARGIN_BORDER * 2
        var desiredHeight = MARGIN_BORDER * 2

        desiredHeight += measureMediasHeight(contentWidth)
        desiredHeight += measureFilesHeight(contentWidth)

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

    private fun getVideoBitmap(info: AttachmentList.AttachmentInfo): Bitmap {
        val file = info.file.md5file
        return if (asyncImageLoad) {
            if (info.cachedBitmap != null) {
                info.cachedBitmap!!
            } else {
                ImageLoader.loadVideoThumbnailAsync(file) { b->
                    Log.v(TAG, "getVideoBitmap: async thumb loaded callback: $b for file=${file.absolutePath}")
                    info.cachedBitmap = b ?: ImageLoader.errorBitmap
                    this@NoteItemView.invalidate()
                }
                ImageLoader.loadingBitmap
            }
        } else {
            ImageLoader.loadImage(file) ?: ImageLoader.errorBitmap
        }
    }

    private fun getImageBitmap(info: AttachmentList.AttachmentInfo): Bitmap {
        val file = info.file.md5file
        return if (asyncImageLoad) {
            if (info.cachedBitmap != null) {
                info.cachedBitmap!!
            } else {
                ImageLoader.loadImageAsync(file) { b->
                    Log.v(TAG, "getFileBitmap: async image loaded callback: $b for file=${file.absolutePath}")
                    info.cachedBitmap = b ?: ImageLoader.errorBitmap
                    this@NoteItemView.invalidate()
                }
                ImageLoader.loadingBitmap
            }
        } else {
            ImageLoader.loadImage(file) ?: ImageLoader.errorBitmap
        }
    }

    private fun Canvas.drawMediaFile(info: AttachmentList.AttachmentInfo, dst: RectF, paint: Paint) {
        val isVideo = info.file.isVideo
        val bitmap = if (isVideo) getVideoBitmap(info) else getImageBitmap(info)

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

        if (isVideo && bitmap != ImageLoader.errorBitmap && bitmap != ImageLoader.loadingBitmap) {
            // draw play icon at center
            val iconSize = (dst.height() / 3).toInt().coerceAtMost(40.dp())

            val iconLeft = dst.left + (dst.width() - iconSize) / 2
            val iconTop = dst.top + (dst.height() - iconSize) / 2
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
            this.drawCircle(cx, cy, radius, videoIconPaint)

            videoIconPaint.style = Paint.Style.STROKE
            videoIconPaint.alpha = 255
            videoIconPaint.color = Color.WHITE
            videoIconPaint.strokeWidth = (iconSize / 6f).coerceAtMost(6f)
            this.drawCircle(cx, cy, radius, videoIconPaint)
            videoIconPaint.style = Paint.Style.FILL

            val side = radius
            videoIconPath.reset()
            videoIconPath.moveTo(cx - side / 3f, cy - side / 2f)
            videoIconPath.lineTo(cx - side / 3f, cy + side / 2f)
            videoIconPath.lineTo(cx + side * 2f / 3f, cy)
            videoIconPath.close()
            this.drawPath(videoIconPath, videoIconPaint)
        }
    }

    private fun drawMedias(canvas: Canvas): Float {
        Log.v(TAG, "drawMedias: total size: ${_medias.size}")

        if (_medias.isEmpty()) return 0f

        for (info in _medias.take(MAX_IMAGE_COUNT)) {
            if (info.rect.isEmpty) {
                Log.e(TAG, "drawing image without setting the image rect is not allowed")
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
            canvas.drawMediaFile(info, imageRect, imagePaint)

            // Reset xfermode
            imagePaint.xfermode = null
            canvas.restoreToCount(saveCount)
        }

        if (_medias.size > MAX_IMAGE_COUNT) {
            val restCount = _medias.size - 9
            val info = _medias[9]
            mediaOverlayRect.set(info.rect)
            // draw the overlay background
            canvas.drawRect(mediaOverlayRect, mediaOverlayPaint)

            val overlayText = "+$restCount"
            mediaOverlayFontPaint.textSize = (mediaOverlayRect.height() / 4)

            val textWidth = mediaOverlayFontPaint.measureText(overlayText)
            val textX = mediaOverlayRect.left + (mediaOverlayRect.width() - textWidth) / 2
            val textY = mediaOverlayRect.top + (mediaOverlayRect.height() + mediaOverlayFontPaint.textSize) / 2 - mediaOverlayFontPaint.descent()

            canvas.drawText(overlayText, textX, textY, mediaOverlayFontPaint)

            return _medias[9].rect.bottom + MARGIN_IMAGE
        } else {
            return _medias.last().rect.bottom + MARGIN_IMAGE
        }
    }

    private fun drawFiles(canvas: Canvas, contentWidth: Int): Float {
        var offsetY: Float = MARGIN_FILE.toFloat()

        for (info in _files.take(MAX_FILE_COUNT)) {
            val file = info.file
            Log.v(TAG, "drawFiles: file=${file.name}, md5=${file.md5}")
            val moreFiles = _files.size > MAX_FILE_COUNT && _files.indexOf(info) == MAX_FILE_COUNT - 1

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
                if (moreFiles) ImageLoader.iconMore else ImageLoader.iconFile,
                null,
                rect,
                fileIconPaint
            )

            // Calculate max lines based on icon height and text size
            val maxLines = ((FILE_ICON_SIZE.dp() - MARGIN_FILENAME * 2) / TEXT_SIZE_FILENAME)
                .toInt().coerceAtLeast(1)

            // draw filename
            val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
            val sourceText: String = if (moreFiles) {
                "+${_files.size - MAX_FILE_COUNT + 1} files"
            } else {
                file.name
            }

            val fileNameLayout = StaticLayout.Builder
                .obtain(
                    sourceText, 0, sourceText.length, fileNamePaint,
                    contentWidth - MARGIN_FILE * 2 - MARGIN_FILENAME - FILE_ICON_SIZE.dp()
                )
                .setAlignment(alignment)
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

            offsetY += FILE_ICON_SIZE.dp() + MARGIN_FILE * 2
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
        canvas.translate(0F, drawMedias(canvas))
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

        (_medias + _files).iterator().forEach { info ->
            val rect = RectF(
                info.rect.left + MARGIN_BACKGROUND_START + MARGIN_BORDER,
                info.rect.top + MARGIN_BACKGROUND_Y + MARGIN_BORDER,
                info.rect.right + MARGIN_BACKGROUND_START + MARGIN_BORDER,
                info.rect.bottom + MARGIN_BACKGROUND_Y + MARGIN_BORDER
            )
            if (rect.contains(x, y)) {
                Log.v(TAG, "checkClickedItem: attachment $info clicked")
                val attachment = info.file
                val type = when (attachment.type) {
                    AttachmentFile.Type.MEDIA -> ClickedContent.Type.IMAGE
                    AttachmentFile.Type.FILE -> ClickedContent.Type.FILE
                }
                val index = when (attachment.type) {
                    AttachmentFile.Type.MEDIA -> _medias.indexOf(info)
                    AttachmentFile.Type.FILE -> _files.indexOf(info)
                }
                return ClickedContent(
                    type,
                    attachment,
                    index
                )
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
        const val MARGIN_BACKGROUND_END = 256
        const val BACKGROUND_RADIUS = 16
        const val BACKGROUND_COLOR = 0x33FFFFFF

        const val MARGIN_BORDER = 8
        const val MARGIN_IMAGE = 8
        const val IMAGE_RADIUS = 14
        const val MAX_IMAGE_COUNT = 10

        const val MARGIN_FILE = 8
        const val FILE_ICON_SIZE = 48  // in dp
        const val FILE_ICON_RADIUS = 36
        const val MARGIN_FILENAME = 16
        const val TEXT_SIZE_FILENAME = 40f
        const val MAX_FILE_COUNT = 4

        const val MARGIN_TEXT = 16
        const val MARGIN_TIME = 8

        const val TEXT_SIZE_CONTENT = 48f
        const val TEXT_SIZE_TIME = 30f

        var sdf: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
    }

}
