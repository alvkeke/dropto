package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.comonent.NoteItemView

class NoteListAdapter : FilterableListAdapter<NoteItem, NoteListAdapter.ViewHolder>() {

    var eventListener: NoteItemView.EventListener? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = NoteItemView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(view)
    }

    private val nonDeletedFilter = { e: NoteItem -> !e.isDeleted }
    var showDeleted = true
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            if (value) {
                this.removeFilter(nonDeletedFilter)
            } else {
                this.addFilter(nonDeletedFilter)
            }

            notifyDataSetChanged()
        }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView as NoteItemView
        view.eventListener = eventListener
        view.note = this.get(position)
    }

}
