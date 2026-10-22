package cn.alvkeke.dropto.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import cn.alvkeke.dropto.data.PasswordHelper
import cn.alvkeke.dropto.data.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The local accounts of the shared `localuser.db`, [DataManager] exposes them to the UI. */
object LocalUserManager {

    private const val TAG = "LocalUserManager"

    private lateinit var appContext: Context

    private val database: SQLiteDatabase by lazy {
        LocalUserDBHelper(appContext).writableDatabase
    }

    /** Called once from the application, like [DataManager.init]. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isLocalUser(user: User): Boolean =
        user.domain == User.LOCAL_DOMAIN && User.isValidId(user.id)

    suspend fun exists(user: User): Boolean = withContext(Dispatchers.IO) {
        isLocalUser(user) && database.hasUser(user.id)
    }

    suspend fun hasPassword(user: User): Boolean = withContext(Dispatchers.IO) {
        isLocalUser(user) && !database.queryPassword(user.id).isNullOrEmpty()
    }

    suspend fun verifyUser(user: User, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalUser(user) || !database.hasUser(user.id)) {
            Log.i(TAG, "No such local user: ${user.fullUserId}")
            return@withContext false
        }

        val stored = database.queryPassword(user.id) ?: return@withContext false
        PasswordHelper.matches(password, stored)
    }

    /** An id that is taken is never modified, an empty password registers an account without one. */
    suspend fun create(user: User, password: String): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalUser(user)) {
            Log.i(TAG, "Refusing to register the user: ${user.fullUserId}")
            return@withContext false
        }

        if (database.hasUser(user.id)) {
            Log.i(TAG, "User exists already: ${user.fullUserId}")
            return@withContext false
        }

        val encoded = if (password.isEmpty()) "" else PasswordHelper.encode(password)
        try {
            database.insertUser(user.id, user.name ?: user.id, null, encoded) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register the user ${user.fullUserId}", e)
            false
        }
    }

    suspend fun profile(user: User): LocalUserInfo? = withContext(Dispatchers.IO) {
        if (!isLocalUser(user)) null else database.queryUser(user.id)
    }

    suspend fun users(): ArrayList<LocalUserInfo> = withContext(Dispatchers.IO) {
        ArrayList<LocalUserInfo>().also { database.queryUsers(it) }
    }

    /** A null field in [info] clears the stored value. */
    suspend fun updateProfile(info: LocalUserInfo): Boolean = withContext(Dispatchers.IO) {
        if (!User.isValidId(info.userId)) return@withContext false

        database.updateUser(info) > 0
    }

    /** An empty or null password removes the password. */
    suspend fun setPassword(user: User, password: String?): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalUser(user) || !database.hasUser(user.id)) {
            Log.i(TAG, "No such local user: ${user.fullUserId}")
            return@withContext false
        }

        val encoded = if (password.isNullOrEmpty()) "" else PasswordHelper.encode(password)
        database.updateUserPassword(user.id, encoded) > 0
    }

    /** Removes the account row only, the notes live in their own database. */
    suspend fun delete(user: User): Boolean = withContext(Dispatchers.IO) {
        if (!isLocalUser(user)) return@withContext false

        database.deleteUser(user.id) > 0
    }
}
