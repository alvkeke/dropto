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
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class CoreService extends Service {

    public CoreService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
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

    private void initServiceData() {
        Log.e(this.toString(), "initCategoryData: " + Global.getInstance());
        Global global = Global.getInstance();

        File img_folder = this.getExternalFilesDir("imgs");
        assert img_folder != null;
        if (!img_folder.exists()) {
            Log.i(this.toString(), "image folder not exist, create: " + img_folder.mkdir());
        }
        global.setFileStoreFolder(img_folder);

        // TODO: for debug only, remember to remove.
        if (BuildConfig.DEBUG) {
            DebugFunction.fill_database_for_category(this);
            List<File> img_files = DebugFunction.try_extract_res_images(this, img_folder);
            DebugFunction.fill_database_for_note(this, img_files, 1);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(this.toString(), "CoreService onCreate");
        startForeground();
        initServiceData();
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

    @SuppressWarnings("unused")
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
                default:
                    return;
            }
            handler.sendMessage(msg);
        }).start();
    }

    public void handleCategoryReadTask() {
        new Thread(() -> {
            ArrayList<Category> categories = Global.getInstance().getCategories();
            DataBaseHelper.IterateCallback<Category> cb = getCategoryIterateCallback(categories);
            // retrieve all categories from database always, since they will not take up
            // too many memory
            try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
                dbHelper.start();
                dbHelper.queryCategory(-1, categories, cb);
                dbHelper.finish();
            } catch (Exception e) {
                Log.e(this.toString(), "failed to retrieve data from database:" + e);
            }

        }).start();
    }

    @Nullable
    private DataBaseHelper.IterateCallback<Category> getCategoryIterateCallback(ArrayList<Category> categories) {
        if (listener == null) return null;
        return category -> {
            int index = categories.indexOf(category);
            Message msg = new Message();
            msg.what = Task.Target.Category.ordinal();
            msg.arg2 = Task.Type.READ.ordinal();
            msg.arg1 = index;
            msg.obj = category;
            handler.sendMessage(msg);
        };
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
                default:
                    return;
            }
            handler.sendMessage(msg);
        }).start();
    }

    public void handleNoteReadTask(long categoryId) {
        new Thread(() -> {
            Category c = Global.getInstance().findCategory(categoryId);
            if (c == null) return;
            if (!c.getNoteItems().isEmpty()) return;
            DataBaseHelper.IterateCallback<NoteItem> cb = getNoteIterateCallback(c);
            try (DataBaseHelper dataBaseHelper = new DataBaseHelper(this)) {
                dataBaseHelper.start();
                dataBaseHelper.queryNote(-1, c.getId(), c.getNoteItems(), cb);
                dataBaseHelper.finish();
                c.setInitialized(true);
            }
        }).start();
    }

    private DataBaseHelper.IterateCallback<NoteItem> getNoteIterateCallback(Category category) {
        if (listener == null) return null;
        return note -> {
            int index = category.indexNoteItem(note);
            Message msg = new Message();
            msg.what = Task.Target.NoteItem.ordinal();
            msg.arg2 = Task.Type.READ.ordinal();
            msg.arg1 = index;
            msg.obj = note;
            handler.sendMessage(msg);
        };
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
                    if (task == null)
                        continue;
                    if (task.type == Task.Type.READ) {
                        if (task.target == Task.Target.Category)
                            handleCategoryReadTask();
                        else if (task.target == Task.Target.NoteItem)
                            handleNoteReadTask((Long) task.object);
                    } else {
                        if (task.target == Task.Target.Category)
                            handleCategoryTask(task.type, (Category) task.object);
                        else if (task.target == Task.Target.NoteItem)
                            handleNoteTask(task.type, (NoteItem) task.object);
                    }
                } catch (InterruptedException ignored) { }
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
        taskSemaphore.release();    // release semaphore to exit waiting
        taskRunnerThread = null;
    }

    public void queueTask(Task task) {
        tasks.offer(task);
        taskSemaphore.release();
    }

    public void queueTask(Task.Type taskType, NoteItem item) {
        if (taskType == Task.Type.READ) return;
        Task task = new Task();
        task.object = item;
        task.target = Task.Target.NoteItem;
        task.type = taskType;
        queueTask(task);
    }

    public void queueTask(Task.Type taskType, Category category) {
        Task task = new Task();
        task.type = taskType;
        if (taskType == Task.Type.READ) {
            task.target = Task.Target.NoteItem;
            task.object = category.getId();
        } else {
            task.object = category;
            task.target = Task.Target.Category;
        }
        queueTask(task);
    }

    public void queueReadTask(Task.Target target, long id) {
        Task task = new Task();
        task.target = target;
        task.type = Task.Type.READ;
        task.object = id;
        queueTask(task);
    }

}