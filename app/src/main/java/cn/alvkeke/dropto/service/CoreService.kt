package cn.alvkeke.dropto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.service.Task.ResultListener
import cn.alvkeke.dropto.storage.DataBaseHelper
import cn.alvkeke.dropto.storage.DataLoader.categories
import cn.alvkeke.dropto.storage.DataLoader.findCategory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.i(this.toString(), "SDK version lower then Q(29), skip")
                return
            }
            startForeground(CHANNEL_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            Log.d(this.toString(), "startForeground finished")
        } catch (e: Exception) {
            Log.e(this.toString(), "Failed to startForeground: $e")
            Toast.makeText(
                this, "Failed to start coreService",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(this.toString(), "CoreService onCreate")
        startForeground()
        startTaskRunnerThread()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(this.toString(), "CoreService onDestroy")
        tryStopTaskRunnerThread()
        stopForeground(true)
    }

    private val listeners = ArrayList<ResultListener>()

    fun addTaskListener(listener: ResultListener) {
        this.listeners.add(listener)
    }

    fun delTaskListener(listener: ResultListener) {
        this.listeners.remove(listener)
    }

    private fun interface TaskListenerIterator {
        fun onIterate(listener: ResultListener)
    }

    private fun iterateTaskListener(iterator: TaskListenerIterator) {
        for (listener in listeners) {
            iterator.onIterate(listener)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private fun notifyListener(task: Task) {
        handler.post {
            iterateTaskListener { listener: ResultListener ->
                listener.onTaskFinish(task)
            }
        }
    }

    private fun handleTaskCategoryCreate(task: Task) {
        val category = task.taskObj as Category

        try {
            DataBaseHelper(this).use { helper ->
                helper.start()
                category.id = helper.insertCategory(category)
                helper.finish()
                val categories: ArrayList<Category> = categories
                categories.add(category)
                task.result = categories.indexOf(category)
            }
        } catch (_: Exception) {
            Log.e(this.toString(), "Failed to add new category to database")
            task.result = -1
        }
        notifyListener(task)
    }

    private fun handleTaskCategoryRemove(task: Task) {
        val categories: ArrayList<Category> = categories
        val category = task.taskObj as Category

        val index: Int
        if ((categories.indexOf(category).also { index = it }) == -1) {
            Log.i(this.toString(), "category not exist in list, abort")
            return
        }

        try {
            DataBaseHelper(this).use { helper ->
                helper.start()
                helper.deleteNotes(category.id)
                if (0 == helper.deleteCategory(category.id)) Log.e(
                    this.toString(),
                    "no category row be deleted in database"
                )
                helper.finish()
                categories.remove(category)
                task.result = index
            }
        } catch (ex: Exception) {
            Log.e(
                this.toString(), "Failed to remove category with id " +
                        category.id + ", exception: " + ex
            )
            task.result = -1
        }
        notifyListener(task)
    }

    private fun handleTaskCategoryUpdate(task: Task) {
        val categories: ArrayList<Category> = categories
        val category = task.taskObj as Category

        val index: Int
        if (-1 == (categories.indexOf(category).also { index = it })) {
            Log.e(this.toString(), "category not exist in list, abort")
            return
        }

        try {
            DataBaseHelper(this).use { helper ->
                helper.start()
                if (0 == helper.updateCategory(category)) {
                    Log.e(this.toString(), "no category row be updated")
                }
                helper.finish()
                task.result = index
            }
        } catch (ex: Exception) {
            Log.e(this.toString(), "Failed to modify Category: $ex")
            task.result = -1
        }
        notifyListener(task)
    }

    private fun handleTaskNoteCreate(task: Task) {
        val newItem = task.taskObj as NoteItem
        val category = findCategory(newItem.categoryId)
        if (category == null) {
            Log.e(this.toString(), "Failed to find category with id " + newItem.categoryId)
            task.result = -1
            notifyListener(task)
            return
        }

        newItem.categoryId = category.id
        try {
            DataBaseHelper(this).use { dbHelper ->
                dbHelper.start()
                newItem.id = dbHelper.insertNote(newItem)
                dbHelper.finish()
            }
        } catch (_: Exception) {
            Log.e(this.toString(), "Failed to add new item to database!")
            task.result = -1
            notifyListener(task)
            return
        }

        if (!category.isInitialized) {
            Log.i(this.toString(), "category not initialized, not add new item in the list")
            task.result = -1
            notifyListener(task)
            return
        }
        task.result = category.addNoteItem(newItem)
        notifyListener(task)
    }

    private fun handleTaskNoteRemove(task: Task) {
        val e = task.taskObj as NoteItem
        val c = findCategory(e.categoryId)
        if (c == null) {
            Log.e(this.toString(), "Failed to find category with id " + e.categoryId)
            return
        }

        val index = c.indexNoteItem(e)
        if (index == -1) return

        try {
            DataBaseHelper(this).use { dbHelper ->
                dbHelper.start()
                if (0 == dbHelper.deleteNote(e.id)) Log.e(this.toString(), "no row be deleted")
                dbHelper.finish()
                c.delNoteItem(e)
                task.result = index
            }
        } catch (_: Exception) {
            Log.e(
                this.toString(), "Failed to remove item with id " +
                        e.id + ", exception: " + e
            )
            task.result = -1
        }
        notifyListener(task)
    }

    private fun handleTaskNoteUpdate(task: Task) {
        val newItem = task.taskObj as NoteItem
        val c = findCategory(newItem.categoryId)
        if (c == null) {
            Log.e(this.toString(), "Failed to find category with id " + newItem.categoryId)
            return
        }

        val oldItem = c.findNoteItem(newItem.id)
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item with id " + newItem.id)
            return
        }
        val index = c.indexNoteItem(oldItem)
        newItem.id = oldItem.id
        try {
            DataBaseHelper(this).use { dbHelper ->
                dbHelper.start()
                if (0 == dbHelper.updateNote(newItem)) {
                    Log.i(this.toString(), "no item was updated")
                    task.result = -1
                } else {
                    dbHelper.finish()
                    oldItem.update(newItem, true)
                    task.result = index
                }
            }
        } catch (exception: Exception) {
            Log.e(this.toString(), "Failed to update note item in database: $exception")
            task.result = -1
        }
        notifyListener(task)
    }

    private fun distributeTask(task: Task) {
        if (task.type == Task.Type.Category) {
            when (task.job) {
                Task.Job.CREATE -> handleTaskCategoryCreate(task)
                Task.Job.REMOVE -> handleTaskCategoryRemove(task)
                Task.Job.UPDATE -> handleTaskCategoryUpdate(task)
            }
        } else if (task.type == Task.Type.NoteItem) {
            when (task.job) {
                Task.Job.CREATE -> handleTaskNoteCreate(task)
                Task.Job.REMOVE -> handleTaskNoteRemove(task)
                Task.Job.UPDATE -> handleTaskNoteUpdate(task)
            }
        }
    }

    private val tasks = ConcurrentLinkedQueue<Task>()
    private val taskSemaphore = Semaphore(0)

    private var taskRunnerThread: Thread? = null
    private var canTaskRunnerThreadRun = false
    private val taskRunner = Runnable {
        while (canTaskRunnerThreadRun) {
            try {
                taskSemaphore.acquire()
                val task = tasks.poll() ?: continue

                distributeTask(task)
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun startTaskRunnerThread() {
        taskRunnerThread = Thread(taskRunner)
        canTaskRunnerThreadRun = true
        taskRunnerThread!!.start()
    }

    private fun tryStopTaskRunnerThread() {
        canTaskRunnerThreadRun = false
        taskSemaphore.release() // release semaphore to exit waiting
        taskRunnerThread = null
    }

    fun queueTask(task: Task) {
        Thread { distributeTask(task) }.start()
//        tasks.offer(task);
//        taskSemaphore.release();
    }

    companion object {
        private const val CHANNEL_ID = 100
        private const val CHANNEL_STR_ID = "CHANNEL_ID_CORE_DROP_TO"
        private const val CHANNEL_NAME = "CoreService"
    }
}