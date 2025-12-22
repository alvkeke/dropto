package cn.alvkeke.dropto.storage

import android.content.Context
import android.util.Log
import cn.alvkeke.dropto.data.Category

object DataLoader {

    @JvmStatic
    var categories: ArrayList<Category> = ArrayList()
        private set

    @JvmStatic
    fun loadCategories(context: Context): ArrayList<Category> {

        try {
            DataBaseHelper(context).use { helper ->
                helper.start()
                helper.queryCategory(-1, categories)
                helper.finish()
            }
        } catch (_: Exception) {
            Log.e(this.toString(), "Failed to retrieve category data from database")
        }

        return categories
    }

    @JvmStatic
    fun findCategory(id: Long): Category? {
        for (c in categories) {
            if (c.id == id) return c
        }
        return null
    }

    @JvmStatic
    fun loadCategoryNotes(context: Context, category: Category): Boolean {
        if (category.isInitialized)
            return true

        try {
            DataBaseHelper(context).use { helper ->
                helper.start()
                helper.queryNote(-1, category.id, category.noteItems)
                category.isInitialized = true
                helper.finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "${e.printStackTrace()}")
            return false
        }
        return true
    }

}
