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
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.storage.ImageLoader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class NoteItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), SelectableRecyclerView.HighlightAble {

    private val density = context.resources.displayMetrics.density
    private fun Int.dp(): Int = (this * density).toInt()

    var index: Int = -1
    var text: String = ""
    var createTime: Long = 0L
    var isEdited: Boolean = false
    var isDeleted: Boolean = false
    var isSynced: Boolean = false

    @JvmField
    var asyncImageLoad: Boolean = true
    var asyncVideoLoad: Boolean = true

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

    private var bubbleRect: RectF = RectF()
    private var bubblePaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.note_bubble_background)
        style = Paint.Style.FILL
        setShadowLayer(
            4f,
            0f,
            0f,
            ContextCompat.getColor(context, R.color.note_bubble_shadow)
        )
    }

    private var mediaRect: RectF = RectF()
    private val mediaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mediaPath = Path()
    private val videoPlayIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
    }
    private val videoPlayIconPath = Path()
    private val moreMediaOverlayRect = RectF()
    private val moreMediaOverlayBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.more_media_overlay_background)
        style = Paint.Style.FILL
    }
    private val moreMediaOverlayTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.more_media_overlay_text)
    }

    private val fileIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.file_icon_background)
    }
    private val fileNamePaint = TextPaint().apply {
        color = ContextCompat.getColor(context, R.color.color_text_main)
        textSize = TEXT_SIZE_FILENAME
        isAntiAlias = true
    }

    private lateinit var textLayout: StaticLayout
    private val textPaint = TextPaint().apply {
        color = ContextCompat.getColor(context, R.color.color_text_main)
        textSize = TEXT_SIZE_CONTENT
        isAntiAlias = true
    }
    private lateinit var timeLayout: StaticLayout
    private val timePaint: TextPaint = TextPaint().apply {
        color = ContextCompat.getColor(context, R.color.color_text_sub)
        textSize = TEXT_SIZE_TIME
        isAntiAlias = true
    }
    private val iconPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.color_text_sub)
        isAntiAlias = true
    }
    private val extInfoBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.ext_info_mask)
        alpha = 200
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

    private fun getFirstMediaSize(contentWidth: Int) : Size {
        val a = _medias[0]
        val f = a.file.md5file
        if (!f.exists()) {
            Log.e(TAG, "measureImageHeight: image file not exists: ${f.absolutePath}")
            return Size(contentWidth, contentWidth)
        }

        if (a.file.isVideo) {
            // decode video size is time consuming,
            // set to 3:4 ratio directly to improve performance
            return Size(contentWidth, contentWidth * 3 / 4)
        } else {
            val option = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(f.absolutePath, option)
            return Size(option.outWidth, option.outHeight)
        }
    }

    private fun measureMediasHeight(contentWidth: Int): Int {
        val maxHeight = contentWidth * 3 / 2

        var desiredHeight = 0
        when (_medias.size) {
            0 -> return 0
            1 -> {
                val e = getFirstMediaSize(contentWidth)

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
                val height = contentWidth * 3 / 4f
                val width = (contentWidth - MARGIN_IMAGE) / 2f

                var info = _medias[0]
                info.rect.set(0F, 0F, width, height)

                val offsetX = (width + MARGIN_IMAGE)
                info = _medias[1]
                info.rect.set(offsetX, 0F, (offsetX + width), height)

                desiredHeight += (height + MARGIN_IMAGE).toInt()
            }
            3 -> {
                val width = (contentWidth - MARGIN_IMAGE) / 2f
                val heightRight = width * 4 / 3
                val heightLeft = heightRight * 2 + MARGIN_IMAGE

                var info = _medias[0]
                info.rect.set(0F, 0F, width, heightLeft)

                info = _medias[1]
                val offsetX = width + MARGIN_IMAGE
                info.rect.set(offsetX, 0f, (offsetX + width), heightRight)

                val offsetY = heightRight + MARGIN_IMAGE
                info = _medias[2]
                info.rect.set(
                    offsetX,
                    offsetY,
                    (offsetX + width),
                    (offsetY + heightRight)
                )

                desiredHeight += (heightLeft + MARGIN_IMAGE).toInt()
            }
            4 -> {
                val side = (contentWidth - MARGIN_IMAGE) / 2f
                val heightLeft = side * 3 + MARGIN_IMAGE * 2

                var info = _medias[0]
                info.rect.set(0f, 0f, side, heightLeft)

                val offsetX = side + MARGIN_IMAGE
                info = _medias[1]
                info.rect.set(offsetX, 0f, (offsetX + side), side)

                var offsetY = side + MARGIN_IMAGE
                info = _medias[2]
                info.rect.set(offsetX, offsetY, (offsetX + side), (offsetY + side))

                offsetY += side + MARGIN_IMAGE
                info = _medias[3]
                info.rect.set(offsetX, offsetY, (offsetX + side), (offsetY + side))

                desiredHeight += (heightLeft + MARGIN_IMAGE).toInt()
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

    private fun measureFilesHeight(contentWidth: Int, baseHeight: Int): Int {
        if (_files.isEmpty()) return 0

        // display max 4 files, if more then 4, then the item 4 for showing the detail fragment
        val fileCount = _files.size.coerceAtMost(MAX_FILE_COUNT)
        for (i in 0 until fileCount) {
            val f = _files[i]
            val top = (MARGIN_FILE * 2 + FILE_ICON_SIZE.dp()) * i.toFloat() + baseHeight
            f.rect.set(
                0F,
                top,
                contentWidth.toFloat(),
                top + MARGIN_FILE * 2 + FILE_ICON_SIZE.dp()
            )
        }
        return fileCount * (FILE_ICON_SIZE.dp() + MARGIN_FILE * 2)
    }

    private fun measureIconWidth(size: Int): Int {
        var totalWidth = MARGIN_ICON

        if (isEdited) totalWidth += size + MARGIN_ICON
        if (isDeleted) totalWidth += size + MARGIN_ICON
        if (!isSynced) totalWidth += size + MARGIN_ICON

        return totalWidth - MARGIN_ICON
    }

    private fun measureBubbleMixed(width: Int) : Int {
        val bubbleMaxWidth = width - MARGIN_BUBBLE_START - MARGIN_BUBBLE_END
        val contentMaxWidth = bubbleMaxWidth - MARGIN_BORDER * 2
        var bubbleHeight = MARGIN_BORDER

        bubbleHeight += measureMediasHeight(contentMaxWidth)
        bubbleHeight += measureFilesHeight(contentMaxWidth, bubbleHeight)

        if (!text.isEmpty()) {
            textLayout = StaticLayout.Builder
                .obtain(
                    text, 0, text.length, textPaint,
                    contentMaxWidth - (MARGIN_TEXT * 2)
                )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            bubbleHeight += textLayout.height + MARGIN_TEXT * 2
        }

        val timeText = createTime.format()
        timeLayout = StaticLayout.Builder
            .obtain(
                timeText, 0, timeText.length, timePaint,
                contentMaxWidth - (MARGIN_TIME * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val lastLineWidth = textLayout.getLineWidth(textLayout.lineCount - 1)
        val iconWidth = measureIconWidth(timeLayout.height)
        val timeWidth = timeLayout.getLineWidth(0)
        val extInfoWidth = iconWidth + timeWidth
        val combinedWidth = lastLineWidth + MARGIN_TEXT + extInfoWidth + MARGIN_TIME
        if (combinedWidth > contentMaxWidth) {
            bubbleHeight += timeLayout.height + MARGIN_TIME
        }

        bubbleHeight += MARGIN_BORDER

        val bubbleWidth = contentMaxWidth + MARGIN_BORDER * 2

        bubbleRect.set(
            MARGIN_BUBBLE_START.toFloat(),
            MARGIN_BUBBLE_Y.toFloat(),
            (MARGIN_BUBBLE_START + bubbleWidth).toFloat(),
            (MARGIN_BUBBLE_Y + bubbleHeight).toFloat()
        )

        return bubbleHeight
    }

    private fun measureBubbleMediaText(width: Int) : Int {
        val bubbleMaxWidth = width - MARGIN_BUBBLE_START - MARGIN_BUBBLE_END
        val contentMaxWidth = bubbleMaxWidth - MARGIN_BORDER * 2
        var bubbleHeight = MARGIN_BORDER * 2

        measureMediasHeight(contentMaxWidth)
        val last = _medias.last().rect
        val right = last.right
        val bottom = last.bottom
        val mediaWidth = right
        var bubbleWidth = mediaWidth + MARGIN_BORDER * 2

        bubbleHeight += bottom.toInt()

        textLayout = StaticLayout.Builder
            .obtain(
                text, 0, text.length, textPaint,
                contentMaxWidth - (MARGIN_TEXT * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        bubbleHeight += textLayout.height + MARGIN_TEXT * 2

        val timeText = createTime.format()
        timeLayout = StaticLayout.Builder
            .obtain(
                timeText, 0, timeText.length, timePaint,
                contentMaxWidth - (MARGIN_TIME * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        var maxTextLayoutWidth = 0f // include the margin later
        for (i in 0 until textLayout.lineCount - 1) {
            maxTextLayoutWidth = maxOf(textLayout.getLineWidth(i), maxTextLayoutWidth)
        }

        val lastLineWidth = textLayout.getLineWidth(textLayout.lineCount - 1)
        val iconWidth = measureIconWidth(timeLayout.height)
        val timeWidth = timeLayout.getLineWidth(0)
        val extInfoWidth = timeWidth + iconWidth
        val combinedWidth = lastLineWidth + extInfoWidth + MARGIN_TEXT + MARGIN_TIME
        if (combinedWidth > contentMaxWidth) {
            maxTextLayoutWidth = maxOf(lastLineWidth, maxTextLayoutWidth)
            maxTextLayoutWidth += MARGIN_TEXT * 2
            maxTextLayoutWidth = maxOf(maxTextLayoutWidth, extInfoWidth + MARGIN_TIME * 2)
            bubbleHeight += timeLayout.height + MARGIN_TIME
        } else {
            maxTextLayoutWidth += MARGIN_TEXT * 2
            maxTextLayoutWidth = maxOf(maxTextLayoutWidth, combinedWidth)
        }


        if (mediaWidth < maxTextLayoutWidth) {
            // only single media case can get into this branch
            bubbleWidth = maxTextLayoutWidth + MARGIN_BORDER * 2
            val rect0 = _medias[0].rect
            rect0.set(rect0.left, rect0.top,
                rect0.left + maxTextLayoutWidth,
                rect0.bottom
            )
        }
        bubbleRect.set(
            MARGIN_BUBBLE_START.toFloat(),
            MARGIN_BUBBLE_Y.toFloat(),
            MARGIN_BUBBLE_START + bubbleWidth,
            (MARGIN_BUBBLE_Y + bubbleHeight).toFloat()
        )

        return bubbleHeight
    }

    private var extInfoNeedBackground = false
    private fun measureBubbleMediaOnly(width: Int) : Int {
        val bubbleMaxWidth = width - MARGIN_BUBBLE_START - MARGIN_BUBBLE_END
        val contentMaxWidth = bubbleMaxWidth - MARGIN_BORDER * 2
        var bubbleHeight = MARGIN_BORDER * 2

        measureMediasHeight(contentMaxWidth)

        val last = _medias.last().rect
        val right = last.right
        val bottom = last.bottom

        bubbleHeight += bottom.toInt()
        var bubbleWidth = right + MARGIN_BORDER * 2

        val timeText = createTime.format()
        timeLayout = StaticLayout.Builder
            .obtain(
                timeText, 0, timeText.length, timePaint,
                contentMaxWidth - (MARGIN_TIME * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val iconsWidth = measureIconWidth(timeLayout.height)
        val extInfoWidth = iconsWidth + timeLayout.getLineWidth(0)
        val extInfoWidthWithMargin = extInfoWidth + MARGIN_IMAGE * 2
        val neededInfoWidth = MARGIN_BORDER * 2 + extInfoWidthWithMargin
        if (neededInfoWidth > bubbleWidth) {
            // for now, only single media case can go into this branch
            bubbleWidth = neededInfoWidth
            val rect0 = _medias[0].rect
            rect0.set(rect0.left, rect0.top,
                rect0.left + extInfoWidthWithMargin,
                rect0.bottom
            )
        }

        bubbleRect.set(
            MARGIN_BUBBLE_START.toFloat(),
            MARGIN_BUBBLE_Y.toFloat(),
            MARGIN_BUBBLE_START + bubbleWidth,
            (MARGIN_BUBBLE_Y + bubbleHeight).toFloat()
        )
        extInfoNeedBackground = true

        return bubbleHeight
    }

    private fun measureBubbleTextOnly(width: Int) : Int {
        val bubbleMaxWidth = width - MARGIN_BUBBLE_START - MARGIN_BUBBLE_END
        val contentMaxWidth = bubbleMaxWidth - MARGIN_BORDER * 2
        var bubbleHeight = MARGIN_BORDER * 2

        textLayout = StaticLayout.Builder
            .obtain(
                text, 0, text.length, textPaint,
                contentMaxWidth - (MARGIN_TEXT * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
        bubbleHeight += textLayout.height + MARGIN_TEXT * 2

        val timeText = createTime.format()
        timeLayout = StaticLayout.Builder
            .obtain(
                timeText, 0, timeText.length, timePaint,
                contentMaxWidth - (MARGIN_TIME * 2)
            )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()

        val iconsWidth = measureIconWidth(timeLayout.height)
        val extInfoWidth = iconsWidth + timeLayout.getLineWidth(0)

        var contentWidth = 0f

        for (i in 0 until textLayout.lineCount - 1) {
            contentWidth = maxOf(contentWidth, textLayout.getLineWidth(i))
        }

        val lastLineWidth = textLayout.getLineWidth(textLayout.lineCount-1)
        val combineWidth = lastLineWidth + MARGIN_TEXT + extInfoWidth + MARGIN_TIME

        if (combineWidth > contentMaxWidth) {
            contentWidth = maxOf(contentWidth, lastLineWidth)
            contentWidth = if (contentWidth + MARGIN_TIME * 2 > extInfoWidth + MARGIN_TIME * 2) {
                contentWidth
            } else {
                extInfoWidth
            }
            bubbleHeight += timeLayout.height + MARGIN_TIME
        } else {
            contentWidth = maxOf(contentWidth, combineWidth)
        }

        val bubbleWidth = contentWidth + MARGIN_TEXT * 2 + MARGIN_BORDER * 2

        bubbleRect.set(
            MARGIN_BUBBLE_START.toFloat(),
            MARGIN_BUBBLE_Y.toFloat(),
            (MARGIN_BUBBLE_START + bubbleWidth),
            (MARGIN_BUBBLE_Y + bubbleHeight).toFloat()
        )

        return bubbleHeight
    }

    private enum class ContentStatus {
        ONLY_TEXT,
        ONLY_MEDIA,
        ONLY_FILE,
        MEDIA_TEXT,
        MIXED
    }
    private fun checkContentStatus(): ContentStatus {
        val hasText = !text.isEmpty()
        val hasMedia = _medias.isNotEmpty()
        val hasFile = _files.isNotEmpty()

        return when {
            hasText && !hasMedia && !hasFile -> ContentStatus.ONLY_TEXT
            !hasText && hasMedia && !hasFile -> ContentStatus.ONLY_MEDIA
            !hasText && !hasMedia && hasFile -> ContentStatus.ONLY_FILE
            hasText && hasMedia && !hasFile -> ContentStatus.MEDIA_TEXT
            else -> ContentStatus.MIXED
        }
    }
    private fun measureHeight(width: Int) : Int {
        extInfoNeedBackground = false
        return MARGIN_BUBBLE_Y * 2 + when (checkContentStatus()) {
            ContentStatus.ONLY_TEXT -> {
                measureBubbleTextOnly(width)
            }
            ContentStatus.ONLY_MEDIA -> {
                measureBubbleMediaOnly(width)
            }
            ContentStatus.MEDIA_TEXT -> {
                measureBubbleMediaText(width)
            }
            else -> {
                measureBubbleMixed(width)
            }
        }
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
        return if (asyncVideoLoad) {
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
            videoPlayIconPaint.style = Paint.Style.FILL
            videoPlayIconPaint.color = ContextCompat.getColor(context, R.color.video_play_icon_background)
            this.drawCircle(cx, cy, radius, videoPlayIconPaint)

            videoPlayIconPaint.style = Paint.Style.STROKE
            videoPlayIconPaint.alpha = 255
            videoPlayIconPaint.color = ContextCompat.getColor(context, R.color.video_play_icon_foreground)
            videoPlayIconPaint.strokeWidth = (iconSize / 6f).coerceAtMost(6f)
            this.drawCircle(cx, cy, radius, videoPlayIconPaint)
            videoPlayIconPaint.style = Paint.Style.FILL

            val side = radius
            videoPlayIconPath.reset()
            videoPlayIconPath.moveTo(cx - side / 3f, cy - side / 2f)
            videoPlayIconPath.lineTo(cx - side / 3f, cy + side / 2f)
            videoPlayIconPath.lineTo(cx + side * 2f / 3f, cy)
            videoPlayIconPath.close()
            this.drawPath(videoPlayIconPath, videoPlayIconPaint)
        }
    }

    private fun Canvas.drawMedias(contentWidth: Int): Float {
        Log.v(TAG, "drawMedias: total size: ${_medias.size}")

        if (_medias.isEmpty()) return 0f

        if (_medias[0].rect.isEmpty) {
            Log.w(TAG, "drawMedias: media rect is empty, need to measure again")
            measureMediasHeight(contentWidth)
        }

        var offsetY: Float
        for (info in _medias.take(MAX_IMAGE_COUNT)) {
            if (info.rect.isEmpty) {
                Log.e(TAG, "drawing image without setting the image rect is not allowed")
                continue
            }
            mediaRect.set(info.rect)

            // Draw bitmap with rounded corners
            val saveCount = saveLayer(mediaRect, null)

            // Draw rounded rect as mask
            mediaPath.reset()
            mediaPath.addRoundRect(
                mediaRect,
                IMAGE_RADIUS.toFloat(),
                IMAGE_RADIUS.toFloat(),
                Path.Direction.CW
            )
            drawPath(mediaPath, mediaPaint)

            // Set xfermode to only draw bitmap where the rounded rect was drawn
            mediaPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            drawMediaFile(info, mediaRect, mediaPaint)

            // Reset xfermode
            mediaPaint.xfermode = null
            restoreToCount(saveCount)
        }

        if (_medias.size > MAX_IMAGE_COUNT) {
            val restCount = _medias.size - 9
            val info = _medias[9]
            moreMediaOverlayRect.set(info.rect)
            // draw the overlay background
            drawRect(moreMediaOverlayRect, moreMediaOverlayBackgroundPaint)

            val overlayText = "+$restCount"
            moreMediaOverlayTextPaint.textSize = (moreMediaOverlayRect.height() / 4)

            val textWidth = moreMediaOverlayTextPaint.measureText(overlayText)
            val textX =
                moreMediaOverlayRect.left + (moreMediaOverlayRect.width() - textWidth) / 2
            val textY =
                moreMediaOverlayRect.top + (moreMediaOverlayRect.height() + moreMediaOverlayTextPaint.textSize) / 2 - moreMediaOverlayTextPaint.descent()

            drawText(overlayText, textX, textY, moreMediaOverlayTextPaint)

            offsetY =  _medias[9].rect.bottom + MARGIN_IMAGE
        } else {
            offsetY = _medias.last().rect.bottom + MARGIN_IMAGE
        }
        return offsetY
    }

    private fun Canvas.drawFiles(contentWidth: Int): Float {
        var offsetY: Float = MARGIN_FILE.toFloat()

        if (_files.isEmpty()) return 0f
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
            drawRoundRect(
                rect,
                FILE_ICON_RADIUS.toFloat(),
                FILE_ICON_RADIUS.toFloat(),
                fileIconPaint
            )

            drawBitmap(
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
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            withTranslation(
                (MARGIN_FILE + MARGIN_FILENAME + FILE_ICON_SIZE.dp()).toFloat(),
                offsetY + MARGIN_FILENAME
            ) {
                fileNameLayout.draw(this)
            }

            offsetY += FILE_ICON_SIZE.dp() + MARGIN_FILE * 2
        }

        return offsetY
    }

    private fun Canvas.drawIcons(size: Float): Float {
        var offsetX = 0f
        // TODO: cache the images?
        if (isDeleted) {
            drawBitmap(
                ImageLoader.loadDrawable(context, R.drawable.icon_common_remove,
                    ContextCompat.getColor(context, R.color.color_text_sub)
                    ),
                null,
                RectF(
                    offsetX, 0f,
                    offsetX + size, size
                ),
                iconPaint
            )
            offsetX += size + MARGIN_ICON
        }
        if (!isSynced) {
            drawBitmap(
                ImageLoader.loadDrawable(context, R.drawable.icon_common_not_sync,
                    ContextCompat.getColor(context, R.color.color_text_sub)
                ),
                null,
                RectF(
                    offsetX, 0f,
                    offsetX + size, size
                ),
                iconPaint
            )
            offsetX += size + MARGIN_ICON
        }
        if (isEdited) {
            drawBitmap(
                ImageLoader.loadDrawable(context, R.drawable.icon_common_edit,
                    ContextCompat.getColor(context, R.color.color_text_sub)
                ),
                null,
                RectF(
                    offsetX, 0f,
                    offsetX + size, size
                ),
                iconPaint
            )
            offsetX += size + MARGIN_ICON
        }
        return offsetX
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.v(TAG, "onDraw: $index")

        canvas.drawRoundRect(
            bubbleRect,
            BUBBLE_RADIUS.toFloat(),
            BUBBLE_RADIUS.toFloat(),
            bubblePaint
        )

        val offX = bubbleRect.left + MARGIN_BORDER
        var offY = bubbleRect.top + MARGIN_BORDER

        val contentWidth: Int = (bubbleRect.width() - MARGIN_BORDER * 2).toInt()
        canvas.withTranslation(offX, offY) {
            offY += drawMedias(contentWidth)
        }
        canvas.withTranslation(offX, offY) {
            offY += drawFiles(contentWidth)
        }

        if (!text.isEmpty()) {
            offY += MARGIN_TEXT
            canvas.withTranslation(offX + MARGIN_TEXT, offY) {
                textLayout.draw(canvas)
            }
        }

        val timeX = bubbleRect.right - MARGIN_BORDER - MARGIN_TIME - timeLayout.getLineWidth(0)
        val timeY = bubbleRect.bottom - MARGIN_BORDER - MARGIN_TIME - timeLayout.height
        val iconX = timeX - measureIconWidth(timeLayout.height)
        if (extInfoNeedBackground) {
            canvas.drawRoundRect(
                iconX - MARGIN_TIME, timeY - MARGIN_TIME,
                bubbleRect.right - MARGIN_BORDER,
                bubbleRect.bottom - MARGIN_BORDER,
                IMAGE_RADIUS.toFloat(), IMAGE_RADIUS.toFloat(),
                extInfoBackgroundPaint
            )
        }

        canvas.withTranslation(timeX, timeY) {
            timeLayout.draw(this)
        }
        canvas.withTranslation(iconX, timeY) {
            drawIcons(timeLayout.height.toFloat())
        }

    }

    private val highlightPath = Path()
    override fun getHighlightArea(): Path {
        highlightPath.reset()
        highlightPath.addRoundRect(
            bubbleRect,
            BUBBLE_RADIUS.toFloat(),
            BUBBLE_RADIUS.toFloat(),
            Path.Direction.CW
        )
        return highlightPath
    }

    class ClickedContent(val type: Type, val data: AttachmentFile? = null, val index: Int = -1) {
        enum class Type {
            BACKGROUND,
            MEDIA,
            FILE,
        }
    }

    fun checkClickedContent(x: Float, y: Float): ClickedContent {
        Log.v(TAG, "index-$index, checkClickedItem: x=$x, y=$y")

        val attachments = (_medias + _files)
        attachments.iterator().forEach { info ->
            val checkRect = RectF(
                info.rect.left + MARGIN_BUBBLE_START + MARGIN_BORDER,
                info.rect.top + MARGIN_BUBBLE_Y + MARGIN_BORDER,
                info.rect.right + MARGIN_BUBBLE_START + MARGIN_BORDER,
                info.rect.bottom + MARGIN_BUBBLE_Y + MARGIN_BORDER
            )
            if (checkRect.contains(x, y)) {
                Log.v(TAG, "checkClickedItem: attachment $info clicked")
                val attachment = info.file
                val type = when (attachment.type) {
                    AttachmentFile.Type.MEDIA -> ClickedContent.Type.MEDIA
                    AttachmentFile.Type.FILE -> ClickedContent.Type.FILE
                }
                return ClickedContent(
                    type,
                    attachment,
                    attachments.indexOf(info)
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

        const val MARGIN_BUBBLE_Y = 4
        const val MARGIN_BUBBLE_START = 64
        const val MARGIN_BUBBLE_END = 256
        const val BUBBLE_RADIUS = 16

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

        const val MARGIN_ICON = 8

        const val MARGIN_TEXT = 16
        const val MARGIN_TIME = 8

        const val TEXT_SIZE_CONTENT = 48f
        const val TEXT_SIZE_TIME = 30f

        var sdf: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
    }

}
