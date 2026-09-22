package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.VelocityTracker
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import androidx.compose.ui.graphics.asImageBitmap
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.ImageLoader
import cn.alvkeke.dropto.ui.activity.ShareRecvActivity
import cn.alvkeke.dropto.ui.comonent.NoteItemChild.FileItemView
import cn.alvkeke.dropto.ui.comonent.NoteItemChild.MediaItemView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import androidx.core.graphics.drawable.toDrawable


class NoteItemView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val avatarView: ShapeableImageView
    private val senderView: TextView
    private val timeView: TextView
    private val statusContainer: LinearLayout
    private val editedView: ImageView
    private val deletedView: ImageView
    private val unsyncedView: ImageView
    private val textView: TextView
    private val mediaScroll: HorizontalScrollView
    private val mediaContainer: LinearLayout
    private val fileContainer: LinearLayout
    private val reactionContainer: ChipGroup

    init {
        LayoutInflater.from(context).inflate(R.layout.rlist_item_note, this, true)
        avatarView = findViewById(R.id.note_item_avatar)
        senderView = findViewById(R.id.note_item_sender_name)
        timeView = findViewById(R.id.note_item_create_time)
        statusContainer = findViewById(R.id.note_item_status_container)
        editedView = findViewById(R.id.note_item_status_edited)
        deletedView = findViewById(R.id.note_item_status_deleted)
        unsyncedView = findViewById(R.id.note_item_status_unsynced)
        textView = findViewById(R.id.note_item_text)
        mediaScroll = findViewById(R.id.note_item_media_scroll)
        mediaContainer = findViewById(R.id.note_item_media_container)
        fileContainer = findViewById(R.id.note_item_file_container)
        reactionContainer = findViewById(R.id.note_item_reaction_container)
    }

    var note: NoteItem? = null
        set(value) {
            field = value
            bindNote(value)
        }

    private fun bindNote(note: NoteItem?) {
        textView.text = note?.text.orEmpty()
        textView.isVisible = !note?.text.isNullOrEmpty()
        timeView.text = note?.createTime?.format().orEmpty()
        editedView.isVisible = note?.isEdited == true
        deletedView.isVisible = note?.isDeleted == true
        unsyncedView.isVisible = note?.isSynced == false
        statusContainer.isVisible = note != null &&
                (note.isEdited || note.isDeleted || !note.isSynced)
        bindSender(note?.sender)
        rebuildMedias(note?.medias.orEmpty())
        rebuildFiles(note?.files.orEmpty(), note?.medias?.size ?: 0)
        rebuildReactions(note?.reactions.orEmpty())
    }

    private fun bindSender(sender: String?) {
        if (sender == null) {
            avatarView.setImageDrawable(appIcon)
            senderView.text = context.getString(R.string.string_note_sender_you)
            avatarView.tag = null
            return
        }

        avatarView.setImageDrawable(getSenderIcon(sender))
        senderView.text = getSenderLabel(sender)
        avatarView.tag = ClickedContent(ClickedContent.Type.SENDER_ICON)
    }

    var eventListener: EventListener? = null

    private fun rebuildMedias(medias: List<AttachmentFile>) {
        mediaScroll.scrollTo(0, 0)
        mediaContainer.removeAllViews()

        medias.forEachIndexed { position, file ->
            val cell = MediaItemView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dimenPx(R.dimen.size_note_item_media_default_width),
                    dimenPx(R.dimen.size_note_item_media_height)
                ).apply {
                    marginEnd = dimenPx(R.dimen.margin_note_item_element)
                }
                isVideo = file.isVideo
                tag = ClickedContent(ClickedContent.Type.MEDIA, file, position)
                mediaSizeCache[file.md5file.absolutePath]?.let {
                    setMediaCellWidth(this, mediaCellWidth(it))
                }
                loadMediaThumbnail(file, this)
            }
            mediaContainer.addView(cell)
        }
        mediaScroll.isVisible = medias.isNotEmpty()
    }

    private val touchSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val longPressTimeout: Long by lazy {
        ViewConfiguration.getLongPressTimeout().toLong()
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var lastMoveX = 0f
    private var axis = TouchAxis.NONE
    private var draggingMedias = false
    private var draggingPage = false
    private var pageDraggingAccepted = false
    private var longPressHandled = false
    private var longPressSliding = false
    private var pressedContent = ClickedContent(ClickedContent.Type.NONE)
    private var pressedInMedias = false                 // the finger is on the attachments strip
    private var longPressRunnable: Runnable? = null
    private var velocityTracker: VelocityTracker? = null

    private enum class TouchAxis { NONE, HORIZONTAL, VERTICAL }

    /** the item itself owns every touch of its own area, the children never take one */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startTouch(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> return moveTouch(event)
            MotionEvent.ACTION_UP -> {
                endTouch(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                endTouch(true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startTouch(event: MotionEvent) {
        downRawX = event.rawX
        downRawY = event.rawY
        lastRawX = downRawX
        lastRawY = downRawY
        lastMoveX = downRawX
        pressedContent = findClickedContent(event.x, event.y)
        axis = TouchAxis.NONE
        draggingMedias = false
        draggingPage = false
        pageDraggingAccepted = false
        longPressHandled = false
        longPressSliding = false

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }

        val runnable = Runnable {
            longPressRunnable = null
            if (axis != TouchAxis.NONE || longPressHandled || note == null) {
                return@Runnable
            }

            longPressHandled = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            // the list must not scroll while the notes are selected by the sliding
            parent?.requestDisallowInterceptTouchEvent(true)
            longPressSliding = dispatchLongClick()
        }
        longPressRunnable = runnable
        postDelayed(runnable, longPressTimeout)
    }

    private fun resetTouch() {
        cancelLongPress()
        axis = TouchAxis.NONE
        draggingMedias = false
        draggingPage = false
        pageDraggingAccepted = false
        longPressHandled = false
        longPressSliding = false
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun moveTouch(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)
        lastRawX = event.rawX
        lastRawY = event.rawY

        if (longPressHandled) {
            if (longPressSliding) {
                eventListener?.onLongPressDrag(lastRawX.toInt(), lastRawY.toInt())
            }
            return true
        }

        val deltaX = lastRawX - downRawX
        val deltaY = lastRawY - downRawY

        if (axis == TouchAxis.NONE) {
            if (abs(deltaX) <= touchSlop && abs(deltaY) <= touchSlop) {
                return true
            }
            cancelLongPress()

            if (abs(deltaY) > abs(deltaX)) {
                // the list owns the vertical scrolling, it can take the gesture over
                axis = TouchAxis.VERTICAL
                return false
            }

            axis = TouchAxis.HORIZONTAL
            // the list must not take the gesture while the contents are dragged
            parent?.requestDisallowInterceptTouchEvent(true)

            draggingMedias = pressedInMedias && canScrollMedias(-deltaX.toInt())
            draggingPage = !draggingMedias
            if (draggingPage) {
                pageDraggingAccepted = eventListener?.onDragStart(
                    downRawX.toInt(), downRawY.toInt()
                ) ?: false
            }
        }

        if (axis == TouchAxis.VERTICAL) {
            return false
        }

        if (draggingMedias) {
            val step = (lastRawX - lastMoveX).toInt()
            lastMoveX = lastRawX
            mediaScroll.scrollBy(-step, 0)
            return true
        }

        if (draggingPage && pageDraggingAccepted) {
            eventListener?.onDragging(lastRawX.toInt(), lastRawY.toInt())
        }
        return true
    }


    private fun endTouch(cancelled: Boolean) {
        velocityTracker?.computeCurrentVelocity(1000)
        val speedX = (velocityTracker?.xVelocity ?: 0f) / 1000f
        val speedY = (velocityTracker?.yVelocity ?: 0f) / 1000f

        if (longPressHandled) {
            longPressHandled = false
            if (longPressSliding) eventListener?.onLongPressRelease()
        } else if (axis == TouchAxis.NONE && !cancelled) {
            dispatchClick()
        } else if (axis == TouchAxis.HORIZONTAL) {
            if (draggingMedias) {
                if (!cancelled) {
                    mediaScroll.fling(-(speedX * 1000).toInt())
                }
            } else if (draggingPage && pageDraggingAccepted) {
                eventListener?.onDragEnd(lastRawX.toInt(), lastRawY.toInt(), speedX, speedY)
            }
        }

        resetTouch()
    }


    /** what a content of the item is, the tags of the children are [ClickedContent] */
    private class ClickedContent(val type: Type, val data: AttachmentFile? = null, val index: Int = -1) {
        enum class Type {
            NONE,
            SENDER_ICON,
            MEDIA,
            FILE,
            REACTION,
        }
    }

    private val clickedContent = ClickedContent(ClickedContent.Type.NONE)

    private fun findClickedContent(x: Float, y: Float): ClickedContent {
        var view: View? = findHitView(this, x, y)
        var content: ClickedContent? = null
        pressedInMedias = false

        while (view != null && view !== this) {
            if (view === mediaScroll) pressedInMedias = true
            if (content == null) content = view.tag as? ClickedContent
            view = view.parent as? View
        }
        return content ?: clickedContent
    }

    private fun findHitView(group: ViewGroup, x: Float, y: Float): View? {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue

            val childX = x + group.scrollX - child.left
            val childY = y + group.scrollY - child.top
            if (childX < 0f || childY < 0f ||
                childX >= child.width.toFloat() || childY >= child.height.toFloat()
            ) {
                continue
            }

            if (child is ViewGroup) {
                return findHitView(child, childX, childY) ?: child
            }
            return child
        }
        return null
    }

    /** the click of the content which was hit by [startTouch] */
    private fun dispatchClick() {
        val listener = eventListener ?: return
        val note = this.note ?: return
        val content = pressedContent
        val anchorY = lastRawY.toInt()
        when (content.type) {
            ClickedContent.Type.SENDER_ICON -> listener.onSenderClick(note)
            ClickedContent.Type.MEDIA -> content.data?.let { listener.onMediaClick(note, it) }
            ClickedContent.Type.FILE -> content.data?.let { listener.onFileClick(note, it) }
            ClickedContent.Type.REACTION -> {
                listener.onReactionClick(note, reactionOf(content.index))
            }
            else -> listener.onBackgroundClick(note, anchorY)
        }
    }

    /**
     * The long press of the content which was hit by [startTouch].
     * @return true when the sliding which follows has to be reported, the contents which
     *         open something (a card, a filter) do not want the selection to start
     */
    private fun dispatchLongClick(): Boolean {
        val listener = eventListener ?: return false
        val note = this.note ?: return false
        val content = pressedContent
        val anchorY = lastRawY.toInt()
        return when (content.type) {
            ClickedContent.Type.MEDIA -> {
                content.data?.let { listener.onMediaLongClick(note, it, anchorY) }
                false
            }
            ClickedContent.Type.FILE -> {
                content.data?.let { listener.onFileLongClick(note, it, anchorY) }
                false
            }
            ClickedContent.Type.REACTION -> {
                listener.onReactionLongClick(note, reactionOf(content.index))
                false
            }
            else -> {
                listener.onItemLongPress(note, anchorY)
                true
            }
        }
    }

    private fun reactionOf(position: Int): String =
        this.note?.reactions?.getOrNull(position) ?: ""

    private fun canScrollMedias(delta: Int): Boolean {
        if (mediaScroll.visibility != View.VISIBLE) return false

        val max = (mediaContainer.width - mediaScroll.width).coerceAtLeast(0)
        val target = mediaScroll.scrollX + delta
        return target in 0..max && target != mediaScroll.scrollX
    }

    override fun cancelLongPress() {
        longPressRunnable?.let { removeCallbacks(it) }
        longPressRunnable = null
    }

    override fun onDetachedFromWindow() {
        cancelLongPress()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }


    private fun rebuildFiles(files: List<AttachmentFile>, mediaCount: Int) {
        fileContainer.removeAllViews()

        files.forEachIndexed { position, file ->
            val row = FileItemView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                fileName = file.name
                tag = ClickedContent(
                    ClickedContent.Type.FILE, file, mediaCount + position
                )
            }
            fileContainer.addView(row)
        }
        fileContainer.isVisible = files.isNotEmpty()
    }

    private fun rebuildReactions(reactions: List<String>) {
        reactionContainer.removeAllViews()

        reactions.forEachIndexed { position, reaction ->
            reactionContainer.addView(createReactionChip(reaction, position))
        }
        reactionContainer.isVisible = reactions.isNotEmpty()
    }

    private fun createReactionChip(reaction: String, position: Int): Chip {
        return Chip(context).apply {
            isClickable = false
            isLongClickable = false
            isCheckable = false
            isFocusable = false
            setEnsureMinTouchTargetSize(false)   // no public getter, cannot be used as a property

            text = reaction
            setTextColor(context.getColor(R.color.color_text_main))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_REACTION)
            chipBackgroundColor = ColorStateList.valueOf(context.getColor(R.color.reaction_background))
            chipStrokeWidth = 0f
            chipMinHeight = dimenPx(R.dimen.size_note_item_reaction_height).toFloat()
            val padding = dimenPx(R.dimen.padding_note_item_reaction)
            chipStartPadding = padding.toFloat()
            chipEndPadding = padding.toFloat()
            textStartPadding = 0f
            textEndPadding = 0f

            tag = ClickedContent(ClickedContent.Type.REACTION, null, position)
        }
    }

    private fun loadMediaThumbnail(file: AttachmentFile, cell: MediaItemView) {
        val requestedPath = file.md5file.absolutePath
        // the view can be reused for another attachment before the bitmap is loaded,
        // so remember what was requested here and check it in the callback
        cell.boundPath = requestedPath
        cell.thumbnail = ImageLoader.loadingBitmap.asImageBitmap()

        val listener = ImageLoader.ImageLoadListener { bitmap ->
            if (cell.boundPath != requestedPath) {
                Log.v(TAG, "thumbnail of $requestedPath is not needed anymore, drop it")
            } else if (bitmap == null) {
                cell.thumbnail = ImageLoader.errorBitmap.asImageBitmap()
            } else {
                applyMediaThumbnail(cell, requestedPath, bitmap)
            }
        }
        val cached = if (file.isVideo) {
            ImageLoader.loadVideoThumbnailAsync(file.md5file, false, listener)
        } else {
            ImageLoader.loadImageAsync(file.md5file, false, listener)
        }
        if (cached != null) {
            applyMediaThumbnail(cell, requestedPath, cached)
        }
    }

    private fun applyMediaThumbnail(cell: MediaItemView, path: String, bitmap: Bitmap) {
        val size = Size(bitmap.width, bitmap.height)
        if (size.width > 0 && size.height > 0) {
            if (mediaSizeCache.size >= MEDIA_SIZE_CACHE_LIMIT) {
                mediaSizeCache.clear()
            }
            mediaSizeCache[path] = size
            setMediaCellWidth(cell, mediaCellWidth(size))
        }
        cell.thumbnail = bitmap.asImageBitmap()
    }

    private fun mediaCellWidth(size: Size): Int {
        val width = dimenPx(R.dimen.size_note_item_media_height) * size.width / size.height
        return width.coerceIn(
            dimenPx(R.dimen.size_note_item_media_min_width),
            dimenPx(R.dimen.size_note_item_media_max_width)
        )
    }

    private fun dimenPx(id: Int): Int = resources.getDimensionPixelSize(id)

    private fun setMediaCellWidth(cell: View, width: Int) {
        val params = cell.layoutParams ?: return
        if (params.width == width) return

        params.width = width
        cell.layoutParams = params
    }

    private fun getSenderIcon(sender: String): Drawable {
        if (sender == ShareRecvActivity.UNKNOWN_SENDER_PACKAGE) {
            return unknownSenderIcon
        }
        senderIconCache[sender]?.let { return it.freshCopy() }

        val icon = try {
            context.packageManager.getApplicationIcon(sender)
        } catch (e: Exception) {
            Log.e(TAG, "getSenderIcon: failed to get the icon of $sender, error: $e")
            unknownSenderIcon
        }
        senderIconCache[sender] = icon
        return icon.freshCopy()
    }

    private fun getSenderLabel(sender: String): String {
        if (sender == ShareRecvActivity.UNKNOWN_SENDER_PACKAGE) {
            return context.getString(R.string.string_note_sender_unknown)
        }
        senderLabelCache[sender]?.let { return it }

        val label = try {
            val info = context.packageManager.getApplicationInfo(sender, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            Log.e(TAG, "getSenderLabel: failed to get the label of $sender, error: $e")
            sender
        }
        senderLabelCache[sender] = label
        return label
    }

    /** a drawable instance cannot be used by more than one view at the same time */
    private fun Drawable.freshCopy(): Drawable =
        constantState?.newDrawable()?.mutate() ?: this

    private val unknownSenderIcon: Drawable by lazy {
        context.getDrawable(R.drawable.icon_category_unknown)?.mutate() ?: appIcon
    }

    private val errorDrawable: Drawable by lazy {
        ImageLoader.errorBitmap.toDrawable(resources)
    }

    private val appIcon: Drawable by lazy {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "appIcon: failed to get the icon of this application, error: $e")
            errorDrawable
        }
    }

    private fun Long.format(): String {
        val noteDate = Date(this)
        val now = Date()
        val calNote = java.util.Calendar.getInstance().apply { time = noteDate }
        val calNow = java.util.Calendar.getInstance().apply { time = now }

        val sameYear = calNote.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR)
        val dayOfYearNote = calNote.get(java.util.Calendar.DAY_OF_YEAR)
        val dayOfYearNow = calNow.get(java.util.Calendar.DAY_OF_YEAR)
        val diffDays = dayOfYearNow - dayOfYearNote

        return when {
            sameYear && diffDays == 0 -> dataFormatToday.format(noteDate) // Today
            sameYear && diffDays == 1 -> "Yesterday " + dataFormatToday.format(noteDate)
            sameYear && diffDays in 2..6 -> dataFormatWeekly.format(noteDate)
            else -> dataFormatCommon.format(noteDate)
        }
    }

    companion object {
        const val TAG: String = "NoteItemView"


        private const val TEXT_SIZE_REACTION = 13f    // in sp

        private const val MEDIA_SIZE_CACHE_LIMIT = 512

        private val senderIconCache = HashMap<String, Drawable>()
        private val senderLabelCache = HashMap<String, String>()

        private val mediaSizeCache = HashMap<String, Size>()
        val dataFormatCommon: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
        val dataFormatToday: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.CHINESE)
        val dataFormatWeekly: SimpleDateFormat = SimpleDateFormat("EEE HH:mm", Locale.CHINESE)

    }


    interface EventListener {
        fun onSenderClick(note: NoteItem)
        fun onBackgroundClick(note: NoteItem, anchorY: Int)
        fun onMediaClick(note: NoteItem, file: AttachmentFile)
        fun onMediaLongClick(note: NoteItem, file: AttachmentFile, anchorY: Int)
        fun onFileClick(note: NoteItem, file: AttachmentFile)
        fun onFileLongClick(note: NoteItem, file: AttachmentFile, anchorY: Int)
        fun onReactionClick(note: NoteItem, reaction: String)
        fun onReactionLongClick(note: NoteItem, reaction: String)

        fun onItemLongPress(note: NoteItem, anchorY: Int)
        fun onLongPressDrag(currentX: Int, currentY: Int)
        fun onLongPressRelease()

        fun onDragStart(downX: Int, downY: Int): Boolean
        fun onDragging(currentX: Int, currentY: Int): Boolean

        fun onDragEnd(currentX: Int, currentY: Int, speedX: Float, speedY: Float)
    }

}
