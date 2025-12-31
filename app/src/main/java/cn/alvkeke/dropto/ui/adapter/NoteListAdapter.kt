package cn.alvkeke.dropto.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.AttachmentFile
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = this.get(position)
        val view = holder.content
        view.index = position
        view.text = note.text
        view.createTime = note.createTime
        view.images.clear()
        view.files.clear()
        // set the attachment Files only, to avoid async issue
        note.attachments.iterator().forEach { e ->
            when(e.type) {
                AttachmentFile.Type.IMAGE -> view.images.add(e.md5file)
                AttachmentFile.Type.FILE -> view.files.add(e)
            }
        }
        view.selected = isSelected(note)
        view.invalidate()
    }

}
