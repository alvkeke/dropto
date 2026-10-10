package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.component.NoteItemView
import java.util.IdentityHashMap

class NoteListAdapter : FilterableListAdapter<NoteItem, NoteListAdapter.ViewHolder>() {

    var eventListener: NoteItemView.EventListener? = null

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    @SuppressLint("NotifyDataSetChanged")
    fun syncList(notes: Collection<NoteItem>) {
        val oldList = filteredElements.toList()
        elements.clear()
        elements.addAll(notes)
        val newList = filteredElements

        val oldIndexes = IdentityHashMap<NoteItem, Int>(oldList.size)
        oldList.forEachIndexed { index, e -> oldIndexes.putIfAbsent(e, index) }
        val newIndexes = IdentityHashMap<NoteItem, Int>(newList.size)
        newList.forEachIndexed { index, e -> newIndexes.putIfAbsent(e, index) }

        // check if the sequence of the list changed or not, if yes, need all notify
        var lastIndex = -1
        for (e in oldList) {
            val index = newIndexes[e] ?: continue
            if (index < lastIndex) {
                notifyDataSetChanged()
                return
            }
            lastIndex = index
        }

        for (index in oldList.indices.reversed()) {
            if (!newIndexes.containsKey(oldList[index])) notifyItemRemoved(index)
        }
        newList.forEachIndexed { index, e ->
            if (!oldIndexes.containsKey(e)) notifyItemInserted(index)
        }
    }

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
