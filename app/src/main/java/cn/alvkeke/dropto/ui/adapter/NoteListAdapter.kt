package cn.alvkeke.dropto.ui.adapter

import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.NoteItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG = "NoteListAdapter"

class NoteListAdapter : SelectableListAdapter<NoteItem, NoteListAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.rlist_item_note_text)
        private val tvCreateTime: TextView = itemView.findViewById(R.id.rlist_item_note_create_time)
        private val ivEdited: ImageView = itemView.findViewById(R.id.rlist_item_note_is_edited)
        private val containerImage: ConstraintLayout = itemView.findViewById(R.id.rlist_item_note_img_container)
        private val ivImages = arrayOf(
            itemView.findViewById(R.id.rlist_item_note_img_view0),
            itemView.findViewById(R.id.rlist_item_note_img_view1),
            itemView.findViewById(R.id.rlist_item_note_img_view2),
            itemView.findViewById<ImageView>(R.id.rlist_item_note_img_view3)
        )
        private val guideView = itemView.findViewById<View>(R.id.rlist_item_note_img_guide_view)

        private fun showView(v: View) {
            v.visibility = View.VISIBLE
        }

        private fun hideView(v: View) {
            v.visibility = View.GONE
        }


        private fun timeFormat(timestamp: Long): String {
            return sdf.format(Date(timestamp))
        }

        fun setText(text: String) {
            if (text.isEmpty()) {
                tvText.visibility = View.GONE
            } else {
                tvText.visibility = View.VISIBLE
                tvText.text = text
            }
        }

        fun setCreateTime(time: Long) {
            if (time < 0) {
                hideView(tvCreateTime)
                return
            }
            showView(tvCreateTime)
            tvCreateTime.text = timeFormat(time)
        }

        fun setIsEdited(foo: Boolean) {
            tvCreateTime.measure(0, 0)
            val he = tvCreateTime.measuredHeight
            ivEdited.layoutParams.height = he
            ivEdited.layoutParams.width = he
            ivEdited.requestLayout()
            ivEdited.visibility = if (foo) View.VISIBLE else View.INVISIBLE
        }

        private fun assignImage1() {
            val params = ConstraintLayout.LayoutParams(0, 0)
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            ivImages[0].layoutParams = params
            ivImages[0].visibility = View.VISIBLE
            for (i in 1..<ivImages.size) {
                ivImages[i].visibility = View.GONE
            }
        }

        private fun assignImage2() {
            for (i in 0..1) {
                val params = ConstraintLayout.LayoutParams(0, 0)
                params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                if (i != 0) {
                    params.startToEnd = guideView.id
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                } else {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    params.endToStart = guideView.id
                }
                ivImages[i].layoutParams = params
                ivImages[i].visibility = View.VISIBLE
            }
            for (i in 2..<ivImages.size) {
                ivImages[i].visibility = View.GONE
            }
        }

        private fun assignImage3() {
            for (i in 0..2) {
                val params = ConstraintLayout.LayoutParams(0, 0)
                if (i == 0) {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    params.endToStart = guideView.id
                } else {
                    params.startToEnd = guideView.id
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    if (i == 1) {
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        params.bottomToTop = guideView.id
                    } else {
                        params.topToBottom = guideView.id
                        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
                ivImages[i].layoutParams = params
                ivImages[i].visibility = View.VISIBLE
            }
            ivImages[3].visibility = View.GONE
        }

        private fun assignImage4() {
            for (i in 0..3) {
                val params = ConstraintLayout.LayoutParams(0, 0)
                if (i < 2) {
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    params.bottomToTop = guideView.id
                } else {
                    params.topToBottom = guideView.id
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                if (i % 2 == 0) {
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    params.endToStart = guideView.id
                } else {
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    params.startToEnd = guideView.id
                }
                ivImages[i].layoutParams = params
                ivImages[i].visibility = View.VISIBLE
            }
        }

        private fun assignImageLayout(count: Int) {
            when (count) {
                1 -> assignImage1()
                2 -> assignImage2()
                3 -> assignImage3()
                else -> assignImage4()
            }
        }

        private fun loadNoteImageAt(note: NoteItem, index: Int) {
            val img = note.attachments[index]
//            loadImageAsync(img.md5file) { bitmap: Bitmap? ->
//                if (bitmap == null) {
//                    val errMsg = "Failed to get image file, skip this item"
//                    Log.e(this.toString(), errMsg)
//                    ivImages[index].setImageResource(R.drawable.img_load_error)
//                } else {
//                    ivImages[index].setImageBitmap(bitmap)
//                    ivImages[index].measure(
//                        View.MeasureSpec.UNSPECIFIED,
//                        View.MeasureSpec.UNSPECIFIED
//                    )
//                }
//            }

            // TODO: fix the UI display
            Log.e("Debug:$TAG", "loadAttachment: for note ${note.id}, type: ${note.attachmentType}, index $index, md5 ${img.md5}, filename: ${img.name}" )
        }

        private fun loadAttachments(note: NoteItem) {
            val count = note.attachmentCount
            assignImageLayout(count)
            when (count) {
                4 -> {
                    loadNoteImageAt(note, 3)
                    loadNoteImageAt(note, 2)
                    loadNoteImageAt(note, 1)
                    loadNoteImageAt(note, 0)
                }

                3 -> {
                    loadNoteImageAt(note, 2)
                    loadNoteImageAt(note, 1)
                    loadNoteImageAt(note, 0)
                }

                2 -> {
                    loadNoteImageAt(note, 1)
                    loadNoteImageAt(note, 0)
                }

                1 -> loadNoteImageAt(note, 0)
                else -> {
                    ivImages[3].setImageResource(R.drawable.icon_common_more)
                    loadNoteImageAt(note, 2)
                    loadNoteImageAt(note, 1)
                    loadNoteImageAt(note, 0)
                }
            }
        }

        fun setAttachmentViews(note: NoteItem) {
            if (note.noAttachment) {
                containerImage.visibility = View.GONE
                return
            }
            loadAttachments(note)
            containerImage.visibility = View.VISIBLE
        }

        companion object {
            var sdf: SimpleDateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINESE)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rlist_item_note, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = this.get(position)
        holder.setText(note.text)
        holder.setCreateTime(note.createTime)
        holder.setIsEdited(note.isEdited)
        holder.setAttachmentViews(note)
        if (isSelected(note)) {
            // TODO: use another color for selected item
            holder.itemView.setBackgroundColor(Color.LTGRAY)
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    companion object {
        private val image = arrayOfNulls<View>(4)
        private val rect = Rect()
        @JvmStatic
        fun checkImageClicked(v: View, x: Int, y: Int): Int {
            val container = v.findViewById<View>(R.id.rlist_item_note_img_container)
            if (container.visibility != View.VISIBLE) {
                return -1
            }
            image[0] = v.findViewById(R.id.rlist_item_note_img_view0)
            image[1] = v.findViewById(R.id.rlist_item_note_img_view1)
            image[2] = v.findViewById(R.id.rlist_item_note_img_view2)
            image[3] = v.findViewById(R.id.rlist_item_note_img_view3)
            for (i in image.indices) {
                if (image[i] == null)
                    continue
                if (image[i]!!.visibility != View.VISIBLE)
                    continue

                image[i]!!.getGlobalVisibleRect(rect)
                if (rect.contains(x, y)) {
                    return i
                }
            }
            return -1
        }
    }
}
