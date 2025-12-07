package cn.alvkeke.dropto.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.Category.Companion.typeToIconResource

class CategoryListAdapter : SelectableListAdapter<Category, CategoryListAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.item_category_icon)
        private val tvTitle: TextView = itemView.findViewById(R.id.item_category_title)
        private val tvPreview: TextView = itemView.findViewById(R.id.item_category_preview_text)

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
        if (isSelected(i)) {
            // TODO: use another color for selected item
            viewHolder.itemView.setBackgroundColor(Color.LTGRAY)
        } else {
            viewHolder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
