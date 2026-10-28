package cn.alvkeke.dropto.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import cn.alvkeke.dropto.provider.local.LocalProvider
import cn.alvkeke.dropto.provider.model.UserRecord
import cn.alvkeke.dropto.runtime.model.Category
import cn.alvkeke.dropto.runtime.model.NoteItem
import cn.alvkeke.dropto.runtime.model.User
import cn.alvkeke.dropto.service.CoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The only entry point to the data of the app, every query and write task goes through here.
 * It owns the runtime data the UI observes, drives the provider, and translates between the provider
 * records and the runtime models, so business code never touches a provider, its SQL extensions or
 * [CoreService] directly.
 */
object DataManager {

    const val TAG: String = "DataManager"

    private lateinit var appContext: Context

    private lateinit var localProvider: LocalProvider

    /** Called once from the application, operations may outlive the UI that asked for them. */
    fun init(context: Context) {
        appContext = context.applicationContext
        localProvider = LocalProvider(appContext)
    }

    // the network side is not implemented yet, this is the seam where it will be driven from
    @Suppress("unused")
    var coreService: CoreService? = null

    var currentUser: User? = null
        private set
    var currentUserAvator: Drawable? = null
        private set

    private suspend fun getUserAvatar(user: User): Drawable? = withContext(Dispatchers.IO) {
        when (user.domain) {
            User.LOCAL_DOMAIN -> {
                appContext.applicationInfo.loadIcon(appContext.packageManager)
            }
            else -> {
                Log.i(TAG, "Unsupported user domain: ${user.domain}")
                null
            }
        }
    }

    private const val PREFERENCE_NAME = "active_user"
    private const val KEY_USER_DOMAIN = "active_user_domain"
    private const val KEY_USER_ID = "active_user_id"

    private fun userPreference() =
        appContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    suspend fun loadActiveUser(): Boolean = withContext(Dispatchers.IO) {
        val prefs = userPreference()
        val userId = prefs.getString(KEY_USER_ID, null)
        val userDomain = prefs.getString(KEY_USER_DOMAIN, null)
        if (userDomain == null || userId == null) {
            Log.i(TAG, "No active user saved in the preference")
            return@withContext false
        }

        if (!User.isValidId(userId)) {
            Log.i(TAG, "Invalid User ID")
            return@withContext false
        }

        val user = User(userDomain, userId)
        when (user.domain) {
            User.LOCAL_DOMAIN -> {
                val profile = localProvider.profile(user.id)
                if (profile == null) {
                    Log.i(TAG, "No such local user: ${user.fullUserId}")
                    return@withContext false
                }
                user.name = profile.nickname
                user.avatar = profile.avatar
                currentUserAvator = getUserAvatar(user)
            }
            else -> {
                Log.i(TAG, "Unsupported user domain: ${user.domain}")
                return@withContext false
            }
        }

        localProvider.openUser(user.fullUserId)
        currentUser = user
        Log.i(TAG, "User logged in: ${user.fullUserId}")
        true
    }

    @SuppressLint("UseKtx")
    suspend fun activateUser(user: User): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalUser(user)) {
            Log.i(TAG, "Unsupported user: ${user.fullUserId}")
            return@withContext false
        }

        // commit, the database path is derived from this record as soon as the login returns
        val saved = userPreference().edit()
            .putString(KEY_USER_DOMAIN, user.domain)
            .putString(KEY_USER_ID, user.id)
            .commit()
        if (!saved) {
            Log.e(TAG, "Failed to save the active user: ${user.fullUserId}")
            return@withContext false
        }

        loadActiveUser()
    }

    suspend fun deactivateUser(): Boolean = withContext(Dispatchers.IO) {
        val cleared = userPreference().edit()
            .remove(KEY_USER_DOMAIN)
            .remove(KEY_USER_ID)
            .commit()
        if (!cleared) {
            Log.e(TAG, "Failed to clear the active user")
            return@withContext false
        }

        currentUser = null
        currentUserAvator = null
        localProvider.closeUser()
        Log.i(TAG, "User logged out")
        true
    }

    suspend fun updateActiveUserName(name: String?): Boolean = withContext(Dispatchers.IO) {
        if (name.isNullOrEmpty()) {
            Log.i(TAG, "Invalid name")
            return@withContext false
        }
        val user = currentUser ?: return@withContext false
        val updated = localProvider.updateProfile(
            UserRecord(userId = user.id, nickname = name, avatar = user.avatar)
        )
        if (!updated) {
            Log.e(TAG, "Failed to update the name of ${user.fullUserId}")
            return@withContext false
        }

        user.name = name
        true
    }

    // TODO: only Local user available now, implement network user in feature
    suspend fun registerUser(user: User, password: String): Boolean {
        if (!isLocalUser(user)) return false
        return localProvider.createUser(user.toRecord(), password)
    }

    suspend fun verifyUser(user: User, password: String): Boolean {
        if (!isLocalUser(user)) return false
        return localProvider.verifyUser(user.id, password)
    }

    /** The domain rules of this device, only the local accounts can be activated here. */
    private fun isLocalUser(user: User): Boolean =
        user.domain == User.LOCAL_DOMAIN && User.isValidId(user.id)

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

    fun prepareDatabase() {
        localProvider.prepare()
    }

    @Suppress("unused")
    fun destroyDatabase() {
        localProvider.destroy()
    }

    suspend fun migrateLegacyDatabase() = withContext(Dispatchers.IO) {
        // the notes of the old layout only belong to the local user, they land in its database
        when (localProvider.migrateLegacy(User.LOCAL.fullUserId)) {
            LocalProvider.LegacyMigration.NO_LEGACY,
            LocalProvider.LegacyMigration.FAILED -> return@withContext
            LocalProvider.LegacyMigration.ALREADY_MIGRATED,
            LocalProvider.LegacyMigration.COPIED -> addDefaultLocalUser()
        }
    }

    /** The migrated notes belong to the default user, that account has to exist and be active. */
    private suspend fun addDefaultLocalUser() {
        val created = localProvider.createUser(
            User.LOCAL.toRecord(), User.DEFAULT_LOCAL_USER_PWD
        )
        if (created) activateUser(User.LOCAL)
    }

    fun legacyDatabaseFile(): File? = localProvider.legacyFile()

    /** Same as [prepareDatabaseForExport], for the database left behind by the migration. */
    suspend fun prepareLegacyDatabaseForExport(): File? = withContext(Dispatchers.IO) {
        localProvider.prepareLegacyExport()
    }

    suspend fun deleteLegacyDatabase() = withContext(Dispatchers.IO) {
        localProvider.deleteLegacy()
    }

    @JvmStatic
    fun loadCategories(): ArrayList<Category> {
        // loading replaces the runtime list, callers may reload after the activity was recreated
        categories.clear()
        try {
            categories.addAll(localProvider.loadCategories().map { it.toRuntime() })
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
            category.noteItems.addAll(localProvider.loadNotes(category.id).map { it.toRuntime() })
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
            localProvider.loadReactions().toMutableList()
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
            category.id = localProvider.insertCategory(category.toRecord())
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
            // the store removes the notes of the category along with it
            if (0 == localProvider.deleteCategory(category.id)) {
                Log.e(TAG, "no category row be deleted in database")
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
            Log.i(TAG, "category not exist in list, abort")
            return -1
        }
        return try {
            if (0 == localProvider.updateCategory(category.toRecord())) {
                Log.e(TAG, "no category row be updated")
            }
            index
        } catch (e: Exception) {
            Log.e(TAG, "Failed to modify Category: $e")
            -1
        }
    }

    private fun doReorderCategories(categories: List<Category>): Boolean {
        return try {
            localProvider.reorderCategories(categories.map { it.id })
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
            note.id = localProvider.insertNote(note.toRecord())
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
            if (0 == localProvider.setNoteDeleted(note.id, true)) Log.e(TAG, "no row be deleted")
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
            if (0 == localProvider.setNoteDeleted(note.id, false)) {
                Log.e(TAG, "no row be restored in database")
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
            val updated = localProvider.updateNote(newItem.toRecord())
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
            localProvider.saveReactions(reactions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save reaction list: $e")
        }
    }

    /**
     * Merge the WAL content back into the database file, so a copy of the file taken afterwards is
     * complete.
     */
    suspend fun prepareDatabaseForExport(): File = withContext(Dispatchers.IO) {
        localProvider.exportFile()
    }
}
