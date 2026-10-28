package cn.alvkeke.dropto.provider.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.sqlite.transaction
import cn.alvkeke.dropto.provider.model.CategoryRecord
import cn.alvkeke.dropto.provider.model.NoteRecord
import cn.alvkeke.dropto.provider.model.UserRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The provider that keeps everything on this device: the accounts of the device (`localuser.db`),
 * the categories and notes of the active user (`data.db`) and the database files behind them.
 *
 * One provider is a self contained unit, a provider for a server would be a sibling of this class
 * with the same shape instead of a part of it.
 */
class LocalProvider(context: Context) {

    private val appContext = context.applicationContext

    private val accountDatabase: SQLiteDatabase by lazy {
        LocalUserDBHelper(appContext).writableDatabase
    }

    /** The database of the active user, bound by [openUser]. */
    private var userDatabaseFile: File? = null

    // ----------------------------------------------------------------- files

    fun openUser(fullUserId: String) {
        userDatabaseFile = databaseFileOf(fullUserId)
    }

    fun closeUser() {
        userDatabaseFile = null
    }

    private fun userDatabase(): File = userDatabaseFile ?: error("No active user")

    private fun databaseFileOf(fullUserId: String): File {
        val dir = File(File(appContext.dataDir, DATABASES_DIR), fullUserId)
        if (!dir.isDirectory) dir.mkdirs()
        return File(dir, DATABASE_NAME)
    }

    fun legacyFile(): File? {
        val oldDb = File(File(appContext.dataDir, DATABASES_DIR), LEGACY_DATABASE_NAME)
        return if (oldDb.isFile) oldDb else null
    }

    private fun <T> read(block: (SQLiteDatabase) -> T): T =
        DataBaseHelper(appContext, userDatabase()).readableDatabase.use(block)

    private fun <T> write(block: (SQLiteDatabase) -> T): T =
        DataBaseHelper(appContext, userDatabase()).writableDatabase.use(block)

    // --------------------------------------------------------------- accounts

    suspend fun exists(userId: String): Boolean = withContext(Dispatchers.IO) {
        accountDatabase.hasUser(userId)
    }

    suspend fun hasPassword(userId: String): Boolean = withContext(Dispatchers.IO) {
        !accountDatabase.queryPassword(userId).isNullOrEmpty()
    }

    suspend fun verifyUser(userId: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!accountDatabase.hasUser(userId)) {
                Log.i(TAG, "No such local user: $userId")
                return@withContext false
            }

            val stored = accountDatabase.queryPassword(userId) ?: return@withContext false
            PasswordHelper.matches(password, stored)
        }

    /** An id that is taken is never modified, an empty password registers an account without one. */
    suspend fun createUser(record: UserRecord, password: String): Boolean =
        withContext(Dispatchers.IO) {
            if (accountDatabase.hasUser(record.userId)) {
                Log.i(TAG, "User exists already: ${record.userId}")
                return@withContext false
            }

            val encoded = if (password.isEmpty()) "" else PasswordHelper.encode(password)
            try {
                accountDatabase.insertUser(
                    record.userId, record.displayName, record.avatar, encoded
                ) > 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register the user ${record.userId}", e)
                false
            }
        }

    suspend fun profile(userId: String): UserRecord? = withContext(Dispatchers.IO) {
        accountDatabase.queryUser(userId)
    }

    suspend fun users(): List<UserRecord> = withContext(Dispatchers.IO) {
        ArrayList<UserRecord>().also { accountDatabase.queryUsers(it) }
    }

    /** A null field in [record] clears the stored value. */
    suspend fun updateProfile(record: UserRecord): Boolean = withContext(Dispatchers.IO) {
        if (record.userId.isBlank()) return@withContext false

        accountDatabase.updateUser(record) > 0
    }

    /** An empty or null password removes the password. */
    suspend fun setPassword(userId: String, password: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (!accountDatabase.hasUser(userId)) {
                Log.i(TAG, "No such local user: $userId")
                return@withContext false
            }

            val encoded = if (password.isNullOrEmpty()) "" else PasswordHelper.encode(password)
            accountDatabase.updateUserPassword(userId, encoded) > 0
        }

    /** Removes the account row only, the notes live in their own database. */
    suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        accountDatabase.deleteUser(userId) > 0
    }

    // ------------------------------------------------------------- categories

    fun loadCategories(): List<CategoryRecord> {
        val records = ArrayList<CategoryRecord>()
        read { db -> db.queryCategory(records) }
        return records
    }

    /** @return the id assigned by the database */
    fun insertCategory(record: CategoryRecord): Long = write { db -> db.insertCategory(record) }

    /** @return the number of affected rows */
    fun updateCategory(record: CategoryRecord): Int = write { db -> db.updateCategory(record) }

    /**
     * Remove a category together with its notes, no note may outlive its category.
     * @return the number of removed category rows
     */
    fun deleteCategory(id: Long): Int = write { db ->
        // the notes have no foreign key, the cascade and the row removal go in one transaction
        db.transaction {
            // explicit receiver: the same names exist as members of this provider
            this.deleteNotes(id)
            this.deleteCategory(id)
        }
    }

    fun reorderCategories(ids: List<Long>) {
        write { db -> db.reorderCategories(ids) }
    }

    // ------------------------------------------------------------------ notes

    fun loadNotes(categoryId: Long): List<NoteRecord> {
        val records = ArrayList<NoteRecord>()
        read { db -> db.queryNote(categoryId, records) }
        return records
    }

    /** @return the id assigned by the database */
    fun insertNote(record: NoteRecord): Long = write { db -> db.insertNote(record) }

    /** @return the number of affected rows */
    fun updateNote(record: NoteRecord): Int = write { db -> db.updateNote(record) }

    /** @return the number of affected rows */
    fun setNoteDeleted(id: Long, deleted: Boolean): Int = write { db ->
        if (deleted) db.deleteNote(id) else db.restoreNote(id)
    }

    // -------------------------------------------------------------- reactions

    fun loadReactions(): List<String> = read { db -> db.getReactionList() }

    fun saveReactions(reactions: List<String>) {
        write { db -> db.updateReactionList(reactions) }
    }

    // ------------------------------------------------- database housekeeping

    /** Create or open the database of the active user. */
    fun prepare() {
        write { }
    }

    fun destroy() {
        SQLiteDatabase.deleteDatabase(userDatabase())
    }

    /** Merge the WAL of the active user's database back, then hand the file over for export. */
    fun exportFile(): File = prepareExport(userDatabase())

    /**
     * Copy the database of the layout used before users existed into the database of
     * [targetFullUserId]. The old file is kept, a failed copy leaves no half written file behind.
     */
    fun migrateLegacy(targetFullUserId: String): LegacyMigration {
        val oldDb = legacyFile() ?: return LegacyMigration.NO_LEGACY
        val target = databaseFileOf(targetFullUserId)
        if (target.isFile) {
            Log.i(TAG, "Database of the target user already exists, nothing to migrate")
            return LegacyMigration.ALREADY_MIGRATED
        }

        // the wal may hold committed notes that were never written back to the file
        prepareExport(oldDb)

        val tmp = File(target.parentFile, target.name + ".tmp")
        return try {
            oldDb.copyTo(tmp, true)
            if (!tmp.renameTo(target)) {
                Log.e(TAG, "Failed to put the copied database in place: ${target.absolutePath}")
                tmp.delete()
                return LegacyMigration.FAILED
            }
            Log.i(TAG, "Database copied to ${target.absolutePath}")
            LegacyMigration.COPIED
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy the legacy database: $e")
            tmp.delete()
            LegacyMigration.FAILED
        }
    }

    /** Same as [exportFile], for the database left behind by the migration. */
    fun prepareLegacyExport(): File? {
        val oldDb = legacyFile() ?: return null
        return prepareExport(oldDb)
    }

    fun deleteLegacy() {
        val oldDb = legacyFile() ?: return
        val sideFiles = DATABASE_SIDE_SUFFIX.map { File(oldDb.parentFile, oldDb.name + it) }
        (listOf(oldDb) + sideFiles).forEach { file ->
            if (file.isFile && !file.delete()) {
                Log.e(TAG, "Failed to delete legacy database file: ${file.absolutePath}")
            }
        }
    }

    /**
     * Merge the WAL content back into the database file, so a copy of the file taken afterwards is
     * complete. The file is opened directly: it may be the legacy one, which must not be touched by
     * [DataBaseHelper]'s schema handling.
     */
    private fun prepareExport(file: File): File {
        try {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            ).use { it.checkpoint() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to checkpoint database ${file.name}: $e")
        }
        return file
    }

    enum class LegacyMigration {
        NO_LEGACY,
        ALREADY_MIGRATED,
        COPIED,
        FAILED,
    }

    companion object {
        private const val TAG = "LocalProvider"

        private const val DATABASES_DIR = "databases"
        private const val DATABASE_NAME = "data.db"

        // the layout used before users existed
        private const val LEGACY_DATABASE_NAME = "note.db"

        // sqlite derives these names from the database file name, they can only be handled together
        private val DATABASE_SIDE_SUFFIX = arrayOf("-journal", "-wal", "-shm")
    }
}
