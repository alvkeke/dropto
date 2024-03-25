package cn.alvkeke.dropto.ui.intf;

public interface ListNotification {
    enum Notify {
        CREATED,
        REMOVED,
        MODIFIED,
    }

    void notifyItemListChanged(Notify notify, int index, Object object);
}
