package cn.alvkeke.dropto.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AttachmentListAdapter(context: Context) : RecyclerView.Adapter<AttachmentListAdapter.ViewHolder>() {
    interface OnItemClickListener {
        fun onClick(index: Int)
        fun onLongClick(index: Int): Boolean
    }

    class AttachmentInfo(val file: String, val names: String?)
    private val attachments = ArrayList<AttachmentInfo>()
    private var listener: OnItemClickListener? = null

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ViewHolder {
        val v = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        var displayName = attachments[i].file
        if (attachments[i].names != null) {
            displayName += "\n${attachments[i].names}"
        }
        viewHolder.setText(displayName)
        viewHolder.setOnClickListener(i)
    }

    override fun getItemCount(): Int {
        return attachments.size
    }

    fun add(file: String, names: String? = null) {
        val info = AttachmentInfo(file, names)
        attachments.add(info)
        val index = attachments.indexOf(info)
        if (index >= 0) this.notifyItemInserted(index)
    }

    fun remove(index: Int) {
        attachments.removeAt(index)
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, attachments.size - index)
    }

    fun get(index: Int): AttachmentInfo {
        return attachments[index]
    }

    fun emptyList() {
        this.notifyItemRangeRemoved(0, attachments.size)
        attachments.clear()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

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
