package cn.alvkeke.dropto.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import java.util.Random;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.ImageFile;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.mgmt.Global;
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.service.CoreServiceConnection;
import cn.alvkeke.dropto.service.Task;
import cn.alvkeke.dropto.storage.DataLoader;
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment;
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;
import cn.alvkeke.dropto.ui.fragment.ImageViewerFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;
import cn.alvkeke.dropto.ui.intf.CategoryAttemptListener;
import cn.alvkeke.dropto.ui.intf.ErrorMessageHandler;
import cn.alvkeke.dropto.ui.intf.FragmentOnBackListener;
import cn.alvkeke.dropto.ui.intf.NoteAttemptListener;

public class MainActivity extends AppCompatActivity implements
        ErrorMessageHandler, Task.ResultListener,
        NoteAttemptListener, CategoryAttemptListener,
        CategorySelectorFragment.CategorySelectListener {

    private CoreService service = null;
    private final CoreServiceConnection serviceConn = new CoreServiceConnection(this) {
        @Override
        public void execOnServiceConnected(ComponentName componentName, Bundle bundleAfterConnected) {
            service = getService();
        }

        @Override
        public void execOnServiceDisconnected() {
            service = null;
        }
    };

    private void setupCoreService(Bundle savedInstanceState) {
        Intent serviceIntent = new Intent(this, CoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        serviceConn.setBundleAfterConnected(savedInstanceState);
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
    }

    private void clearCoreService() {
        if (service == null) return;
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

        getOnBackPressedDispatcher().
                addCallback(this, new OnFragmentBackPressed(true));

        setupCoreService(savedInstanceState);

        ArrayList<Category> categories = DataLoader.getInstance().loadCategories(this);
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
                } else if (f instanceof CategorySelectorFragment) {
                     CategorySelectorFragment fragment = (CategorySelectorFragment) f;
                     fragment.setCategories(categories);
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
        categoryListFragment.setCategories(categories);
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
        Category category = DataLoader.getInstance().findCategory(savedNoteListCategoryId);
        noteListFragment.setCategory(category);
    }
    private static final long SAVED_NOTE_INFO_NOTE_ID_NONE = -1;
    private long savedNoteInfoNoteId = SAVED_NOTE_INFO_NOTE_ID_NONE;
    private static final String SAVED_NOTE_INFO_NOTE_ID = "SAVED_NOTE_INFO_NOTE_ID";
    private void recoverNoteDetailFragment(NoteDetailFragment fragment, Bundle state) {
        if (savedNoteListCategoryId == SAVED_NOTE_LIST_CATEGORY_ID_NONE) return;
        savedNoteInfoNoteId = state.getLong(SAVED_NOTE_INFO_NOTE_ID, SAVED_NOTE_INFO_NOTE_ID_NONE);
        Category category = DataLoader.getInstance().findCategory(savedNoteListCategoryId);
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
        Category category = DataLoader.getInstance().findCategory(savedCategoryDetailId);
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
    private void startFragmentAnime(Fragment fragment) {
        currentFragments.push(fragment);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in, R.anim.slide_out)
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
        }
        boolean ret = DataLoader.getInstance().loadCategoryNotes(this, category);
        if (!ret) {
            Log.e(this.toString(), "Failed to get noteList from database");
        }
        noteListFragment.setCategory(category);
        savedNoteListCategoryId = category.id;
        startFragmentAnime(noteListFragment);
    }

    public void showCategoryCreatingDialog() {
        savedCategoryDetailId = SAVED_CATEGORY_DETAIL_ID_NONE;
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(null), null)
                .commit();
    }

    private void handleCategoryDetailShow(Category c) {
        savedCategoryDetailId = c.id;
        CategoryDetailFragment fragment = new CategoryDetailFragment(c);
        getSupportFragmentManager().beginTransaction()
                .add(fragment, null)
                .commit();
    }

    private void addDebugData() {
        service.queueTask(Task.createCategory(new Category("Local(Debug)", Category.Type.LOCAL_CATEGORY), null));
        service.queueTask(Task.createCategory(new Category("REMOTE USERS", Category.Type.REMOTE_USERS), null));
        service.queueTask(Task.createCategory(new Category("REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV), null));

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Log.e(this.toString(), "failed to sleep: " + ex);
            }

            File img_folder = Global.getFolderImage(this);
            List<File> img_files = DebugFunction.tryExtractResImages(this, img_folder);
            if (img_files == null)
                return;
            ArrayList<Category> categories = DataLoader.getInstance().getCategories();
            if (categories.isEmpty())
                return;

            Random r = new Random();
            long cate_id = categories.get(r.nextInt(categories.size())).id;
            int idx = 0;
            for (int i = 0; i < 15; i++) {
                NoteItem e = new NoteItem("ITEM" + i + i, System.currentTimeMillis());
                e.categoryId = cate_id;
                if (r.nextBoolean()) {
                    e.setText(e.getText(), true);
                }
                if (idx < img_files.size() && r.nextBoolean()) {
                    File img_file = img_files.get(idx);
                    idx++;
                    if (img_file.exists()) {
                        ImageFile imageFile = ImageFile.from(img_file, img_file.getName());
                        e.addImageFile(imageFile);
                    }

                }
                service.queueTask(Task.createNote(e, null));
            }
        }).start();
    }

    @Override
    public void onAttempt(CategoryAttemptListener.Attempt attempt, Category category) {
        switch (attempt) {
            case CREATE:
                service.queueTask(Task.createCategory(category, null));
                break;
            case REMOVE:
                service.queueTask(Task.removeCategory(category, null));
                break;
            case UPDATE:
                service.queueTask(Task.updateCategory(category, null));
                break;
            case SHOW_DETAIL:
                handleCategoryDetailShow(category);
                break;
            case SHOW_CREATE:
                showCategoryCreatingDialog();
                break;
            case SHOW_EXPAND:
                handleCategoryExpand(category);
                break;
            case DEBUG_ADD_DATA:
                new AlertDialog.Builder(this)
                        .setTitle("Debug function")
                        .setMessage("Create Debug data?")
                        .setNegativeButton(R.string.string_cancel, null)
                        .setPositiveButton(R.string.string_ok, (dialog, i) -> addDebugData())
                        .create().show();
                break;
            default:
                Log.d(this.toString(), "other attempt: " + attempt);
        }
    }

    private Uri getUriForFile(File file) {
        return FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
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
    private void emptyShareFolder() {
        if (share_folder == null) return;
        File[] files = share_folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isDirectory()) {
                boolean ret = file.delete();
                Log.d("emptyFolder", "file delete result: " + ret);
            }
        }
    }
    private File generateShareFile(ImageFile imageFile) {
        share_folder = Global.getFolderImageShare(this);

        try {
            String imageName = imageFile.getName();
            File fileToShare;
            if (imageName.isEmpty()) {
                fileToShare = imageFile.md5file;
            } else {
                fileToShare = new File(share_folder, imageName);
                copyFile(imageFile.md5file, fileToShare);
            }
            return fileToShare;
        } catch (IOException e) {
            Log.e(this.toString(), "Failed to copy file: " + e);
            return null;
        }
    }

    private void generateShareFileAndUriForNote(NoteItem note, @NonNull ArrayList<Uri> uris) {
        note.iterateImages().forEachRemaining(f -> {
            File ff = generateShareFile(f);
            Uri uri = getUriForFile(ff);
            uris.add(uri);
        });
    }

    private void triggerShare(String text, ArrayList<Uri> uris) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        int count = uris.size();
        if (count == 0) {
            sendIntent.setType("text/plain");
        } else {
            sendIntent.setType("image/*");
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        if (count == 1) {
            sendIntent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }

        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        Log.d(this.toString(), "no image, share text: " + text);

        Intent shareIntent = Intent.createChooser(sendIntent, "Share to");
        try {
            this.startActivity(shareIntent);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to create share Intent: " + e);
        }
    }

    private void handleNoteShareMultiple(ArrayList<NoteItem> items) {
        emptyShareFolder();
        ArrayList<Uri> uris = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (NoteItem e : items) {
            generateShareFileAndUriForNote(e, uris);
            String text = e.getText();
            if (!text.isEmpty()) {
                sb.append(text);
                sb.append('\n');
            }
        }
        triggerShare(sb.toString(), uris);
    }

    private void handleNoteShare(NoteItem item) {
        emptyShareFolder();
        ArrayList<Uri> uris = new ArrayList<>();
        generateShareFileAndUriForNote(item, uris);
        triggerShare(item.getText(), uris);
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
        savedNoteInfoNoteId = item.id;
        NoteDetailFragment fragment = new NoteDetailFragment();
        fragment.setNoteItem(item);
        getSupportFragmentManager().beginTransaction()
                .add(fragment, null)
                .commit();
    }

    private void handleNoteImageShow(NoteItem item, int imageIndex) {
        if (imageViewerFragment == null) {
            imageViewerFragment = new ImageViewerFragment();
        }
        ImageFile imageFile = item.getImageAt(imageIndex);
        if (imageFile == null) {
            Log.e(this.toString(), "Failed to get image at index: " + imageIndex);
            return;
        }
        savedImageViewFile = imageFile.md5file.getAbsolutePath();
        imageViewerFragment.setImgFile(imageFile.md5file);
        imageViewerFragment.show(getSupportFragmentManager(), null);
    }

    private NoteItem pendingForwardNote;
    private void handleNoteForward(NoteItem note) {
        pendingForwardNote = note;
        CategorySelectorFragment forwardFragment = new CategorySelectorFragment();
        forwardFragment.setCategories(DataLoader.getInstance().getCategories());
        forwardFragment.show(getSupportFragmentManager(), null);
    }

    @Override
    public void onAttempt(NoteAttemptListener.Attempt attempt, NoteItem e) {
        onAttempt(attempt, e, null);
    }

    @Override
    public void onAttempt(NoteAttemptListener.Attempt attempt, NoteItem e, Object ext) {
        switch (attempt) {
            case REMOVE:
                service.queueTask(Task.removeNote(e, null));
                break;
            case CREATE:
                service.queueTask(Task.createNote(e, null));
                break;
            case SHOW_DETAIL:
                handleNoteDetailShow(e);
                break;
            case COPY:
                handleNoteCopy(e);
                break;
            case SHOW_SHARE:
                handleNoteShare(e);
                break;
            case UPDATE:
                service.queueTask(Task.updateNote(e, null));
                break;
            case SHOW_IMAGE:
                int imageIndex = (int) ext;
                handleNoteImageShow(e, imageIndex);
                break;
            case SHOW_FORWARD:
                handleNoteForward(e);
                break;
        }
    }

    private static Task.Job convertAttemptToJob(NoteAttemptListener.Attempt attempt) {
        switch (attempt) {
            case REMOVE:
                return Task.Job.REMOVE;
            case UPDATE:
                return Task.Job.UPDATE;
            case CREATE:
                return Task.Job.CREATE;
        }
        return null;
    }
    @Override
    public void onAttemptBatch(NoteAttemptListener.Attempt attempt, ArrayList<NoteItem> noteItems) {
        switch (attempt) {
            case REMOVE:
            case CREATE:
            case UPDATE:
                Task.Job job = convertAttemptToJob(attempt);
                for (NoteItem e : noteItems) {
                    service.queueTask(Task.onNoteStorage(job, e, null));
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
            case SHOW_SHARE:
                if (noteItems.size() == 1)
                    handleNoteShare(noteItems.get(0));
                else
                    handleNoteShareMultiple(noteItems);
                break;
            default:
                Log.e(this.toString(), "This operation is not support batch: " +attempt);
        }
    }

    @Override
    public void onSelected(int index, Category category) {
        if (pendingForwardNote == null)
            return;
        NoteItem item = pendingForwardNote.clone();
        item.categoryId = category.id;
        service.queueTask(Task.createNote(item, null));
    }

    @Override
    public void onError(String errMessage) {
        Toast.makeText(this, errMessage, Toast.LENGTH_SHORT).show();
    }

    private void onCategoryTaskFinish(Task task) {
        if (task.result < 0) return;
        if (categoryListFragment == null) return;
        switch (task.job) {
            case CREATE:
            case REMOVE:
            case UPDATE:
                categoryListFragment.notifyItemListChanged(
                        Task.jobToNotify(task.job), task.result, (Category) task.param);
                break;
        }
    }

    private void onNoteTaskFinish(Task task) {
        NoteItem n = (NoteItem) task.param;
        int index = task.result;
        if (n == pendingForwardNote)
            pendingForwardNote = null;
        if (index < 0) return;
        if (noteListFragment == null) return;
        noteListFragment.notifyItemListChanged(Task.jobToNotify(task.job), index, n);
    }

    @Override
    public void onTaskFinish(Task task, Object param) {
        switch (task.type) {
            case Category:
                onCategoryTaskFinish(task);
                return;
            case NoteItem:
                onNoteTaskFinish(task);
        }
    }
}