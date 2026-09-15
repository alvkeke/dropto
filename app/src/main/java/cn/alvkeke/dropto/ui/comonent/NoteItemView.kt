package cn.alvkeke.dropto.ui.comonent

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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

    var index: Int = -1
    var text: String = ""
        set(value) {
            field = value
            textView.text = value
            textView.isVisible = value.isNotEmpty()
        }

    var createTime: Long = 0L
        set(value) {
            field = value
            timeView.text = value.format()
        }

    var isEdited: Boolean = false
        set(value) {
            field = value
            editedView.isVisible = value
            updateStatusContainer()
        }

    var isDeleted: Boolean = false
        set(value) {
            field = value
            deletedView.isVisible = value
            updateStatusContainer()
        }

    var isSynced: Boolean = false
        set(value) {
            field = value
            unsyncedView.isVisible = !value
            updateStatusContainer()
        }

    var sender: String? = null
        set(value) {
            field = value
            bindSender(value)
        }

    val medias: MutableList<AttachmentFile> =
        NotifiableList(ArrayList()) { rebuildMedias() }

    val files: MutableList<AttachmentFile> =
        NotifiableList(ArrayList()) { rebuildFiles() }

    val reactionList: MutableList<String> =
        NotifiableList(ArrayList()) { rebuildReactions() }

    /** the avatar is only clickable when the note comes from another application */
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

    private fun updateStatusContainer() {
        statusContainer.isVisible = isEdited || isDeleted || !isSynced
    }

    /** the attachments sit in their own scroll container, so their clicks are reported here */
    var onMediaClick: ((mediaIndex: Int) -> Unit)? = null
    var onMediaLongClick: ((mediaIndex: Int, rawY: Float) -> Unit)? = null

    /** called while the attachments are dragged to the right and are already at their start */
    var onMediaDragToClose: ((deltaX: Float) -> Unit)? = null
    /** called when such a dragging ends, with the speed of the release in pixels per ms */
    var onMediaDragToCloseEnd: ((deltaX: Float, speed: Float) -> Unit)? = null

    private fun rebuildMedias() {
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
                tag = ClickedContent(ClickedContent.Type.MEDIA, file, position)
                isVideo = file.isVideo

                setOnClickListener { view: View ->
                    (view.tag as? ClickedContent)?.let { onMediaClick?.invoke(it.index) }
                }
                setOnLongClickListener { view: View ->
                    (view.tag as? ClickedContent)?.let { content ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onMediaLongClick?.invoke(content.index, mediaCellScreenY(view))
                    }
                    true
                }
                @SuppressLint("ClickableViewAccessibility")
                setOnTouchListener { view: View, event: MotionEvent ->
                    handleMediaDrag(view, event)
                }

                mediaSizeCache[file.md5file.absolutePath]?.let {
                    setMediaCellWidth(this, mediaCellWidth(it))
                }
                loadMediaThumbnail(file, this)
            }
            mediaContainer.addView(cell)
        }
        mediaScroll.isVisible = medias.isNotEmpty()
    }

    private fun mediaCellScreenY(cell: View): Float {
        val location = IntArray(2)
        cell.getLocationOnScreen(location)
        return (location[1] + cell.height / 2).toFloat()
    }

    private val mediaDragSlop: Int by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private var mediaDragStartX = 0f
    private var mediaDragStartY = 0f
    private var mediaDragDecided = false
    private var mediaDragActive = false
    private var mediaDragTracker: VelocityTracker? = null

    /**
     * The attachments are scrolled by [mediaScroll], the page which contains the item can
     * only be dragged with them when they are already scrolled to their start.
     * @return true if the dragging was given to the page, false to let the attachments handle it
     */
    private fun handleMediaDrag(cell: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mediaDragStartX = event.rawX
                mediaDragStartY = event.rawY
                mediaDragDecided = false
                mediaDragActive = false
                mediaDragTracker?.recycle()
                mediaDragTracker = VelocityTracker.obtain().apply { addMovement(event) }
                // hold the gesture until it is clear whether the page needs it
                mediaScroll.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                mediaDragTracker?.addMovement(event)
                val deltaX = event.rawX - mediaDragStartX
                val deltaY = event.rawY - mediaDragStartY

                if (!mediaDragDecided) {
                    if (abs(deltaX) <= mediaDragSlop && abs(deltaY) <= mediaDragSlop) {
                        return false
                    }
                    mediaDragDecided = true
                    // a dragging is not a long press, the view must not fire it after the timeout
                    cell.cancelLongPress()
                    cell.isPressed = false

                    val toPage = deltaX > abs(deltaY) && mediaScroll.scrollX <= 0
                    if (!toPage) {
                        mediaScroll.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    mediaDragActive = true
                }
                if (!mediaDragActive) {
                    return false
                }

                onMediaDragToClose?.invoke(deltaX)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasActive = mediaDragActive
                mediaDragActive = false
                mediaDragDecided = false
                if (wasActive) {
                    onMediaDragToCloseEnd?.invoke(
                        event.rawX - mediaDragStartX,
                        releaseSpeed()
                    )
                }
                mediaDragTracker?.recycle()
                mediaDragTracker = null
                return wasActive
            }
        }
        return false
    }

    private fun releaseSpeed(): Float {
        val tracker = mediaDragTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.xVelocity / 1000f
    }


    private fun rebuildFiles() {
        fileContainer.removeAllViews()

        files.forEachIndexed { position, file ->
            val row = FileItemView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                fileName = file.name
                tag = ClickedContent(ClickedContent.Type.FILE, file, medias.size + position)
            }
            fileContainer.addView(row)
        }
        fileContainer.isVisible = files.isNotEmpty()
    }

    private fun rebuildReactions() {
        reactionContainer.removeAllViews()

        reactionList.forEachIndexed { position, reaction ->
            reactionContainer.addView(createReactionChip(reaction, position))
        }
        reactionContainer.isVisible = reactionList.isNotEmpty()
    }

    private fun createReactionChip(reaction: String, position: Int): Chip {
        return Chip(context).apply {
            // must not consume the touch, it is handled by the note item itself
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

    class ClickedContent(val type: Type, val data: AttachmentFile? = null, val index: Int = -1) {
        enum class Type {
            BACKGROUND,
            SENDER_ICON,
            MEDIA,
            FILE,
            REACTION,
        }
    }

    /** Check what is displayed at the given point, in the coordinate of this view. */
    fun checkClickedContent(x: Float, y: Float): ClickedContent {
        Log.v(TAG, "index-$index, checkClickedContent: x=$x, y=$y")

        var view: View? = findHitView(this, x, y)
        while (view != null && view !== this) {
            val tag = view.tag
            if (tag is ClickedContent) {
                Log.v(TAG, "checkClickedContent: got $tag")
                return tag
            }
            view = view.parent as? View
        }

        Log.v(TAG, "checkClickedContent: nothing hit, background")
        return ClickedContent(ClickedContent.Type.BACKGROUND)
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
    private class NotifiableList<T>(
        private val backing: MutableList<T>,
        private val onChanged: () -> Unit,
    ) : MutableList<T> {

        override val size: Int get() = backing.size

        override fun contains(element: T): Boolean = backing.contains(element)

        override fun containsAll(elements: Collection<T>): Boolean = backing.containsAll(elements)

        override fun get(index: Int): T = backing[index]

        override fun indexOf(element: T): Int = backing.indexOf(element)

        override fun isEmpty(): Boolean = backing.isEmpty()

        override fun iterator(): MutableIterator<T> = backing.iterator()

        override fun lastIndexOf(element: T): Int = backing.lastIndexOf(element)

        override fun listIterator(): MutableListIterator<T> = backing.listIterator()

        override fun listIterator(index: Int): MutableListIterator<T> = backing.listIterator(index)

        override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
            backing.subList(fromIndex, toIndex)

        override fun add(element: T): Boolean =
            backing.add(element).also { if (it) onChanged() }

        override fun add(index: Int, element: T) {
            backing.add(index, element)
            onChanged()
        }

        override fun addAll(elements: Collection<T>): Boolean =
            backing.addAll(elements).also { if (it) onChanged() }

        override fun addAll(index: Int, elements: Collection<T>): Boolean =
            backing.addAll(index, elements).also { if (it) onChanged() }

        override fun clear() {
            if (backing.isEmpty()) return
            backing.clear()
            onChanged()
        }

        override fun remove(element: T): Boolean =
            backing.remove(element).also { if (it) onChanged() }

        override fun removeAll(elements: Collection<T>): Boolean =
            backing.removeAll(elements.toSet()).also { if (it) onChanged() }

        override fun removeAt(index: Int): T =
            backing.removeAt(index).also { onChanged() }

        override fun retainAll(elements: Collection<T>): Boolean =
            backing.retainAll(elements.toSet()).also { if (it) onChanged() }

        override fun set(index: Int, element: T): T =
            backing.set(index, element).also { onChanged() }
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

}
