package cn.alvkeke.dropto.service

import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem

interface CoreServiceListener {

    fun onCategoryCreated(result: Int, category: Category) {}
    fun onCategoryUpdated(result: Int, category: Category) {}
    fun onCategoryRemoved(result: Int, category: Category) {}

    fun onNoteCreated(result: Int, noteItem: NoteItem) {}
    fun onNoteUpdated(result: Int, noteItem: NoteItem) {}
    fun onNoteRemoved(result: Int, noteItem: NoteItem) {}
    fun onNoteRestored(result: Int, noteItem: NoteItem) {}

}