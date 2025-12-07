package cn.alvkeke.dropto.ui.intf

import cn.alvkeke.dropto.data.Category

interface CategoryAttemptListener {
    enum class Attempt {
        CREATE,
        REMOVE,
        UPDATE,
        SHOW_DETAIL,
        SHOW_CREATE,
        SHOW_EXPAND,
        DEBUG_ADD_DATA,
    }

    fun onAttempt(attempt: Attempt, category: Category?)
}

