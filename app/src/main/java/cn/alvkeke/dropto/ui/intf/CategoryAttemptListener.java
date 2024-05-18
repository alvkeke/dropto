package cn.alvkeke.dropto.ui.intf;

import cn.alvkeke.dropto.data.Category;

public interface CategoryAttemptListener {
    enum Attempt {
        CREATE,
        REMOVE,
        UPDATE,
        SHOW_DETAIL,
        SHOW_CREATE,
        SHOW_EXPAND,
    }
    void onAttempt(CategoryAttemptListener.Attempt attempt, Category category);
}

