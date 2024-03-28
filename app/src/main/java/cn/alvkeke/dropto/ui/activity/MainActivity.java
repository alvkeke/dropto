package cn.alvkeke.dropto.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.adapter.MainFragmentAdapter;
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment;
import cn.alvkeke.dropto.ui.fragment.CategoryListFragment;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;
import cn.alvkeke.dropto.ui.intf.ListNotification;
import cn.alvkeke.dropto.ui.intf.SystemKeyListener;

public class MainActivity extends AppCompatActivity implements
        NoteDetailFragment.NoteEventListener, CategoryDetailFragment.CategoryDetailEvent {

    private ViewPager2 viewPager;
    private MainFragmentAdapter fragmentAdapter;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            finish();
            return;
        }

        initCategoryList();

        switch (action) {
            case Intent.ACTION_MAIN:
                onCreateMain(savedInstanceState);
                break;
            case Intent.ACTION_SEND:
                onCreateSend(intent);
                break;
            default:
                finish();
        }
    }

    private void onCreateSend(Intent intent) {
        NoteItem recvNote = handleSharedInfo(intent);
        if (recvNote == null) {
            Toast.makeText(this, "Failed to create new item",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .add(new CategorySelectorFragment(this, new ShareRecvHandler(recvNote)), null)
                .commit();
    }

    class ShareRecvHandler implements CategorySelectorFragment.CategorySelectListener {

        private final NoteItem recvNote;
        ShareRecvHandler(NoteItem recvNote) {
            this.recvNote = recvNote;
        }

        @Override
        public void onSelected(int index, Category category) {
            handleNoteCreate(category, recvNote);
            finish();
        }

        @Override
        public void onError(String error) {
            Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
        }
    }

    NoteItem handleSharedInfo(Intent intent) {
        String type = intent.getType();

        if (type == null) {
            Log.e(this.toString(), "Cannot get type");
            Toast.makeText(this, "Cannot get type", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (type.startsWith("text/")) {
            return handleText(intent);
        } else if (type.startsWith("image/")) {
            return handleImage(intent);
        } else {
            Log.e(this.toString(), "Got unsupported type: " + type);
        }
        return null;
    }

    NoteItem handleText(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            Log.e(this.toString(), "Failed to get shared text");
            return null;
        }

        return new NoteItem(text);
    }

    // TODO: seems ugly implementation, seek if there is better implementation
    private String getFileNameFromUri(Uri uri) {
        // ContentResolver to resolve the content Uri
        ContentResolver resolver = this.getContentResolver();
        // Query the file name from the content Uri
        Cursor cursor = resolver.query(uri, null, null, null, null);
        String fileName = null;
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index != -1) {
                fileName = cursor.getString(index);
            }
            cursor.close();
        }
        return fileName;
    }

    NoteItem handleImage(Intent intent) {
        File storeFolder = Global.getInstance().getFileStoreFolder();
        if (storeFolder == null) {
            Log.e(this.toString(), "Failed to get storage folder");
            return null;
        }

        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            Log.e(this.toString(), "Failed to get Uri");
            return null;
        }

        try (ParcelFileDescriptor inputPFD = this.getContentResolver().openFileDescriptor(uri, "r")) {
            if (inputPFD == null) {
                Log.e(this.toString(), "Failed to get ParcelFileDescriptor");
                return null;
            }
            FileDescriptor fd = inputPFD.getFileDescriptor();
            byte[] md5sum = FileHelper.calculateMD5(fd);
            File retFile = FileHelper.md5ToFile(storeFolder, md5sum);
            if (retFile.isFile() && retFile.exists()) {
                Log.d(this.toString(), "File exist");
            } else {
                Log.d(this.toString(), "Save file to : " + retFile.getAbsolutePath());
                FileHelper.copyFileTo(fd, retFile);
            }
            String ext_str = intent.getStringExtra(Intent.EXTRA_TEXT);
            NoteItem item = new NoteItem(ext_str == null ? "" : ext_str);
            item.setImageFile(retFile);
            item.setImageName(getFileNameFromUri(uri));
            return item;
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to store shared file: " + e);
        }
        return null;
    }

    private void onCreateMain(Bundle savedInstanceState) {
        Log.e(this.toString(), "enter MAIN mode");
        setTheme(R.style.Theme_DropTo_NoActionBar);
        setContentView(R.layout.activity_main);

        getOnBackPressedDispatcher().
                addCallback(this, new OnFragmentBackPressed(true));

        viewPager = findViewById(R.id.main_viewpager);
        fragmentAdapter = new MainFragmentAdapter(this);
        fragmentAdapter.createCategoryListFragment(new CategoryListAttemptListener(),
                Global.getInstance().getCategories());
        viewPager.setAdapter(fragmentAdapter);

        if (savedInstanceState != null) {
            int index = savedInstanceState.getInt("currentPageIndex");
            viewPager.setCurrentItem(index);
        }
    }

    class OnFragmentBackPressed extends OnBackPressedCallback {
        public OnFragmentBackPressed(boolean enabled) {
            super(enabled);
        }

        @Override
        public void handleOnBackPressed() {
            int index = viewPager.getCurrentItem();
            SystemKeyListener listener = (SystemKeyListener) fragmentAdapter.getFragmentAt(index);
            if (!listener.onBackPressed()) {
                MainActivity.this.finish();
            }
        }
    }

    public void handleCategoryExpand(Category category) {
        handleNoteLoad(category);
        fragmentAdapter.createNoteListFragment(new NoteListAttemptListener(), category);
        viewPager.setCurrentItem(1);
    }

    private void handleCategoryCreate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort creating");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            category.setId(helper.insertCategory(category));
            helper.finish();
            ArrayList<Category> categories = Global.getInstance().getCategories();
            categories.add(category);
            int index = categories.indexOf(category);
            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
                    ListNotification.Notify.CREATED, index, categories);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new category to database");
        }
    }

    public void showCategoryCreatingDialog() {
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(null), null)
                .commit();
    }

    private void handleCategoryRemove(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort deleting");
            return;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        int index;
        if ((index = categories.indexOf(category)) == -1) {
            Log.i(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.deleteCategory(category.getId()))
                Log.e(this.toString(), "no category row be deleted in database");
            helper.finish();
            categories.remove(category);
            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
                    ListNotification.Notify.REMOVED, index, category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove category with id " +
                            category.getId() + ", exception: " + ex);
        }
    }

    private void handleCategoryUpdate(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort modifying");
            return;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
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
            fragmentAdapter.getCategoryFragment().notifyItemListChanged(
                    ListNotification.Notify.MODIFIED, index, category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to modify Category: " + ex);
        }
    }

    private void deleteCategoryRecursion(Category category) {
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
        handleCategoryRemove(category);
    }

    @Override
    public void onCategoryDetailFinish(CategoryDetailFragment.Result result, Category category) {
        switch (result) {
            case CREATE:
                handleCategoryCreate(category);
                break;
            case DELETE:
                handleCategoryRemove(category);
                break;
            case FULL_DELETE:
                deleteCategoryRecursion(category);
                break;
            case MODIFY:
                handleCategoryUpdate(category);
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

    private void handleNoteListExit() {
        viewPager.setCurrentItem(0);
        fragmentAdapter.removeFragment(MainFragmentAdapter.FragmentType.NoteList);
    }

    private void handleNoteLoad(Category c) {
        if (!c.getNoteItems().isEmpty()) return;

        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(this)) {
            dataBaseHelper.start();
            dataBaseHelper.queryNote(-1, c.getId(), c.getNoteItems());
            dataBaseHelper.finish();
        }
    }

    private void handleNoteCreate(Category c, NoteItem newItem) {
        newItem.setCategoryId(c.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            newItem.setId(dbHelper.insertNote(newItem));
            dbHelper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new item to database!");
            return;
        }

        int index = c.addNoteItem(newItem);
        NoteListFragment fragment = fragmentAdapter.getNoteListFragment();
        if (fragment == null) return;
        fragment.notifyItemListChanged(ListNotification.Notify.CREATED, index, newItem);
    }

    private void handleNoteRemove(Category c, NoteItem e) {
        int index = c.indexNoteItem(e);
        if (index == -1) return;

        try (DataBaseHelper dbHelper = new DataBaseHelper(this)){
            dbHelper.start();
            if (0 == dbHelper.deleteNote(e.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            c.delNoteItem(e);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove item with id " +
                    e.getId() + ", exception: " + e);
            return;
        }

        fragmentAdapter.getNoteListFragment().notifyItemListChanged(
                ListNotification.Notify.REMOVED, index, e);
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

    private void handleNoteUpdate(Category c, NoteItem newItem) {
        NoteItem oldItem = c.findNoteItem(newItem.getId());
        if (oldItem == null) {
            Log.e(this.toString(), "Failed to get note item with id "+ newItem.getId());
            return;
        }
        Log.e(this.toString(), "newItem == oldItem: " + newItem.equals(oldItem));
        int index = c.indexNoteItem(oldItem);
        newItem.setId(oldItem.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            if (0 == dbHelper.updateNote(newItem)) {
                Log.i(this.toString(), "no item was updated");
                return;
            }
            dbHelper.finish();
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            return;
        }

        oldItem.update(newItem, true);
        fragmentAdapter.getNoteListFragment().notifyItemListChanged(
                ListNotification.Notify.MODIFIED, index, newItem);
    }

    class NoteListAttemptListener implements NoteListFragment.AttemptListener {
        @Override
        public void onAttemptRecv(Attempt attempt, Category c, NoteItem e) {
            switch (attempt) {
                case REMOVE:
                    handleNoteRemove(c, e);
                    break;
                case CREATE:
                    handleNoteCreate(c, e);
                    break;
                case DETAIL:
                    handleNoteDetailShow(e);
                    break;
                case COPY:
                    handleNoteCopy(e);
                    break;
                case EXIT:
                    handleNoteListExit();
                    break;
                case SHARE:
                    handleNoteShare(e);
                    break;
                case UPDATE:
                    break;
            }
        }

        @Override
        public void onErrorRecv(String errorMessage) {
            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private Category findCategoryById(long id) {
        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (Category c : categories) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    @Override
    public void onNoteDetailFinish(NoteDetailFragment.Result result, NoteItem item) {
        Category c = findCategoryById(item.getCategoryId());
        if (c == null) {
            Log.e(this.toString(), "Failed to get Category of noteItem");
            return;
        }
        switch (result) {
            case CREATE:
                handleNoteCreate(c, item);
                break;
            case MODIFY:
                handleNoteUpdate(c, item);
                break;
            case REMOVE:
                handleNoteRemove(c, item);
                break;
        }
    }
}