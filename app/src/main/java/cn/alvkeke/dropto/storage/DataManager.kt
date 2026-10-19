package cn.alvkeke.dropto.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.CoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The only entry point to the data of the app: queries and write tasks all happen here, and the
 * network sync will be driven from here as well. Business code must not use [DataBaseHelper], the
 * SQL extensions or [CoreService] directly.
 */
object DataManager {

    const val TAG: String = "DataManager"

    private lateinit var appContext: Context

    /** Called once from the application, operations may outlive the UI that asked for them. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // the network side is not implemented yet, this is the seam where it will be driven from
    @Suppress("unused")
    var coreService: CoreService? = null

    @JvmStatic
    var categories: ArrayList<Category> = ArrayList()
        private set

    @JvmStatic
    fun findCategory(id: Long): Category? {
        for (c in categories) {
            if (c.id == id) return c
        }
        return null
    }

    private val taskScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = ArrayList<DataManagerListener>()

    fun addTaskListener(listener: DataManagerListener) {
        listeners.add(listener)
    }

    fun delTaskListener(listener: DataManagerListener) {
        listeners.remove(listener)
    }

    // results go to the main thread, so the listeners may touch the UI right away
    private suspend fun dispatchResult(block: (DataManagerListener) -> Unit) {
        withContext(Dispatchers.Main) {
            listeners.forEach { block(it) }
        }
    }

    fun databaseFile(): File {
        return appContext.getDatabasePath(DataBaseHelper.DATABASE_NAME)
    }

    /** Creates or upgrades the database, so that it is ready before the first real access. */
    fun prepareDatabase() {
        write { }
    }

    @Suppress("unused")
    fun destroyDatabase() {
        SQLiteDatabase.deleteDatabase(databaseFile())
    }

    @JvmStatic
    fun loadCategories(): ArrayList<Category> {
        // loading replaces the runtime list, callers may reload after the activity was recreated
        categories.clear()
        try {
            read { db -> db.queryCategory(categories) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve category data from database: $e")
        }
        return categories
    }

    @JvmStatic
    fun loadCategoryNotes(category: Category): Boolean {
        if (category.isInitialized)
            return true

        try {
            read { db -> db.queryNote(category.id, category.noteItems) }
            category.isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load notes for category, $e")
            e.printStackTrace()
            return false
        }
        return true
    }

    fun getReactionList(): MutableList<String> {
        return try {
            read { db -> db.getReactionList() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get reaction list: $e")
            mutableListOf()
        }
    }

    fun createCategory(category: Category) {
        taskScope.launch {
            val result = doCreateCategory(category)
            dispatchResult { it.onCategoryCreated(result, category) }
        }
    }

    fun removeCategory(category: Category) {
        taskScope.launch {
            val result = doRemoveCategory(category)
            dispatchResult { it.onCategoryRemoved(result, category) }
        }
    }

    fun updateCategory(category: Category) {
        taskScope.launch {
            val result = doUpdateCategory(category)
            dispatchResult { it.onCategoryUpdated(result, category) }
        }
    }

    fun reorderCategory(categories: List<Category>) {
        taskScope.launch { doReorderCategories(categories) }
    }

    fun createNote(note: NoteItem) {
        taskScope.launch {
            val result = doCreateNote(note)
            dispatchResult { it.onNoteCreated(result, note) }
        }
    }

    fun removeNote(note: NoteItem) {
        taskScope.launch {
            val result = doRemoveNote(note) ?: return@launch
            dispatchResult { it.onNoteRemoved(result, note) }
        }
    }

    fun restoreNote(note: NoteItem) {
        taskScope.launch {
            val result = doRestoreNote(note) ?: return@launch
            dispatchResult { it.onNoteRestored(result, note) }
        }
    }

    fun updateNote(newItem: NoteItem) {
        taskScope.launch {
            val result = doUpdateNote(newItem) ?: return@launch
            dispatchResult { it.onNoteUpdated(result, newItem) }
        }
    }

    fun saveReactionList(reactions: List<String>) {
        taskScope.launch { doSaveReactionList(reactions) }
    }

    private fun doCreateCategory(category: Category): Int {
        return try {
            write { db -> db.insertCategory(category) }
            categories.add(category)
            categories.indexOf(category)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add new category to database: $e")
            -1
        }
    }

    private fun doRemoveCategory(category: Category): Int {
        val index = categories.indexOf(category)
        if (index == -1) {
            Log.i(TAG, "category not exist in list, abort")
            return -1
        }
        return try {
            write { db ->
                db.deleteNotes(category.id)
                if (0 == db.deleteCategory(category.id)) {
                    Log.e(TAG, "no category row be deleted in database")
                }
            }
            categories.remove(category)
            index
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove category with id ${category.id}: $e")
            -1
        }
    }

    private fun doUpdateCategory(category: Category): Int {
        val index = categories.indexOf(category)
        if (index == -1) {
            Log.e(TAG, "category not exist in list, abort")
            return -1
        }
        return try {
            write { db ->
                if (0 == db.updateCategory(category)) {
                    Log.e(TAG, "no category row be updated")
                }
            }
            index
        } catch (e: Exception) {
            Log.e(TAG, "Failed to modify Category: $e")
            -1
        }
    }

    private fun doReorderCategories(categories: List<Category>): Boolean {
        return try {
            write { db -> db.reorderCategories(categories) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reorder categories: $e")
            false
        }
    }

    /** @return index of the new note, -1 when it could not be created */
    private fun doCreateNote(note: NoteItem): Int {
        val category = findCategory(note.categoryId)
        if (category == null) {
            Log.e(TAG, "Failed to find category with id ${note.categoryId}")
            return -1
        }

        note.categoryId = category.id
        try {
            write { db -> db.insertNote(note) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add new item to database!", e)
            return -1
        }

        if (!category.isInitialized) {
            Log.i(TAG, "category not initialized, not add new item in the list")
            return -1
        }
        return category.addNoteItem(note)
    }

    /** @return index of the removed note, -1 on failure, null when the note is not known here */
    private fun doRemoveNote(note: NoteItem): Int? {
        val category = findCategory(note.categoryId)
        if (category == null) {
            Log.e(TAG, "Failed to find category with id ${note.categoryId}")
            return null
        }
        val index = category.indexNoteItem(note)
        if (index == -1) return null

        return try {
            write { db ->
                if (0 == db.deleteNote(note.id)) Log.e(TAG, "no row be deleted")
            }
            // TODO: need to think how to treat the deleted item, mark it as deleted now
            note.isDeleted = true
            index
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove item with id ${note.id}: $e")
            -1
        }
    }

    /** @return index of the restored note, -1 on failure, null when the note is not known here */
    private fun doRestoreNote(note: NoteItem): Int? {
        val category = findCategory(note.categoryId)
        if (category == null) {
            Log.e(TAG, "Failed to find category with id ${note.categoryId}")
            return null
        }
        val index = category.indexNoteItem(note)
        if (index == -1) return null

        return try {
            write { db ->
                if (0 == db.restoreNote(note.id)) {
                    Log.e(TAG, "no row be restored in database")
                }
            }
            note.isDeleted = false
            index
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore item with id ${note.id}: $e")
            -1
        }
    }

    /** @return index of the updated note, -1 on failure, null when the note is not known here */
    private fun doUpdateNote(newItem: NoteItem): Int? {
        val category = findCategory(newItem.categoryId)
        if (category == null) {
            Log.e(TAG, "Failed to find category with id ${newItem.categoryId}")
            return null
        }
        val oldItem = category.findNoteItem(newItem.id)
        if (oldItem == null) {
            Log.e(TAG, "Failed to get note item with id ${newItem.id}")
            return null
        }

        val index = category.indexNoteItem(oldItem)
        newItem.id = oldItem.id
        return try {
            val updated = write { db -> db.updateNote(newItem) }
            if (0 == updated) {
                Log.i(TAG, "no item was updated")
                -1
            } else {
                oldItem.updateFrom(newItem)
                index
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update note item in database: $e")
            -1
        }
    }

    private fun doSaveReactionList(reactions: List<String>) {
        try {
            write { db -> db.updateReactionList(reactions) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save reaction list: $e")
        }
    }

    /**
     * Merge the WAL content back into the database file, so a copy of the file taken afterwards is
     * complete.
     */
    suspend fun prepareDatabaseForExport(): File = withContext(Dispatchers.IO) {
        write { db -> db.checkpoint() }
        databaseFile()
    }

    private fun <T> read(block: (SQLiteDatabase) -> T): T {
        return DataBaseHelper(appContext).readableDatabase.use(block)
    }

    private fun <T> write(block: (SQLiteDatabase) -> T): T {
        return DataBaseHelper(appContext).writableDatabase.use(block)
    }
}
