package cn.alvkeke.dropto.ui.intf;

public interface ListNotification<T> {
    enum Notify {
        INSERTED,
        REMOVED,
        UPDATED,
        CLEARED,
    }

    void notifyItemListChanged(Notify notify, int index, T object);
}
