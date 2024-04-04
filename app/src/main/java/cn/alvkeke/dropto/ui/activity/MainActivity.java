package cn.alvkeke.dropto.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment;
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.intf.SysBarColorNotify;

public class MainActivity extends AppCompatActivity implements
        NoteDetailFragment.NoteEventListener, CategoryDetailFragment.CategoryDetailEvent,
        SysBarColorNotify, CoreService.TaskResultListener {

    private void initCategoryList() {
        Global global = Global.getInstance();
        ArrayList<Category> categories = global.getCategories();

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

        // retrieve all categories from database always, since they will not take up
        // too many memory
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            dbHelper.queryCategory(-1, categories);
            dbHelper.finish();
        } catch (Exception e) {
            Log.e(this.toString(), "failed to retrieve data from database:" + e);
        }
    }

    private CoreService.CoreSrvBinder binder = null;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (CoreService.CoreSrvBinder) iBinder;
            binder.getService().setListener(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (binder == null) return;
            binder.getService().setListener(null);
            binder = null;
        }
    };

    private void setupCoreService() {
        Intent serviceIntent = new Intent(this, CoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
    }

    private void clearCoreService() {
        if (binder == null) return;
        unbindService(serviceConn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearCoreService();
    }

    private CategoryListFragment categoryListFragment;
    private NoteListFragment noteListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupCoreService();
        initCategoryList();

        getOnBackPressedDispatcher().
                addCallback(this, new OnFragmentBackPressed(true));

        if (categoryListFragment == null) {
            categoryListFragment = new CategoryListFragment();
            categoryListFragment.setListener(new CategoryListAttemptListener());
        }
        categoryListFragment.setCategories(Global.getInstance().getCategories());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_container, categoryListFragment)
                .setReorderingAllowed(true)
                .addToBackStack("CategoryList")
                .commit();
    }

    @Override
    public void setStatusBarColor(int color) {
        getWindow().setStatusBarColor(color);
    }

    @Override
    public void setNavigationBarColor(int color) {
        getWindow().setNavigationBarColor(color);
    }

    class OnFragmentBackPressed extends OnBackPressedCallback {
        public OnFragmentBackPressed(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            FragmentManager manager = getSupportFragmentManager();
            if (manager.getBackStackEntryCount() <= 1) {
                MainActivity.this.finish();
                return;
            }
            getSupportFragmentManager().popBackStack();
        }
    }

    public void handleCategoryExpand(Category category) {
        binder.getService().triggerCategoryTask(CoreService.TaskType.READ, category);
        if (noteListFragment == null) {
            noteListFragment = new NoteListFragment();
            noteListFragment.setListener(new NoteListAttemptListener());
        }
        noteListFragment.setCategory(category);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_container, noteListFragment)
                .addToBackStack("NoteList")
                .commit();
    }

    public void showCategoryCreatingDialog() {
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(null), null)
                .commit();
    }

    @Override
    public void onCategoryDetailFinish(CategoryDetailFragment.Result result, Category category) {
        switch (result) {
            case CREATE:
                binder.getService().triggerCategoryTask(CoreService.TaskType.CREATE, category);
                break;
            case DELETE:
                binder.getService().triggerCategoryTask(CoreService.TaskType.REMOVE, category);
                break;
            case FULL_DELETE:
                binder.getService().triggerCategoryTask(CoreService.TaskType.REMOVE, category);
                break;
            case MODIFY:
                binder.getService().triggerCategoryTask(CoreService.TaskType.UPDATE, category);
                break;
            default:
                Log.d(this.toString(), "other result: " + result);
        }
    }

    private void handleCategoryDetailShow(Category c) {
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(c), null)
                .commit();
    }

    class CategoryListAttemptListener implements CategoryListFragment.AttemptListener {

        @Override
        public void onAttemptRecv(Attempt attempt, Category category) {
            switch (attempt) {
                case DETAIL:
                    handleCategoryDetailShow(category);
                    break;
                case CREATE:
                    showCategoryCreatingDialog();
                    break;
                case EXPAND:
                    handleCategoryExpand(category);
                    break;
            }
        }

        @Override
        public void onErrorRecv(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleNoteShare(NoteItem item) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);

        // add item text for sharing
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, item.getText());
        Log.d(this.toString(), "no image, share text: " + item.getText());

        if (item.getImageFile() != null) {
            // add item image for sharing if exist
            sendIntent.setType("image/*");
            Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", item.getImageFile());
            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(this.toString(), "share image Uri: " + fileUri);
        }
        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");

        try {
            this.startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
        }
    }

    private void handleNoteCopy(NoteItem e) {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Log.e(this.toString(), "Failed to get ClipboardManager");
            return;
        }
        ClipData data = ClipData.newPlainText("text", e.getText());
        clipboardManager.setPrimaryClip(data);
    }

    private void handleNoteDetailShow(NoteItem item) {
        getSupportFragmentManager().beginTransaction()
                .add(new NoteDetailFragment(item), null)
                .commit();
    }

    class NoteListAttemptListener implements NoteListFragment.AttemptListener {
        @Override
        public void onAttemptRecv(Attempt attempt, Category c, NoteItem e) {
            switch (attempt) {
                case REMOVE:
                    binder.getService().triggerNoteTask(CoreService.TaskType.REMOVE, e);
                    break;
                case CREATE:
                    binder.getService().triggerNoteTask(CoreService.TaskType.CREATE, e);
                    break;
                case DETAIL:
                    handleNoteDetailShow(e);
                    break;
                case COPY:
                    handleNoteCopy(e);
                    break;
                case SHARE:
                    handleNoteShare(e);
                    break;
                case UPDATE:
                    binder.getService().triggerNoteTask(CoreService.TaskType.UPDATE, e);
                    break;
            }
        }

        @Override
        public void onErrorRecv(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNoteDetailFinish(NoteDetailFragment.Result result, NoteItem item) {
        switch (result) {
            case CREATE:
                binder.getService().triggerNoteTask(CoreService.TaskType.CREATE, item);
                break;
            case UPDATE:
                binder.getService().triggerNoteTask(CoreService.TaskType.UPDATE, item);
                break;
            case REMOVE:
                binder.getService().triggerNoteTask(CoreService.TaskType.REMOVE, item);
                break;
        }
    }

    private final static ListNotification.Notify[] notifies = {
            ListNotification.Notify.CREATED,
            ListNotification.Notify.REMOVED,
            ListNotification.Notify.UPDATED,
    };
    @Override
    public void onCategoryTaskFinish(CoreService.TaskType taskType, int index, Category c) {
        if (index < 0) return;
        if (categoryListFragment == null) return;
        categoryListFragment.notifyItemListChanged(notifies[taskType.ordinal()], index, c);
    }

    @Override
    public void onNoteTaskFinish(CoreService.TaskType taskType, int index, NoteItem n) {
        if (index < 0) return;
        if (noteListFragment == null) return;
        noteListFragment.notifyItemListChanged(notifies[taskType.ordinal()], index, n);
    }

}