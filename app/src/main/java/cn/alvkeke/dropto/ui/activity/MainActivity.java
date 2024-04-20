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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment;
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment;
import cn.alvkeke.dropto.ui.fragment.ImageViewerFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener;

public class MainActivity extends AppCompatActivity implements
        NoteDetailFragment.NoteEventListener, CategoryDetailFragment.CategoryDetailEvent,
        CoreService.TaskResultListener {

    private CoreService.CoreSrvBinder binder = null;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (CoreService.CoreSrvBinder) iBinder;
            binder.getService().addTaskListener(MainActivity.this);
            binder.getService().queueReadTask(CoreService.Task.Target.Category, 0);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (binder == null) return;
            binder.getService().delTaskListener(MainActivity.this);
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
    private ImageViewerFragment imageViewerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setupCoreService();

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
                } else if (f instanceof ImageViewerFragment) {
                    imageViewerFragment = (ImageViewerFragment) f;
                    recoverImageViewFragment(savedInstanceState);
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
            startFragment(categoryListFragment);
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
    private String savedImageViewFile = null;
    private static final String SAVED_IMAGE_VIEW_FILE = "SAVED_IMAGE_VIEW_FILE";
    private void recoverImageViewFragment(Bundle state) {
        savedImageViewFile = state.getString(SAVED_IMAGE_VIEW_FILE);
        if (savedImageViewFile == null) return;
        imageViewerFragment.setImgFile(new File(savedImageViewFile));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(SAVED_NOTE_LIST_CATEGORY_ID, savedNoteListCategoryId);
        outState.putLong(SAVED_NOTE_INFO_NOTE_ID, savedNoteInfoNoteId);
        outState.putLong(SAVED_CATEGORY_DETAIL_ID, savedCategoryDetailId);
        outState.putString(SAVED_IMAGE_VIEW_FILE, savedImageViewFile);
    }

    private final LinkedList<Fragment> currentFragments = new LinkedList<>();
    private void startFragment(Fragment fragment) {
        currentFragments.push(fragment);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.main_container, fragment, null)
                .addToBackStack(null)
                .commit();
    }
    private void startFragment(Fragment fragment, int anim_in, int anim_out) {
        currentFragments.push(fragment);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(anim_in, anim_out)
                .add(R.id.main_container, fragment, null)
                .addToBackStack(null)
                .commit();
    }
    private Fragment popFragment() {
        Fragment fragment;
        while ((fragment=currentFragments.pop()) != null) {
            if (!fragment.isVisible())
                continue;
            return fragment;
        }
        return null;
    }
    class OnFragmentBackPressed extends OnBackPressedCallback {
        public OnFragmentBackPressed(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            Fragment fragment = popFragment();
            if (fragment instanceof NoteListFragment) {
                savedNoteListCategoryId = SAVED_NOTE_LIST_CATEGORY_ID_NONE;
            }
            boolean ret = false;
            if (fragment instanceof FragmentOnBackListener) {
                ret = ((FragmentOnBackListener) fragment).onBackPressed();
            }
            if (!ret) {
                MainActivity.this.finish();
            }
        }
    }

    public void handleCategoryExpand(Category category) {
        if (noteListFragment == null) {
            noteListFragment = new NoteListFragment();
            noteListFragment.setListener(new NoteListAttemptListener());
        }
        noteListFragment.setCategory(category);
        binder.getService().queueTask(CoreService.Task.Type.READ, category);
        savedNoteListCategoryId = category.getId();
        startFragment(noteListFragment, R.anim.slide_in, R.anim.slide_out);
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
                Log.e(this.toString(), "NoteItems are not removed currently");
                // TODO: implement this
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

    private Uri getUriForFile(File file) {
        return FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
    }

    private static void emptyFolder(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isDirectory()) {
                boolean ret = file.delete();
                Log.d("emptyFolder", "file delete result: " + ret);
            }
        }
    }
    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        in.close();
        out.close();

    }

    private File share_folder = null;
    private File generateFileWithRealName(NoteItem item, boolean needClean) {
        if (share_folder == null) {
            share_folder = this.getExternalFilesDir("share");
            assert share_folder != null;
        }
        if (share_folder.mkdir()) Log.i(this.toString(), "share folder created");
        if (needClean) emptyFolder(share_folder);

        try {
            String imageName = item.getImageName();
            File fileToShare;
            if (imageName == null) {
                fileToShare = item.getImageFile();
            } else {
                fileToShare = new File(share_folder, imageName);
                copyFile(item.getImageFile(), fileToShare);
            }
            return fileToShare;
        } catch (IOException e) {
            Log.e(this.toString(), "Failed to copy file: " + e);
            return null;
        }
    }

    private ArrayList<File> generateFilesWithRealName(ArrayList<NoteItem> noteItems) {
        ArrayList<File> files = new ArrayList<>();
        for (NoteItem e : noteItems) {
            if (e.getImageFile() == null) continue;
            File f = generateFileWithRealName(e, false);
            if (f == null) { continue; }
            files.add(f);
        }

        return files;
    }

    private boolean handleNoteShareMultiple(ArrayList<NoteItem> items) {
        ArrayList<File> files = generateFilesWithRealName(items);
        if (files.isEmpty()) return false;
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : files) {
            Uri uri = getUriForFile(f);
            uris.add(uri);
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        sendIntent.setType("image/*");
        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivity(Intent.createChooser(sendIntent, "Share multiple images"));
        return true;
    }

    private boolean handleNoteShare(NoteItem item) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);

        // add item text for sharing
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, item.getText());
        Log.d(this.toString(), "no image, share text: " + item.getText());

        if (item.getImageFile() != null) {
            // add item image for sharing if exist
            sendIntent.setType("image/*");
            File file2Share = generateFileWithRealName(item, true);
            Uri fileUri = getUriForFile(file2Share);
            sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(this.toString(), "share image Uri: " + fileUri);
        }
        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");

        try {
            this.startActivity(shareIntent);
            return true;
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
            return false;
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

    private void handleNoteImageShow(NoteItem item) {
        if (imageViewerFragment == null) {
            imageViewerFragment = new ImageViewerFragment();
        }
        savedImageViewFile = item.getImageFile().getAbsolutePath();
        imageViewerFragment.setImgFile(item.getImageFile());
        startFragment(imageViewerFragment, R.anim.slide_in_bottom, 0);
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
                case SHOW_IMAGE:
                    handleNoteImageShow(e);
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
                    boolean ret;
                    if (noteItems.size() == 1)
                        ret = handleNoteShare(noteItems.get(0));
                    else
                        ret = handleNoteShareMultiple(noteItems);
                    if (ret) break; // success, return
                    Toast.makeText(MainActivity.this,
                            "only images are supported to share", Toast.LENGTH_SHORT).show();
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

    @Override
    public void onCategoryTaskFinish(CoreService.Task.Type taskType, int index, Category c) {
        if (index < 0) return;
        if (categoryListFragment == null) return;
        categoryListFragment.notifyItemListChanged(CoreService.taskToNotify(taskType), index, c);
    }

    @Override
    public void onNoteTaskFinish(CoreService.Task.Type taskType, int index, NoteItem n) {
        if (index < 0) return;
        if (noteListFragment == null) return;
        noteListFragment.notifyItemListChanged(CoreService.taskToNotify(taskType), index, n);
    }

}