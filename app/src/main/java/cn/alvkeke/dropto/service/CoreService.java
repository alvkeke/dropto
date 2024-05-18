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
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.mgmt.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.storage.DataLoader;

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
        Global global = Global.getInstance();

        File img_folder = global.getFolderImage(this);

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

    private final ArrayList<Task.ResultListener> listeners = new ArrayList<>();

    public void addTaskListener(Task.ResultListener listener) {
        this.listeners.add(listener);
    }

    public void delTaskListener(Task.ResultListener listener) {
        this.listeners.remove(listener);
    }

    private interface TaskListenerIterator {
        void onIterate(Task.ResultListener listener);
    }

    private void iterateTaskListener(TaskListenerIterator iterator) {
        for (Task.ResultListener listener : listeners) {
            iterator.onIterate(listener);
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private void notifyListener(Task task) {
        handler.post(() ->
                iterateTaskListener(listener -> listener.onTaskFinish(task, null)));
    }

    private void handleTaskCategoryCreate(Task task) {
        Category category = (Category) task.param;

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            category.setId(helper.insertCategory(category));
            helper.finish();
            ArrayList<Category> categories = DataLoader.getInstance().getCategories(this);
            categories.add(category);
            task.result = categories.indexOf(category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new category to database");
            task.result = -1;
        }
        notifyListener(task);
    }

    private void handleTaskCategoryRemove(Task task) {
        ArrayList<Category> categories = DataLoader.getInstance().getCategories(this);
        Category category = (Category) task.param;

        int index;
        if ((index = categories.indexOf(category)) == -1) {
            Log.i(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            helper.deleteNotes(category.getId());
            if (0 == helper.deleteCategory(category.getId()))
                Log.e(this.toString(), "no category row be deleted in database");
            helper.finish();
            categories.remove(category);
            task.result = index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove category with id " +
                    category.getId() + ", exception: " + ex);
            task.result = -1;
        }
        notifyListener(task);
    }

    private void handleTaskCategoryUpdate(Task task) {
        ArrayList<Category> categories = DataLoader.getInstance().getCategories(this);
        Category category = (Category) task.param;

        int index;
        if (-1 == (index = categories.indexOf(category))) {
            Log.e(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.updateCategory(category)) {
                Log.e(this.toString(), "no category row be updated");
            }
            helper.finish();
            task.result = index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to modify Category: " + ex);
            task.result = -1;
        }
        notifyListener(task);
    }

    private void handleTaskNoteCreate(Task task) {
        NoteItem newItem = (NoteItem) task.param;
        Category category = DataLoader.getInstance().findCategory(this, newItem.getCategoryId());

        newItem.setCategoryId(category.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            newItem.setId(dbHelper.insertNote(newItem));
            dbHelper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new item to database!");
            task.result = -1;
            notifyListener(task);
            return;
        }

        if (!category.isInitialized()) {
            Log.i(this.toString(), "category not initialized, not add new item in the list");
            task.result = -1;
            notifyListener(task);
            return;
        }
        task.result = category.addNoteItem(newItem);
        notifyListener(task);
    }

    private void handleTaskNoteRemove(Task task) {
        NoteItem e = (NoteItem) task.param;
        Category c = DataLoader.getInstance().findCategory(this, e.getCategoryId());

        int index = c.indexNoteItem(e);
        if (index == -1) return;

        try (DataBaseHelper dbHelper = new DataBaseHelper(this)){
            dbHelper.start();
            if (0 == dbHelper.deleteNote(e.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            c.delNoteItem(e);
            task.result = index;
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove item with id " +
                    e.getId() + ", exception: " + e);
            task.result = -1;
        }
        notifyListener(task);
    }

    private void handleTaskNoteUpdate(Task task) {
        NoteItem newItem = (NoteItem) task.param;
        Category c = DataLoader.getInstance().findCategory(this, newItem.getCategoryId());
        NoteItem oldItem = c.findNoteItem(newItem.getId());
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item with id "+ newItem.getId());
            return;
        }
        int index = c.indexNoteItem(oldItem);
        newItem.setId(oldItem.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            if (0 == dbHelper.updateNote(newItem)) {
                Log.i(this.toString(), "no item was updated");
                task.result = -1;
            } else {
                dbHelper.finish();
                oldItem.update(newItem, true);
                task.result = index;
            }
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            task.result = -1;
        }
        notifyListener(task);
    }

    private void distributeTask(Task task) {
        if (task.type == Task.Type.Category) {
            switch (task.job) {
                case CREATE:
                    handleTaskCategoryCreate(task);
                    break;
                case REMOVE:
                    handleTaskCategoryRemove(task);
                    break;
                case UPDATE:
                    handleTaskCategoryUpdate(task);
                    break;
                default:
                    task.result = -1;
                    notifyListener(task);
                    break;
            }
        } else if (task.type == Task.Type.NoteItem) {
            switch (task.job) {
                case CREATE:
                    handleTaskNoteCreate(task);
                    break;
                case REMOVE:
                    handleTaskNoteRemove(task);
                    break;
                case UPDATE:
                    handleTaskNoteUpdate(task);
                    break;
                default:
                    task.result = -1;
                    notifyListener(task);
                    break;
            }
        }
    }

    private final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<>();
    private final Semaphore taskSemaphore = new Semaphore(0);

    private Thread taskRunnerThread = null;
    private boolean canTaskRunnerThreadRun = false;
    private final Runnable taskRunner = () -> {
        while (canTaskRunnerThreadRun) {
            try {
                taskSemaphore.acquire();
                Task task = tasks.poll();
                if (task == null)
                    continue;

                distributeTask(task);
            } catch (InterruptedException ignored) { }
        }
    };

    private void startTaskRunnerThread() {
        taskRunnerThread = new Thread(taskRunner);
        canTaskRunnerThreadRun = true;
        taskRunnerThread.start();
    }

    private void tryStopTaskRunnerThread() {
        canTaskRunnerThreadRun = false;
        taskSemaphore.release();    // release semaphore to exit waiting
        taskRunnerThread = null;
    }

    public void queueTask(Task task) {
        new Thread(() -> distributeTask(task)).start();
//        tasks.offer(task);
//        taskSemaphore.release();
    }

}