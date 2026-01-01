package cn.alvkeke.dropto.ui.intf

import cn.alvkeke.dropto.data.NoteItem

interface NoteUIAttemptListener {
    enum class Attempt {
        COPY,
        SHOW_DETAIL,
        SHOW_SHARE,
        SHOW_IMAGE,
        OPEN_FILE,
        SHOW_FORWARD,
    }

    fun onAttempt(attempt: Attempt, e: NoteItem)
    /**
     * index: the index of the image in the NoteItem's image list,
     *        currently is used for UI_SHOW_IMAGE only
     */
    fun onAttempt(attempt: Attempt, e: NoteItem, index: Int)
    fun onAttemptBatch(attempt: Attempt, noteItems: ArrayList<NoteItem>)
}