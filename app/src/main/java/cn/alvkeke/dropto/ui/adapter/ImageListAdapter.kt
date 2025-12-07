package cn.alvkeke.dropto.ui.adapter

import android.R
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ImageListAdapter(context: Context) : RecyclerView.Adapter<ImageListAdapter.ViewHolder>() {
    interface OnItemClickListener {
        fun onClick(index: Int)
        fun onLongClick(index: Int): Boolean
    }

    private val images = ArrayList<String>()
    private var listener: OnItemClickListener? = null

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ViewHolder {
        val v = inflater.inflate(R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.setText(images[i])
        viewHolder.setOnClickListener(i)
    }

    override fun getItemCount(): Int {
        return images.size
    }

    fun add(file: String) {
        images.add(file)
        val index = images.indexOf(file)
        if (index >= 0) this.notifyItemInserted(index)
    }

    fun remove(index: Int) {
        images.removeAt(index)
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, images.size - index)
    }

    fun get(index: Int): String? {
        if (index >= images.size) return null
        return images[index]
    }

    fun emptyList() {
        this.notifyItemRangeRemoved(0, images.size)
        images.clear()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text1)

        fun setText(text: String) {
            textView.text = text
        }

        fun setOnClickListener(pos: Int) {
            if (listener != null) {
                textView.setOnClickListener { _: View ->
                    listener!!.onClick(pos)
                }
                textView.setOnLongClickListener { _: View ->
                    listener!!.onLongClick(pos)
                }
            }
        }
    }

    fun setItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }
}
