package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.Category.Companion.typeToIconResource

class CategoryListAdapter : FilterableListAdapter<Category, CategoryListAdapter.ViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null
    fun setItemTouchHelper(helper: ItemTouchHelper) {
        this.itemTouchHelper = helper
    }
    var showDragHandler = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    val categories: List<Category>
        get() = elements

    val filteredCategory: List<Category>
        get() = filteredElements

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.item_category_icon)
        val tvTitle: TextView = itemView.findViewById(R.id.item_category_title)
        val tvPreview: TextView = itemView.findViewById(R.id.item_category_preview_text)
        val ivDragHandle: ImageView = itemView.findViewById(R.id.item_category_drag_handler)

        fun setTitle(title: String) {
            tvTitle.text = title
        }

        fun setPreview(preview: String) {
            tvPreview.text = preview
        }

        fun setType(type: Category.Type) {
            ivIcon.setImageResource(typeToIconResource(type))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rlist_item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        val c = this.get(i)

        viewHolder.setTitle(c.title)
        viewHolder.setPreview(c.previewText)
        viewHolder.setType(c.type)
        if (showDragHandler) {
            viewHolder.ivDragHandle.visibility = View.VISIBLE
            viewHolder.ivDragHandle.setOnLongClickListener {
                itemTouchHelper?.startDrag(viewHolder)
                true
            }
        } else {
            viewHolder.ivDragHandle.visibility = View.GONE
        }
    }
}
