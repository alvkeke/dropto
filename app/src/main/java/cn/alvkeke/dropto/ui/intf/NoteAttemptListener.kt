package cn.alvkeke.dropto.ui.intf

import cn.alvkeke.dropto.data.NoteItem

interface NoteAttemptListener {
    enum class Attempt {
        CREATE,
        REMOVE,
        UPDATE,
        COPY,
        SHOW_DETAIL,
        SHOW_SHARE,
        SHOW_IMAGE,
        SHOW_FORWARD,
    }

    fun onAttempt(attempt: Attempt, e: NoteItem)
    fun onAttempt(attempt: Attempt, e: NoteItem, ext: Any?)
    fun onAttemptBatch(attempt: Attempt, noteItems: ArrayList<NoteItem>)
}
