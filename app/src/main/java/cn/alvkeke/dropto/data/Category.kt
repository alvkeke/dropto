package cn.alvkeke.dropto.data

import cn.alvkeke.dropto.R

class Category(@JvmField var title: String, @JvmField var type: Type) {
    enum class Type {
        LOCAL_CATEGORY,
        REMOTE_SELF_DEV,
        REMOTE_USERS,
    }

    @JvmField
    var id: Long = ID_NOT_ASSIGNED

    @JvmField
    var previewText: String = ""
    @JvmField
    val noteItems: ArrayList<NoteItem> = ArrayList()

    @JvmField
    var isInitialized: Boolean = false

    /**
     * add a new item into category, and return its new index
     * @param item new item object
     * @return index of the new item
     */
    fun addNoteItem(item: NoteItem): Int {
        noteItems.add(0, item)
        previewText = item.text
        return 0 // return the index of the new item
    }

    fun indexNoteItem(e: NoteItem): Int {
        return noteItems.indexOf(e)
    }

    fun delNoteItem(item: NoteItem) {
        noteItems.remove(item)
        if (!noteItems.isEmpty()) {
            val e = noteItems[0]
            previewText = e.text
        }
    }

    fun findNoteItem(id: Long): NoteItem? {
        for (e in noteItems) {
            if (e.id == id) return e
        }
        return null
    }

    fun getNoteItem(index: Int): NoteItem? {
        if (index >= noteItems.size) return null
        return noteItems[index]
    }

    companion object {
        const val ID_NOT_ASSIGNED: Long = -1

        @JvmStatic
        fun typeToIconResource(type: Type): Int {
            return when (type) {
                Type.LOCAL_CATEGORY -> R.drawable.icon_category_local
                Type.REMOTE_USERS -> R.drawable.icon_category_remote_peers
                Type.REMOTE_SELF_DEV -> R.drawable.icon_category_remote_dev
            }
        }

        @JvmStatic
        fun typeToName(type: Type): String {
            return when (type) {
                Type.LOCAL_CATEGORY -> "Local Category"
                Type.REMOTE_USERS -> "Remote Peer"
                Type.REMOTE_SELF_DEV -> "Remote Device"
            }
        }
    }
}
