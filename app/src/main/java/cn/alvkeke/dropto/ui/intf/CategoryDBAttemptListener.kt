package cn.alvkeke.dropto.ui.intf

import cn.alvkeke.dropto.data.Category

interface CategoryDBAttemptListener {
    enum class Attempt {
        CREATE,
        REMOVE,
        UPDATE,
    }

    fun onAttempt(attempt: Attempt, category: Category)
}

