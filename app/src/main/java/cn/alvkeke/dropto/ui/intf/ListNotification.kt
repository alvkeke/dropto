package cn.alvkeke.dropto.ui.intf

interface ListNotification<T> {
    enum class Notify {
        INSERTED,
        REMOVED,
        RESTORED,
        UPDATED,
        CLEARED,
    }

    fun notifyItemListChanged(notify: Notify, index: Int, itemObj: T)
}
