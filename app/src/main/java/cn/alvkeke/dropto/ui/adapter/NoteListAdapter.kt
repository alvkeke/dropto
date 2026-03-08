package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.comonent.NoteItemView

class NoteListAdapter : FilterableListAdapter<NoteItem, NoteListAdapter.ViewHolder>() {
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
        val note = this.get(position)
        val view = holder.itemView as NoteItemView
        view.index = position
        view.text = note.text
        view.createTime = note.createTime
        view.isEdited = note.isEdited
        view.isDeleted = note.isDeleted
        view.isSynced = note.isSynced
        view.sender = note.sender
        view.reactionList.clear()
        view.reactionList.addAll(note.reactions)
        view.medias.clear()
        view.medias.addAll(note.medias)
        view.files.clear()
        view.files.addAll(note.files)
    }

}
