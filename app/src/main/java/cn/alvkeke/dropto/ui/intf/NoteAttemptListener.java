package cn.alvkeke.dropto.ui.intf;

import java.util.ArrayList;

import cn.alvkeke.dropto.data.NoteItem;

public interface NoteAttemptListener {
    enum Attempt {
        CREATE,
        REMOVE,
        UPDATE,
        COPY,
        SHOW_DETAIL,
        SHOW_SHARE,
        SHOW_IMAGE,
    }
    void onAttempt(Attempt attempt, NoteItem e);
    void onAttempt(Attempt attempt, NoteItem e, Object ext);
    void onAttemptBatch(Attempt attempt, ArrayList<NoteItem> noteItems);
}
