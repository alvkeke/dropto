package cn.alvkeke.dropto.ui.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.ui.comonent.NoteItemView

class NoteListAdapter : SelectableListAdapter<NoteItem, NoteListAdapter.ViewHolder>() {
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content: NoteItemView = itemView.findViewById(R.id.rlist_item_note_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.rlist_item_note, parent, false)
        return ViewHolder(view)
    }

    var showDeleted = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int {
        return if (showDeleted) {
            elements.size
        } else {
            elements.filter { !it.isDeleted }.size
        }
    }

    override fun get(index: Int): NoteItem {
        return if (showDeleted) {
            elements[index]
        } else {
            elements.filter { !it.isDeleted }[index]
        }
    }

    override fun remove(e: NoteItem) {
        if (showDeleted) {
            super.remove(e)
        } else {
            val index = elements.filter { !it.isDeleted || it == e }.indexOf(e)
            notifyItemRemoved(index)
        }
    }

    override fun update(e: NoteItem) {
        if (showDeleted) {
            super.update(e)
        } else {
            val index = elements.filter { !it.isDeleted || it == e }.indexOf(e)
            notifyItemChanged(index)
        }
    }

    override fun add(index: Int, e: NoteItem): Boolean {
        if (showDeleted) {
            return super.add(index, e)
        } else {
            val filtered = elements.filter { !it.isDeleted }
            var actualIndex = 0
            var count = 0
            while (actualIndex < elements.size && count < index) {
                if (!elements[actualIndex].isDeleted) {
                    count++
                }
                actualIndex++
            }
            elements.add(actualIndex, e)
            notifyItemInserted(index)
            notifyItemRangeChanged(index, filtered.size - index + 1)
            return true
        }
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = this.get(position)
        val view = holder.content
        view.index = position
        view.text = note.text
        view.createTime = note.createTime
        view.isEdited = note.isEdited
        view.isDeleted = note.isDeleted
        view.isSynced = note.isSynced
        view.medias.clear()
        view.medias.addAll(note.medias)
        view.files.clear()
        view.files.addAll(note.files)
    }

}
