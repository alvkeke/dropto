package cn.alvkeke.dropto.ui.intf;

public interface ListNotification {
    enum Notify {
        INSERTED,
        REMOVED,
        UPDATED,
    }

    void notifyItemListChanged(Notify notify, int index, Object object);
}
