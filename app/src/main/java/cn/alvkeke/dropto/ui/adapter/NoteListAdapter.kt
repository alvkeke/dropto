package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.component.NoteItemView
import java.util.IdentityHashMap
import kotlin.math.abs

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
        parent.clipChildren = false
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
        val note = this.get(position)
        view.groupType = groupTypeOf(position, note)
        view.note = note
    }

    private fun isGroupContinuation(position: Int, note: NoteItem): Boolean {
        if (note.forceNewGroup) return false    // end last group
        if (position + 1 >= itemCount) return false
        val above = get(position + 1)
        return note.sender == above.sender &&
                abs(note.createTime - above.createTime) <= GROUP_MERGE_INTERVAL
    }

    private fun groupTypeOf(position: Int, note: NoteItem): NoteItemView.Type {
        val mergedAbove = isGroupContinuation(position, note)
        val mergedBelow = position > 0 && isGroupContinuation(position - 1, get(position - 1))
        return when {
            mergedAbove && mergedBelow -> NoteItemView.Type.GROUP_BODY
            mergedAbove -> NoteItemView.Type.GROUP_END
            mergedBelow -> NoteItemView.Type.GROUP_HEAD
            else -> NoteItemView.Type.NON_GROUP
        }
    }

    init {
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                rebindGroup(positionStart - 1)
                rebindGroup(positionStart + itemCount)
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                rebindGroup(positionStart - 1)
                rebindGroup(positionStart)
            }
        })
    }

    private fun rebindGroup(position: Int) {
        if (position in 0 until itemCount) notifyItemChanged(position)
    }

    companion object {
        private const val GROUP_MERGE_INTERVAL = 5 * 60 * 1000L
    }

}
