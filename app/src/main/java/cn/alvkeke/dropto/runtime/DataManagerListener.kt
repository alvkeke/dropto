package cn.alvkeke.dropto.runtime

import cn.alvkeke.dropto.runtime.model.Category
import cn.alvkeke.dropto.runtime.model.NoteItem

interface DataManagerListener {

    fun onCategoryCreated(result: Int, category: Category) {}
    fun onCategoryUpdated(result: Int, category: Category) {}
    fun onCategoryRemoved(result: Int, category: Category) {}

    fun onNoteCreated(result: Int, noteItem: NoteItem) {}
    fun onNoteUpdated(result: Int, noteItem: NoteItem) {}
    fun onNoteRemoved(result: Int, noteItem: NoteItem) {}
    fun onNoteRestored(result: Int, noteItem: NoteItem) {}

}
