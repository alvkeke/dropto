package cn.alvkeke.dropto.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.ui.SystemKeyListener;
import cn.alvkeke.dropto.ui.adapter.MainFragmentAdapter;
import cn.alvkeke.dropto.ui.fragment.CategoryDetailFragment;
import cn.alvkeke.dropto.ui.fragment.CategoryFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;

public class MainActivity extends AppCompatActivity
        implements CategoryFragment.CategoryEventListener, NoteListFragment.NoteListEventListener,
        NoteDetailFragment.NoteEventListener, CategoryDetailFragment.CategoryDetailEvent {

    private ViewPager2 viewPager;
    private MainFragmentAdapter fragmentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Objects.requireNonNull(getSupportActionBar()).hide();

        ArrayList<Category> categories = Global.getInstance().getCategories();

        File img_folder = this.getExternalFilesDir("imgs");
        if (img_folder == null) {
            Log.e(this.toString(), "Failed to get image folder, abort");
            assert false;   // crash app
        }
        if (!img_folder.exists()) {
            Log.i(this.toString(), "image folder not exist, create: " + img_folder.mkdir());
        }
        Global.getInstance().setFileStoreFolder(img_folder);

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

        getOnBackPressedDispatcher().
                addCallback(this, new OnFragmentBackPressed(true));

        viewPager = findViewById(R.id.main_viewpager);
        fragmentAdapter = new MainFragmentAdapter(this);
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

    @Override
    public void onNoteListShow(Category category) {
        onNoteDetailExit(NoteDetailFragment.Result.CANCELED, null);
        fragmentAdapter.createNoteListFragment(category);
        viewPager.setCurrentItem(1);
    }

    @Override
    public void OnCategoryAdd() {
        getSupportFragmentManager().beginTransaction()
                .add(new CategoryDetailFragment(), null)
                .commit();
    }

    private void createCategory(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort creating");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            category.setId(helper.insertCategory(category));
            helper.finish();
            Global.getInstance().getCategories().add(category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new category to database");
        }
    }

    private void deleteCategory(Category category) {
        if (category == null) {
            Log.e(this.toString(), "null category, abort deleting");
            return;
        }
        ArrayList<Category> categories = Global.getInstance().getCategories();
        if (!categories.contains(category)) {
            Log.i(this.toString(), "category not exist in list, abort");
            return;
        }

        try (DataBaseHelper helper = new DataBaseHelper(this)) {
            helper.start();
            if (0 == helper.deleteCategory(category.getId()))
                Log.e(this.toString(), "no category row be deleted in database");
            helper.finish();
            categories.remove(category);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove category with id " +
                            category.getId() + ", exception: " + ex);
        }
    }

    @Override
    public void onCategoryDetailFinish(CategoryDetailFragment.Result result, Category category) {
        switch (result) {
            case CREATE:
                createCategory(category);
                break;
            case DELETE:
                deleteCategory(category);
                break;
            default:
                Log.d(this.toString(), "other result: " + result);
        }
    }

    @Override
    public void onCategoryDetail(Category c) {

    }

    @Override
    public void onNoteListClose() {
        viewPager.setCurrentItem(0);
        fragmentAdapter.removeFragment(MainFragmentAdapter.FragmentType.NoteList);
    }

    @Override
    public void onNoteListLoad(Category c) {
        if (!c.getNoteItems().isEmpty()) return;

        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(this)) {
            dataBaseHelper.start();
            dataBaseHelper.queryNote(-1, c.getId(), c.getNoteItems());
            dataBaseHelper.finish();
        }
    }

    @Override
    public int onNoteItemCreate(Category c, NoteItem newItem) {
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            newItem.setId(dbHelper.insertNote(newItem));
            dbHelper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to add new item to database!");
            return -1;
        }

        c.addNoteItem(newItem);
        return c.getNoteItems().size()-1;
    }

    @Override
    public int onNoteItemDelete(Category c, NoteItem e) {
        int index = c.indexNoteItem(e);
        if (index == -1) return -1;

        try (DataBaseHelper dbHelper = new DataBaseHelper(this)){
            dbHelper.start();
            if (0 == dbHelper.deleteNote(e.getId()))
                Log.e(this.toString(), "no row be deleted");
            dbHelper.finish();
            c.delNoteItem(e);
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to remove item with id " +
                    e.getId() + ", exception: " + e);
            return -1;
        }
        return index;
    }

    @Override
    public boolean onNoteItemShare(NoteItem item) {
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
            return false;
        }
        return true;
    }

    @Override
    public boolean onNoteItemCopy(NoteItem e) {
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null) {
            Log.e(this.toString(), "Failed to get ClipboardManager");
            return false;
        }
        ClipData data = ClipData.newPlainText("text", e.getText());
        clipboardManager.setPrimaryClip(data);
        return true;
    }

    @Override
    public void onNoteDetailShow(NoteItem item) {
        fragmentAdapter.createNoteDetailFragment(item);
        viewPager.setCurrentItem(2);
    }

    public int onNoteItemModify(Category c, NoteItem newItem) {
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
        } catch (Exception exception) {
            Log.e(this.toString(), "Failed to update note item in database: " + exception);
            return -1;
        }

        oldItem.update(newItem, true);
        return index;
    }

    private Category findCategoryById(long id) {
        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (Category c : categories) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    @Override
    public void onNoteDetailExit(NoteDetailFragment.Result result, NoteItem item) {
        fragmentAdapter.removeFragment(MainFragmentAdapter.FragmentType.NoteDetail);
        if (result == NoteDetailFragment.Result.CANCELED) return;
        NoteListFragment.ItemListState state;
        Category c = findCategoryById(item.getCategoryId());
        if (c == null) {
            Log.e(this.toString(), "Failed to get Category of noteItem");
            return;
        }
        int index = -1;
        switch (result) {
            case CREATED:
                state = NoteListFragment.ItemListState.CREATE;
                index = onNoteItemCreate(c, item);
                break;
            case MODIFIED:
                state = NoteListFragment.ItemListState.MODIFY;
                index = onNoteItemModify(c, item);
                break;
            case REMOVED:
                state = NoteListFragment.ItemListState.REMOVE;
                index = onNoteItemDelete(c, item);
                break;
            default:
                state = NoteListFragment.ItemListState.NONE;
        }
        if (index == -1) return;
        fragmentAdapter.getNoteListFragment().notifyItemListChanged(state, index, item);
    }
}