package cn.alvkeke.dropto.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class CoreService extends Service {

    public CoreService() {
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        return binder;
    }

    private final IBinder binder = new IBinder();
    public class IBinder extends Binder {
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(this.toString(), "CoreService onDestroy");
        stopForeground(true);
    }

    public interface TaskResultListener {
        int TYPE_CATEGORY = 0;
        int TYPE_NOTE = 1;
        int CREATED = 0;
        int REMOVED = 1;
        int UPDATED = 2;

        /**
         * to indicate that task finished
         * @param type data type: TYPE_CATEGORY, TYPE_NOTE
         * @param operation CREATED, REMOVED, UPDATED
         * @param index index of data item
         * @param object object of data item, determine by `type`
         */
        void onTaskFinish(int type, int operation, int index, Object object);
    }

    private TaskResultListener listener;

    public void setListener(TaskResultListener listener) {
        this.listener = listener;
    }

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

    public final static int TASK_CREATE = 0;
    public final static int TASK_REMOVE = 1;
    public final static int TASK_UPDATE = 2;
    public final static int TASK_READ = 3;

    public void triggerCategoryTask(int task_type, Category category) {
        new Thread(() -> {
            int index;
            int op;
            switch (task_type) {
                case TASK_CREATE:
                    index = handleCategoryCreate(category);
                    op = TaskResultListener.CREATED;
                    break;
                case TASK_UPDATE:
                    index = handleCategoryUpdate(category);
                    op = TaskResultListener.UPDATED;
                    break;
                case TASK_REMOVE:
                    index = handleCategoryRemove(category);
                    op = TaskResultListener.REMOVED;
                    break;
                case TASK_READ:
                    handleNoteLoad(category);
                default:
                    return;
            }
            if (listener == null) return;
            listener.onTaskFinish(TaskResultListener.TYPE_CATEGORY, op, index, category);
        }).start();
    }

    private Category findCategoryById(long id) {
        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (Category c : categories) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    public void triggerNoteTask(int task_type, NoteItem e) {
        Category c = findCategoryById(e.getCategoryId());
        if (c == null) {
            return;
        }
        new Thread(() -> {
            int index;
            int op;
            switch (task_type) {
                case TASK_CREATE:
                    index = handleNoteCreate(c, e);
                    op = TaskResultListener.CREATED;
                    break;
                case TASK_REMOVE:
                    index = handleNoteRemove(c, e);
                    op = TaskResultListener.REMOVED;
                    break;
                case TASK_UPDATE:
                    index = handleNoteUpdate(c, e);
                    op = TaskResultListener.UPDATED;
                    break;
                case TASK_READ:
                    Log.i(this.toString(), "This method is not supported for NoteItem");
                default:
                    return;
            }
            if (listener == null) return;
            listener.onTaskFinish(TaskResultListener.TYPE_NOTE, op, index, e);
        }).start();
    }

}