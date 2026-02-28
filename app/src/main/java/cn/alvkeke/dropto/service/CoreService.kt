package cn.alvkeke.dropto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.DataBaseHelper
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.DataLoader.findCategory
import cn.alvkeke.dropto.storage.deleteCategory
import cn.alvkeke.dropto.storage.deleteNote
import cn.alvkeke.dropto.storage.deleteNotes
import cn.alvkeke.dropto.storage.insertCategory
import cn.alvkeke.dropto.storage.insertNote
import cn.alvkeke.dropto.storage.restoreNote
import cn.alvkeke.dropto.storage.updateCategory
import cn.alvkeke.dropto.storage.updateNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoreService : Service() {
    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private val binder = CoreSrvBinder()

    inner class CoreSrvBinder : Binder() {
        val service: CoreService
            get() = this@CoreService
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_STR_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Notification channel for foreground service of CoreService.\n" +
                "Can be disabled to prevent annoying notification"
        val notificationManager =
            getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_STR_ID).build()
    }

    private fun startForeground() {
        val notification = createNotification()
        try {
            startForeground(CHANNEL_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            Log.d(TAG, "startForeground finished")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground: $e")
            Toast.makeText(
                this, "Failed to start coreService",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CoreService onCreate")
        startForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CoreService onDestroy")
        _serviceScope?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @JvmField
    var resultListener: CoreServiceListener? = null

    private var _serviceScope : CoroutineScope? = null
    private val serviceScope : CoroutineScope
        get() {
            if (_serviceScope == null || _serviceScope?.isActive != true) {
                _serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            return _serviceScope!!
        }

    private suspend fun handleTaskCategoryCreate(category: Category) {

        var result: Int
        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                category.id = db.insertCategory(category)
                val categories: ArrayList<Category> = categories
                categories.add(category)
                result = categories.indexOf(category)
            }
        } catch (_: Exception) {
            Log.e(TAG, "Failed to add new category to database")
            result = -1
        }
        withContext(Dispatchers.Main) {
            resultListener?.onCategoryCreated(result, category)
        }

    }
    fun createCategory(category: Category) {
        serviceScope.launch { handleTaskCategoryCreate(category) }
    }

    private suspend fun handleTaskCategoryRemove(category: Category) {
        val categories: ArrayList<Category> = categories

        val index: Int
        var result = -1
        if ((categories.indexOf(category).also { index = it }) == -1) {
            Log.i(TAG, "category not exist in list, abort")
            return
        }

        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                db.deleteNotes(category.id)
                if (0 == db.deleteCategory(category.id)) Log.e(
                    TAG,
                    "no category row be deleted in database"
                )
                categories.remove(category)
                result = index
            }
        } catch (ex: Exception) {
            Log.e(
                TAG, "Failed to remove category with id " +
                        category.id + ", exception: " + ex
            )
        }
        withContext(Dispatchers.Main) {
            resultListener?.onCategoryRemoved(result, category)
        }
    }
    fun removeCategory(category: Category) {
        serviceScope.launch { handleTaskCategoryRemove(category) }
    }

    private suspend fun handleTaskCategoryUpdate(category: Category) {
        val categories: ArrayList<Category> = categories

        val index: Int
        var result = -1
        if (-1 == (categories.indexOf(category).also { index = it })) {
            Log.e(TAG, "category not exist in list, abort")
            return
        }

        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                if (0 == db.updateCategory(category)) {
                    Log.e(TAG, "no category row be updated")
                }
                result = index
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to modify Category: $ex")
        }

        withContext(Dispatchers.Main) {
            resultListener?.onCategoryUpdated(result, category)
        }
    }
    fun updateCategory(category: Category) {
        serviceScope.launch { handleTaskCategoryUpdate(category) }
    }


    private suspend fun handleTaskNoteRemove(e: NoteItem) {
        val c = findCategory(e.categoryId)
        if (c == null) {
            Log.e(TAG, "Failed to find category with id " + e.categoryId)
            return
        }

        val index = c.indexNoteItem(e)
        if (index == -1) return

        var result = -1
        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                if (0 == db.deleteNote(e.id)) Log.e(TAG, "no row be deleted")
                e.isDeleted = true  // TODO: need to think how to treat the deleted item, mark it as deleted now
                result = index
            }
        } catch (_: Exception) {
            Log.e(
                TAG, "Failed to remove item with id " +
                        e.id + ", exception: " + e
            )
        }
        withContext(Dispatchers.Main) {
            resultListener?.onNoteRemoved(result, e)
        }
    }
    fun removeNote(e: NoteItem) {
        serviceScope.launch { handleTaskNoteRemove(e) }
    }

    private suspend fun handleTaskNoteRestore(e: NoteItem) {
        val c = findCategory(e.categoryId)
        if (c == null) {
            Log.e(TAG, "Failed to find category with id " + e.categoryId)
            return
        }

        val index = c.indexNoteItem(e)
        if (index == -1) return

        var result = -1
        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                if (0 == db.restoreNote(e.id)) Log.e(
                    TAG,
                    "no row be restored in database"
                )
                e.isDeleted = false
                result = index
            }
        } catch (_: Exception) {
            Log.e(
                TAG, "Failed to restore item with id " +
                        e.id + ", exception: " + e
            )
        }
        withContext(Dispatchers.Main) {
            resultListener?.onNoteRestored(result, e)
        }
    }
    fun restoreNote(e: NoteItem) {
        serviceScope.launch { handleTaskNoteRestore(e) }
    }

    private suspend fun handleTaskNoteUpdate(newItem: NoteItem) {
        val c = findCategory(newItem.categoryId)
        if (c == null) {
            Log.e(TAG, "Failed to find category with id " + newItem.categoryId)
            return
        }

        val oldItem = c.findNoteItem(newItem.id)
        if (oldItem == null) {
            Log.e(TAG, "Failed to get note item with id " + newItem.id)
            return
        }
        val index = c.indexNoteItem(oldItem)
        var result = -1
        newItem.id = oldItem.id
        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                if (0 == db.updateNote(newItem)) {
                    Log.i(TAG, "no item was updated")
                } else {
                    oldItem.updateFrom(newItem)
                    result = index
                }
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to update note item in database: $exception")
        }
        withContext(Dispatchers.Main) {
            resultListener?.onNoteUpdated(result, newItem)
        }
    }
    fun updateNote(newItem: NoteItem) {
        serviceScope.launch { handleTaskNoteUpdate(newItem) }
    }

    private suspend fun handleCreateNote(newItem: NoteItem) {
        val category = findCategory(newItem.categoryId)
        var result = -1
        if (category == null) {
            Log.e(TAG, "Failed to find category with id " + newItem.categoryId)
            withContext(Dispatchers.Main) {
                resultListener?.onNoteCreated(result, newItem)
            }
            return
        }

        newItem.categoryId = category.id
        try {
            DataBaseHelper(this).writableDatabase.use { db ->
                newItem.id = db.insertNote(newItem)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add new item to database!", e)
            withContext(Dispatchers.Main) {
                resultListener?.onNoteCreated(result, newItem)
            }
            return
        }

        if (!category.isInitialized) {
            Log.i(TAG, "category not initialized, not add new item in the list")
            withContext(Dispatchers.Main) {
                resultListener?.onNoteCreated(result, newItem)
            }
            return
        }
        result = category.addNoteItem(newItem)
        withContext(Dispatchers.Main) {
            resultListener?.onNoteCreated(result, newItem)
        }
    }
    fun createNote(newItem: NoteItem) {
        serviceScope.launch { handleCreateNote(newItem) }
    }


    companion object {
        private const val TAG = "CoreService"
        private const val CHANNEL_ID = 100
        private const val CHANNEL_STR_ID = "CHANNEL_ID_CORE_DROP_TO"
        private const val CHANNEL_NAME = "CoreService"
    }
}