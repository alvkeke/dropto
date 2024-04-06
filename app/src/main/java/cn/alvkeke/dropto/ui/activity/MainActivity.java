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
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

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

public class MainActivity extends AppCompatActivity implements
        NoteDetailFragment.NoteEventListener, CategoryDetailFragment.CategoryDetailEvent,
        CoreService.TaskResultListener {

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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setupCoreService();
        initCategoryList();

        getOnBackPressedDispatcher().
                addCallback(this, new OnFragmentBackPressed(true));

        if (savedInstanceState != null) {
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            for (Fragment f : fragments) {
                if (f instanceof CategoryListFragment) {
                    categoryListFragment = (CategoryListFragment) f;
                } else if (f instanceof NoteListFragment) {
                    noteListFragment = (NoteListFragment) f;
                    recoverNoteListFragment(savedInstanceState);
                } else if (f instanceof NoteDetailFragment) {
                    recoverNoteDetailFragment((NoteDetailFragment) f, savedInstanceState);
                } else if (f instanceof CategoryDetailFragment) {
                    recoverCategoryDetailFragment((CategoryDetailFragment) f, savedInstanceState);
                } else {
                    Log.e(this.toString(), "unknown fragment: " + f);
                }
            }
        }

        if (categoryListFragment == null) {
            categoryListFragment = new CategoryListFragment();
        }
        categoryListFragment.setListener(new CategoryListAttemptListener());
        categoryListFragment.setCategories(Global.getInstance().getCategories());
        if (!categoryListFragment.isAdded()) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.main_container, categoryListFragment, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private static final long SAVED_NOTE_LIST_CATEGORY_ID_NONE = -1;
    private long savedNoteListCategoryId = SAVED_NOTE_LIST_CATEGORY_ID_NONE;
    private static final String SAVED_NOTE_LIST_CATEGORY_ID = "SAVED_NOTE_LIST_CATEGORY_ID";
    private void recoverNoteListFragment(Bundle state) {
        savedNoteListCategoryId = state.getLong(SAVED_NOTE_LIST_CATEGORY_ID, SAVED_NOTE_LIST_CATEGORY_ID_NONE);
        if (savedNoteListCategoryId == SAVED_NOTE_LIST_CATEGORY_ID_NONE) return;
        Category category = Global.getInstance().findCategory(savedNoteListCategoryId);
        noteListFragment.setListener(new NoteListAttemptListener());
        noteListFragment.setCategory(category);
    }
    private static final long SAVED_NOTE_INFO_NOTE_ID_NONE = -1;
    private long savedNoteInfoNoteId = SAVED_NOTE_INFO_NOTE_ID_NONE;
    private static final String SAVED_NOTE_INFO_NOTE_ID = "SAVED_NOTE_INFO_NOTE_ID";
    private void recoverNoteDetailFragment(NoteDetailFragment fragment, Bundle state) {
        if (savedNoteListCategoryId == SAVED_NOTE_LIST_CATEGORY_ID_NONE) return;
        savedNoteInfoNoteId = state.getLong(SAVED_NOTE_INFO_NOTE_ID, SAVED_NOTE_INFO_NOTE_ID_NONE);
        Category category = Global.getInstance().findCategory(savedNoteListCategoryId);
        NoteItem item = category.findNoteItem(savedNoteInfoNoteId);
        if (item == null) { fragment.dismiss(); return; }
        fragment.setNoteItem(item);
    }
    private static final long SAVED_CATEGORY_DETAIL_ID_NONE = -1;
    private long savedCategoryDetailId = SAVED_CATEGORY_DETAIL_ID_NONE;
    private static final String SAVED_CATEGORY_DETAIL_ID = "SAVED_CATEGORY_DETAIL_ID";
    private void recoverCategoryDetailFragment(CategoryDetailFragment fragment, Bundle state) {
        savedCategoryDetailId = state.getLong(SAVED_CATEGORY_DETAIL_ID, SAVED_CATEGORY_DETAIL_ID_NONE);
        if (savedCategoryDetailId == SAVED_CATEGORY_DETAIL_ID_NONE) return;
        Category category = Global.getInstance().findCategory(savedCategoryDetailId);
        fragment.setCategory(category);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SAVED_NOTE_LIST_CATEGORY_ID, savedNoteListCategoryId);
        outState.putLong(SAVED_NOTE_INFO_NOTE_ID, savedNoteInfoNoteId);
        outState.putLong(SAVED_CATEGORY_DETAIL_ID, savedCategoryDetailId);
    }

    class OnFragmentBackPressed extends OnBackPressedCallback {
        public OnFragmentBackPressed(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            if (noteListFragment.isVisible()) {
                savedNoteListCategoryId = SAVED_NOTE_LIST_CATEGORY_ID_NONE;
                noteListFragment.finish();
//                getSupportFragmentManager().beginTransaction()
//                        .setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
//                        .remove(noteListFragment)
//                        .commit();
            } else if (categoryListFragment.isVisible()) {
                MainActivity.this.finish();
            }
        }
    }

    public void handleCategoryExpand(Category category) {
        binder.getService().queueTask(CoreService.Task.Type.READ, category);
        if (noteListFragment == null) {
            noteListFragment = new NoteListFragment();
            noteListFragment.setListener(new NoteListAttemptListener());
        }
        noteListFragment.setCategory(category);
        savedNoteListCategoryId = category.getId();
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
                .add(R.id.main_container, noteListFragment, null)
                .addToBackStack(null)
                .commit();
    }

    public void showCategoryCreatingDialog() {
        savedCategoryDetailId = SAVED_CATEGORY_DETAIL_ID_NONE;
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(null), null)
                .commit();
    }

    @Override
    public void onCategoryDetailFinish(CategoryDetailFragment.Result result, Category category) {
        switch (result) {
            case CREATE:
                binder.getService().queueTask(CoreService.Task.Type.CREATE, category);
                break;
            case DELETE:
                binder.getService().queueTask(CoreService.Task.Type.REMOVE, category);
                break;
            case FULL_DELETE:
                binder.getService().queueTask(CoreService.Task.Type.REMOVE, category);
                break;
            case MODIFY:
                binder.getService().queueTask(CoreService.Task.Type.UPDATE, category);
                break;
            default:
                Log.d(this.toString(), "other result: " + result);
        }
    }

    private void handleCategoryDetailShow(Category c) {
        savedCategoryDetailId = c.getId();
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

    private void handleTextCopy(String text) {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Log.e(this.toString(), "Failed to get ClipboardManager");
            return;
        }
        ClipData data = ClipData.newPlainText("text", text);
        clipboardManager.setPrimaryClip(data);
    }
    private void handleNoteCopy(NoteItem e) {
        handleTextCopy(e.getText());
    }

    private void handleNoteDetailShow(NoteItem item) {
        savedNoteInfoNoteId = item.getId();
        getSupportFragmentManager().beginTransaction()
                .add(new NoteDetailFragment(item), null)
                .commit();
    }

    class NoteListAttemptListener implements NoteListFragment.AttemptListener {
        @Override
        public void onAttempt(Attempt attempt, NoteItem e) {
            switch (attempt) {
                case REMOVE:
                    binder.getService().queueTask(CoreService.Task.Type.REMOVE, e);
                    break;
                case CREATE:
                    binder.getService().queueTask(CoreService.Task.Type.CREATE, e);
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
                    binder.getService().queueTask(CoreService.Task.Type.UPDATE, e);
                    break;
            }
        }

        private CoreService.Task.Type convertAttemptToType(Attempt attempt) {
            switch (attempt) {
                case REMOVE:
                    return CoreService.Task.Type.REMOVE;
                case UPDATE:
                    return CoreService.Task.Type.UPDATE;
                case CREATE:
                    return CoreService.Task.Type.CREATE;
            }
            return null;
        }
        @Override
        public void onAttemptBatch(Attempt attempt, ArrayList<NoteItem> noteItems) {
            switch (attempt) {
                case REMOVE:
                case CREATE:
                case UPDATE:
                    CoreService.Task.Type type = convertAttemptToType(attempt);
                    for (NoteItem e : noteItems) {
                        binder.getService().queueTask(type, e);
                    }
                    break;
                case COPY:
                    StringBuilder sb = new StringBuilder();
                    NoteItem listOne = noteItems.get(noteItems.size()-1);
                    for (NoteItem e : noteItems) {
                        sb.append(e.getText());
                        if (e == listOne) continue;
                        sb.append("\n");
                    }
                    handleTextCopy(sb.toString());
                    break;
                case SHARE:
                    // TODO: implement this
                    Toast.makeText(MainActivity.this,
                            "multi share is not supported yet", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    Log.e(this.toString(), "This operation is not support batch: " +attempt);
            }
        }

        @Override
        public void onError(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onNoteDetailFinish(NoteDetailFragment.Result result, NoteItem item) {
        switch (result) {
            case CREATE:
                binder.getService().queueTask(CoreService.Task.Type.CREATE, item);
                break;
            case UPDATE:
                binder.getService().queueTask(CoreService.Task.Type.UPDATE, item);
                break;
            case REMOVE:
                binder.getService().queueTask(CoreService.Task.Type.REMOVE, item);
                break;
        }
    }

    private final static ListNotification.Notify[] notifies = {
            ListNotification.Notify.CREATED,
            ListNotification.Notify.REMOVED,
            ListNotification.Notify.UPDATED,
    };
    @Override
    public void onCategoryTaskFinish(CoreService.Task.Type taskType, int index, Category c) {
        if (index < 0) return;
        if (categoryListFragment == null) return;
        categoryListFragment.notifyItemListChanged(notifies[taskType.ordinal()], index, c);
    }

    @Override
    public void onNoteTaskFinish(CoreService.Task.Type taskType, int index, NoteItem n) {
        if (index < 0) return;
        if (noteListFragment == null) return;
        noteListFragment.notifyItemListChanged(notifies[taskType.ordinal()], index, n);
    }

}