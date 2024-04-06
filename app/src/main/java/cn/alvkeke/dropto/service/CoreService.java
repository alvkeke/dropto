package cn.alvkeke.dropto.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class CoreService extends Service {

    public CoreService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return binder;
    }

    private final CoreSrvBinder binder = new CoreSrvBinder();
    public class CoreSrvBinder extends Binder {
        public CoreService getService() {
            return CoreService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private static final int CHANNEL_ID = 100;
    private static final String CHANNEL_STR_ID = "CHANNEL_ID_CORE_DROP_TO";
    private static final String CHANNEL_NAME = "CoreService";
    private Notification createNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return null;
        }

        NotificationChannel channel = new NotificationChannel(CHANNEL_STR_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notification channel for foreground service of CoreService.\n" +
                "Can be disabled to prevent annoying notification");
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        return new NotificationCompat.Builder(this, CHANNEL_STR_ID).build();
    }

    private void startForeground() {
        Notification notification = createNotification();
        if (notification == null) {
            Log.e(this.toString(), "Failed to create notification");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.i(this.toString(), "SDK version lower then Q(29), skip");
                return;
            }
            startForeground(CHANNEL_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            Log.d(this.toString(), "startForeground finished");
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to startForeground: " + e);
            Toast.makeText(this, "Failed to start coreService",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(this.toString(), "CoreService onCreate");
        startForeground();
        startTaskRunnerThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(this.toString(), "CoreService onDestroy");
        tryStopTaskRunnerThread();
        stopForeground(true);
    }

    public interface TaskResultListener {

        /**
         * indicate the task finished, this function will be invoked in MainThread
         * @param taskType operation type for the task, enum Op
         * @param index index of target Category, -1 for failed
         * @param c Category object
         */
        void onCategoryTaskFinish(Task.Type taskType, int index, Category c);

        /**
         * indicate the Note task finished, this function will be invoked in MainThread
         * @param taskType operation type for the tasks, enum Op
         * @param index index of target NoteItem, -1 for failed
         * @param n NoteItem object
         */
        void onNoteTaskFinish(Task.Type taskType, int index, NoteItem n);
    }

    private TaskResultListener listener;

    public void setListener(TaskResultListener listener) {
        this.listener = listener;
    }

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (listener == null) return;
            assert (msg.what >= 0 && msg.what < Task.taskTargets.length);
            assert msg.arg2 >= 0 && msg.arg2 < Task.taskTypes.length;
            Task.Target what = Task.taskTargets[msg.what];
            Log.e(this.toString(), "received msg: " + msg.toString());
            switch (what) {
                case Category:
                    listener.onCategoryTaskFinish(Task.taskTypes[msg.arg2], msg.arg1, (Category) msg.obj);
                    break;
                case NoteItem:
                    listener.onNoteTaskFinish(Task.taskTypes[msg.arg2], msg.arg1, (NoteItem) msg.obj);
                    break;
            }

        }
    };

    private int handleCategoryCreate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort creating");
            return -1;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            category.setId(helper.insertCategory(category));
            helper.finish();
            ArrayList<Category> categories = Global.getInstance().getCategories();
            categories.add(category);
            return categories.indexOf(category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new category to database");
            return -1;
        }
    }

    private int handleCategoryRemove(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort deleting");
            return -1;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        int index;
        if ((index = categories.indexOf(category)) == -1) {
            Log.i(this.toString(), "category not exist in list, abort");
            return -1;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.deleteCategory(category.getId()))
                Log.e(this.toString(), "no category row be deleted in database");
            helper.finish();
            categories.remove(category);
            return index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove category with id " +
                    category.getId() + ", exception: " + ex);
            return -1;
        }
    }

    private int handleCategoryUpdate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort modifying");
            return -1;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        int index;
        if (-1 == (index = categories.indexOf(category))) {
            Log.e(this.toString(), "category not exist in list, abort");
            return -1;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.updateCategory(category)) {
                Log.e(this.toString(), "no category row be updated");
            }
            helper.finish();
            return index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to modify Category: " + ex);
            return -1;
        }
    }

    private int deleteCategoryRecursion(Category category) {
        NoteItem e;
        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            while (null != (e = category.getNoteItem(0))) {
                if (0 == helper.deleteNote(e.getId()))
                    Log.i(this.toString(), "no row be deleted");
                category.delNoteItem(e);
            }
            helper.finish();
        }
        return handleCategoryRemove(category);
    }

    private void handleNoteLoad(Category c) {
        if (!c.getNoteItems().isEmpty()) return;

        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(this)) {
            dataBaseHelper.start();
            dataBaseHelper.queryNote(-1, c.getId(), c.getNoteItems());
            dataBaseHelper.finish();
            c.setInitialized(true);
        }
    }

    private int handleNoteCreate(Category c, NoteItem newItem) {
        newItem.setCategoryId(c.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            newItem.setId(dbHelper.insertNote(newItem));
            dbHelper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new item to database!");
            return -1;
        }

        if (!c.isInitialized()) {
            Log.i(this.toString(), "category not initialized, not add new item in the list");
            return -1;
        }
        return c.addNoteItem(newItem);
    }

    private int handleNoteRemove(Category c, NoteItem e) {
        int index = c.indexNoteItem(e);
        if (index == -1) return -1;

        try (DataBaseHelper dbHelper = new DataBaseHelper(this)){
            dbHelper.start();
            if (0 == dbHelper.deleteNote(e.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            c.delNoteItem(e);
            return index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove item with id " +
                    e.getId() + ", exception: " + e);
            return -1;
        }
    }

    private int handleNoteUpdate(Category c, NoteItem newItem) {
        NoteItem oldItem = c.findNoteItem(newItem.getId());
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item with id "+ newItem.getId());
            return -1;
        }
        Log.e(this.toString(), "newItem == oldItem: " + newItem.equals(oldItem));
        int index = c.indexNoteItem(oldItem);
        newItem.setId(oldItem.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            if (0 == dbHelper.updateNote(newItem)) {
                Log.i(this.toString(), "no item was updated");
                return -1;
            }
            dbHelper.finish();
            oldItem.update(newItem, true);
            return index;
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            return -1;
        }
    }



    public static class Task {
        public enum Type {
            CREATE,
            REMOVE,
            UPDATE,
            READ,
        }
        private static final Type[] taskTypes = Type.values();
        public enum Target {
            Category,
            NoteItem
        }
        private static final Target[] taskTargets = Target.values();
        Target target;
        Type type;
        Object object;
    }

    public void handleCategoryTask(Task.Type task_type, Category category) {
        new Thread(() -> {
            Message msg = new Message();
            msg.what = Task.Target.Category.ordinal();
            msg.obj = category;
            msg.arg2 = task_type.ordinal();
            switch (task_type) {
                case CREATE:
                    msg.arg1 = handleCategoryCreate(category);
                    break;
                case UPDATE:
                    msg.arg1 = handleCategoryUpdate(category);
                    break;
                case REMOVE:
                    msg.arg1 = handleCategoryRemove(category);
                    break;
                case READ:
                    handleNoteLoad(category);
                default:
                    return;
            }
            Log.e(this.toString(), "handle msg: " + msg);
            handler.sendMessage(msg);
        }).start();
    }

    public void handleNoteTask(Task.Type task_type, NoteItem e) {
        Category c = Global.getInstance().findCategory(e.getCategoryId());
        if (c == null) {
            return;
        }
        new Thread(() -> {
            Message msg = new Message();
            msg.what = Task.Target.NoteItem.ordinal();
            msg.obj = e;
            msg.arg2 = task_type.ordinal();
            switch (task_type) {
                case CREATE:
                    msg.arg1 = handleNoteCreate(c, e);
                    break;
                case REMOVE:
                    msg.arg1 = handleNoteRemove(c, e);
                    break;
                case UPDATE:
                    msg.arg1 = handleNoteUpdate(c, e);
                    break;
                case READ:
                    Log.i(this.toString(), "This method is not supported for NoteItem");
                default:
                    return;
            }
            Log.e(this.toString(), "handle msg: " + msg);
            handler.sendMessage(msg);
        }).start();
    }


    private final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<>();
    private final Semaphore taskSemaphore = new Semaphore(0);

    private Thread taskRunnerThread = null;
    private boolean canTaskRunnerThreadRun = false;
    class TaskRunner implements Runnable{
        @Override
        public void run() {
            while (canTaskRunnerThreadRun) {
                try {
                    taskSemaphore.acquire();
                    Task task = tasks.poll();
                    if (task != null) {
                        if (task.target == Task.Target.Category)
                            handleCategoryTask(task.type, (Category) task.object);
                        else if (task.target == Task.Target.NoteItem)
                            handleNoteTask(task.type, (NoteItem) task.object);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void startTaskRunnerThread() {
        taskRunnerThread = new Thread(new TaskRunner());
        canTaskRunnerThreadRun = true;
        taskRunnerThread.start();
    }

    private void tryStopTaskRunnerThread() {
        canTaskRunnerThreadRun = false;
        taskRunnerThread = null;
    }

    public void queueTask(Task task) {
        tasks.offer(task);
        taskSemaphore.release();
    }

    public void queueTask(Task.Type taskType, NoteItem item) {
        Task task = new Task();
        task.object = item;
        task.target = Task.Target.NoteItem;
        task.type = taskType;
        queueTask(task);
    }

    public void queueTask(Task.Type taskType, Category category) {
        Task task = new Task();
        task.object = category;
        task.target = Task.Target.Category;
        task.type = taskType;
        queueTask(task);
    }

}