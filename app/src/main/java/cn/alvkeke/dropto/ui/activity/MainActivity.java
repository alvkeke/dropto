package cn.alvkeke.dropto.ui.activity;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
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
import cn.alvkeke.dropto.ui.adapter.MainFragmentAdapter;
import cn.alvkeke.dropto.ui.fragment.CategoryFragment;
import cn.alvkeke.dropto.ui.fragment.NoteDetailFragment;
import cn.alvkeke.dropto.ui.fragment.NoteListFragment;

public class MainActivity extends AppCompatActivity
        implements CategoryFragment.CategoryEventListener, NoteListFragment.NoteListEventListener,
        NoteDetailFragment.NoteEventListener {

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

        viewPager = findViewById(R.id.main_viewpager);
        fragmentAdapter = new MainFragmentAdapter(this);
        viewPager.setAdapter(fragmentAdapter);

        if (savedInstanceState != null) {
            int index = savedInstanceState.getInt("currentPageIndex");
            viewPager.setCurrentItem(index);
        }
    }

    @Override
    public void onNoteListShow(int index, Category category) {
        Log.e(this.toString(), "Try to show category list: " + index);
        fragmentAdapter.setCurrentCategory(index, category);
        viewPager.setCurrentItem(1);
    }

    @Override
    public void onExit() {
        viewPager.setCurrentItem(0);
    }

    @Override
    public void onListDetailShow(int index, NoteItem item) {
        fragmentAdapter.setCurrentNote(index, item);
        viewPager.setCurrentItem(2);
    }

    @Override
    public void onNoteEdit(int index, NoteItem newNote) {
        Log.e(this.toString(), "Note in "+ index + " was edited");
        // TODO: implement the real function;
    }

    @Override
    public void onNoteDelete(int index, NoteItem noteItem) {
        Log.e(this.toString(), "Note in " + index + " was removed");
        // TODO: implement the real function;
    }

    @Override
    public void onNoteCancel(int index, NoteItem noteItem) {
        // TODO: implement the real function;
    }

    @Override
    public void onNoteAdd(NoteItem item) {
        // TODO: implement the real function;
    }
}