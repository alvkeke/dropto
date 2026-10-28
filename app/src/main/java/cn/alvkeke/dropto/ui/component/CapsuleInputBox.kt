package cn.alvkeke.dropto.ui.component

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.LinearLayout
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.runtime.model.AttachmentFile
import cn.alvkeke.dropto.provider.local.FileHelper
import cn.alvkeke.dropto.ui.loader.ImageLoader
import cn.alvkeke.dropto.ui.listener.OnRecyclerViewTouchListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "CapsuleInputBox"
private const val ATTACHMENT_SIZE_DP = 84

class CapsuleInputBox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CapsuleLayout(context, attrs, defStyleAttr) {

    sealed class AttachmentItem(var deleteMarked: Boolean = false) {
        data class Attachment(val file: AttachmentFile) : AttachmentItem()
        data class Unhandled(val uri: Uri, val type: AttachmentFile.Type) : AttachmentItem()
    }

    private val attachmentAdapter = AttachmentListAdapter()
    private var extraRow: LinearLayout
    private var modeIcon: ImageView
    private var cancelButton: ImageButton
    private var attachmentList: RecyclerView
    private var attachButton: ImageButton
    private var textBox: EditText
    private var sendButton: ImageButton

    var onSendClick: OnClickListener? = null
    var onSendLongClick: OnLongClickListener? = null
    var onAttachClick: OnClickListener? = null
    var onAttachLongClick: OnLongClickListener? = null
    var onCancelClick: OnClickListener? = null

    var editing = false
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    val text: String
        get() = textBox.text?.toString().orEmpty()

    val attachments: List<AttachmentItem>
        get() = attachmentAdapter.nonDeleteList

    init {
        isClickable = true
        isFocusable = true
        LayoutInflater.from(context).inflate(R.layout.component_input_box, this, true)

        extraRow = findViewById(R.id.input_box_extra)
        modeIcon = findViewById(R.id.input_box_mode_icon)
        cancelButton = findViewById(R.id.input_box_cancel)
        attachmentList = findViewById(R.id.input_box_attachments)
        attachButton = findViewById(R.id.input_box_attach)
        textBox = findViewById(R.id.input_box_text)
        sendButton = findViewById(R.id.input_box_send)

        val gap = dpToPx(4)
        attachmentAdapter.itemSize = dpToPx(ATTACHMENT_SIZE_DP)
        attachmentList.adapter = attachmentAdapter
        attachmentList.layoutManager = LinearLayoutManager(
            context,
            RecyclerView.HORIZONTAL,
            false
        )
        attachmentList.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State,
            ) {
                val isHead = parent.getChildAdapterPosition(view) == 0
                outRect.set(if (isHead) gap else 0, gap, gap, gap)
            }
        })
        @SuppressLint("ClickableViewAccessibility")
        attachmentList.setOnTouchListener(AttachmentTouchListener())

        sendButton.setOnClickListener { onSendClick?.onClick(it) }
        sendButton.setOnLongClickListener { onSendLongClick?.onLongClick(it) ?: false }
        attachButton.setOnClickListener { onAttachClick?.onClick(it) }
        attachButton.setOnLongClickListener { onAttachLongClick?.onLongClick(it) ?: false }
        cancelButton.setOnClickListener { onCancelClick?.onClick(it) }
        cancelButton.setOnLongClickListener {
            if (editing) attachmentAdapter.markAllRemoved(!attachmentAdapter.isAllMarkDeleted)
            true
        }
        refresh()
    }

    fun setText(value: CharSequence) {
        textBox.setText(value)
        textBox.setSelection(textBox.text?.length ?: 0)
    }

    fun clearText() {
        textBox.text?.clear()
    }

    fun addAttachment(file: AttachmentFile) {
        attachmentAdapter.add(file)
        refresh()
    }

    fun addAttachment(uri: Uri, type: AttachmentFile.Type) {
        attachmentAdapter.add(uri, type)
        refresh()
    }

    fun clearAttachments() {
        attachmentAdapter.clear()
        refresh()
    }

    private fun refresh() {
        modeIcon.isVisible = editing
        extraRow.isVisible = editing || attachmentAdapter.itemCount > 0
        sendButton.setImageResource(
            if (editing) R.drawable.icon_common_check else R.drawable.img_btn_note_send
        )
    }

    private fun removeAttachment(index: Int) {
        attachmentAdapter.removeAt(index)
        refresh()
    }

    private fun toggleRemoved(index: Int) {
        attachmentAdapter.markRemoved(index, !attachmentAdapter.allList[index].deleteMarked)
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private inner class AttachmentTouchListener : OnRecyclerViewTouchListener(context) {

        override fun onItemLongClick(v: View, index: Int, rawX: Float, rawY: Float): Boolean {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            if (editing) toggleRemoved(index) else removeAttachment(index)
            return true
        }

        override fun onItemClick(v: View, index: Int): Boolean {
            if (editing) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                toggleRemoved(index)
            }
            return true
        }
    }

    private class AttachmentListAdapter : RecyclerView.Adapter<AttachmentListAdapter.ViewHolder>() {
        private val list = mutableListOf<AttachmentItem>()
        val allList : List<AttachmentItem> = list
        val nonDeleteList: List<AttachmentItem>
            get() = list.filter { !it.deleteMarked }
        val isAllMarkDeleted: Boolean
            get() = list.isNotEmpty() && nonDeleteList.isEmpty()
        var itemSize = 0

        class ViewHolder(
            itemView: ConstraintLayout,
            val mainView: View,
            val overlay: View,
        ) : RecyclerView.ViewHolder(itemView)

        private class OverlayView(context: Context) : View(context) {
            private val stripePaint = Paint().apply {
                color = Color.RED
                alpha = 100
                strokeWidth = 8f
                isAntiAlias = true
                style = Paint.Style.STROKE
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val w = width.toFloat()
                val h = height.toFloat()
                val stripeSpacing = 20f

                var i = 0f
                while (i < w + h) {
                    val startX = if (i < w) w - i else 0f
                    val startY = if (i < w) 0f else i - w
                    val endX = if (i < h) w else w - (i - h)
                    val endY = if (i < h) i else h

                    canvas.drawLine(startX, startY, endX, endY, stripePaint)

                    i += stripeSpacing
                }
            }
        }

        private var videoIconDrawable: Drawable? = null
        private inner class MediaView(
            context: Context,
        ) : AppCompatImageView(context) {

            var isVideo = false

            val rect = RectF()
            val paint = Paint().apply {
                color = ContextCompat.getColor(context, R.color.video_play_icon_background)
            }
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                if (!isVideo) return

                val width2 = width/2f
                val height2 = height/2f
                rect.set(
                    width2,
                    height2,
                    width.toFloat(),
                    height.toFloat()
                )

                canvas.drawRect(rect, paint)
                if (videoIconDrawable == null) {
                    videoIconDrawable = ContextCompat.getDrawable(
                        context, R.drawable.icon_common_video
                    )
                    videoIconDrawable?.setTint(
                        ContextCompat.getColor(
                            context, R.color.video_play_icon_foreground
                        )
                    )
                }
                videoIconDrawable?.setBounds(
                    width2.toInt(), height2.toInt(), width, height
                )
                videoIconDrawable?.draw(canvas)
            }
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder {
            val layout = ConstraintLayout(parent.context).apply {
                id = generateViewId()
                layoutParams = RecyclerView.LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                )
            }

            val height: Int
            val width: Int
            val view = when (viewType) {
                0 -> {
                    height = itemSize
                    width = itemSize
                    MediaView(parent.context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                }
                else -> {
                    height = itemSize
                    width = itemSize * 2
                    TextView(parent.context).apply {
                        setTextColor(ContextCompat.getColor(
                            parent.context,
                            R.color.color_text_main
                        ))
                        setBackgroundResource(R.color.note_bubble_background)
                        textAlignment = TEXT_ALIGNMENT_CENTER
                        gravity = Gravity.CENTER
                    }
                }
            }.apply {
                layoutParams = LayoutParams(
                    width,
                    height
                ).apply {
                    startToStart = layout.id
                    topToTop = layout.id
                }
            }

            val overlay = OverlayView(parent.context).apply {
                layoutParams = LayoutParams(
                    width,
                    height
                ).apply {
                    startToStart = layout.id
                    topToTop = layout.id
                }
            }
            layout.addView(view)
            layout.addView(overlay)

            return ViewHolder(layout, view, overlay)
        }

        private lateinit var scope : CoroutineScope
        override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
            super.onAttachedToRecyclerView(recyclerView)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
            scope.cancel()
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            when (item) {
                is AttachmentItem.Attachment -> {
                    when (item.file.type) {
                        AttachmentFile.Type.MEDIA -> {
                            val imageView = holder.mainView as MediaView
                            imageView.setImageBitmap(null)
                            val key = item.file.md5file.absolutePath
                            imageView.tag = key
                            if (item.file.isVideo) {
                                imageView.isVideo = true
                                ImageLoader.loadVideoThumbnailAsync(item.file.md5file) {
                                    it?.let { thumb ->
                                        if (imageView.tag == key) {
                                            imageView.setImageBitmap(thumb)
                                            imageView.invalidate()
                                        }
                                    }
                                }
                            } else {
                                imageView.isVideo = false
                                ImageLoader.loadImageAsync(
                                    item.file.md5file,
                                ) { bitmap ->
                                    bitmap?.let {
                                        if (imageView.tag == key) {
                                            imageView.setImageBitmap(it)
                                            imageView.invalidate()
                                        }
                                    }
                                }
                            }
                        }
                        AttachmentFile.Type.FILE -> {
                            val textView = holder.mainView as TextView
                            textView.text = item.file.name
                        }
                    }
                }
                is AttachmentItem.Unhandled -> {
                    when (item.type) {
                        AttachmentFile.Type.MEDIA -> {
                            val imageView = holder.mainView as MediaView
                            imageView.setImageBitmap(null)
                            val fileName = FileHelper.getFileNameFromUri(
                                imageView.context, item.uri
                            )
                            val mimeType = FileHelper.mimeTypeFromFileName(fileName)
                            val isVideo = mimeType.startsWith("video/")
                            val key = item.uri.toString()
                            imageView.tag = key
                            scope.launch {
                                try {
                                    val bitmap = if (isVideo) {
                                        val retriever = MediaMetadataRetriever()
                                        try {
                                            retriever.setDataSource(imageView.context, item.uri)
                                            val thumbnail =
                                                retriever.frameAtTime // default is first frame, can specify time
                                            thumbnail
                                        } finally {
                                            retriever.release()
                                        }
                                    } else {
                                        val resolver = imageView.context.contentResolver
                                        val source = ImageDecoder.createSource(resolver, item.uri)
                                        ImageDecoder.decodeBitmap(source)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (imageView.tag == key) {
                                            imageView.isVideo = isVideo
                                            imageView.setImageBitmap(bitmap)
                                            val max = 30
                                            val fMax = max.toFloat()
                                            for (a in 0..max) {
                                                imageView.alpha = a / fMax
                                                delay(10.milliseconds)
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        if (imageView.tag == key) {
                                            imageView.setImageBitmap(ImageLoader.errorBitmap)
                                        }
                                    }
                                    Log.e(TAG, "Failed to load media", e)
                                }
                            }
                        }
                        AttachmentFile.Type.FILE -> {
                            val textView = holder.mainView as TextView
                            textView.text = FileHelper.getFileNameFromUri(textView.context, item.uri)
                        }
                    }
                }
            }
            holder.overlay.isVisible = item.deleteMarked
        }

        override fun onViewRecycled(holder: ViewHolder) {
            super.onViewRecycled(holder)
            if (holder.mainView is MediaView) {
                val imageView = holder.mainView
                imageView.tag = null
                imageView.setImageBitmap(null)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (val e = list[position]) {
                is AttachmentItem.Attachment -> {
                    when (e.file.type) {
                        AttachmentFile.Type.MEDIA -> 0
                        AttachmentFile.Type.FILE -> 1
                    }
                }
                is AttachmentItem.Unhandled -> {
                    when (e.type) {
                        AttachmentFile.Type.MEDIA -> 0
                        AttachmentFile.Type.FILE -> 1
                    }
                }
            }
        }

        override fun getItemCount(): Int {
            return list.size
        }

        fun add(item: AttachmentItem) {
            if (!list.contains(item)) {
                list.add(item)
                val index = list.indexOf(item)
                if (index != -1) {
                    this.notifyItemInserted(index)
                }
//            } else {
//                val index = list.indexOf(item)
//                list[index].deleteMarked = false
//                this.notifyItemChanged(index)
            }
        }

        fun add(attachment: AttachmentFile) {
            val newItem = AttachmentItem.Attachment(attachment)
            add(newItem)
        }

        fun add(uri: Uri, type: AttachmentFile.Type) {
            val newItem = AttachmentItem.Unhandled(uri, type)
            add(newItem)
        }

        fun markRemoved(index: Int, set: Boolean) {
            list[index].deleteMarked = set
            notifyItemChanged(index)
        }

        fun markAllRemoved(set: Boolean) {
            list.indices.forEach { index ->
                val item = list[index]
                item.deleteMarked = set
                notifyItemChanged(index)
            }
        }

        fun removeAt(index: Int) {
            list.removeAt(index)
            notifyItemRemoved(index)
        }

        fun clear() {
            val size = list.size
            list.clear()
            notifyItemRangeRemoved(0, size)
        }
    }
}
