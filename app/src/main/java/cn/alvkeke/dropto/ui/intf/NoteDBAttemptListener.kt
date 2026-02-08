package cn.alvkeke.dropto.ui.intf

import cn.alvkeke.dropto.data.NoteItem

interface NoteDBAttemptListener {
    enum class Attempt {
        CREATE,
        REMOVE,
        UPDATE,
        RESTORE,
    }

    fun onAttempt(attempt: Attempt, e: NoteItem)
    fun onAttemptBatch(attempt: Attempt, noteItems: ArrayList<NoteItem>)
}
