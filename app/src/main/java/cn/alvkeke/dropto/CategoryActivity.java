package cn.alvkeke.dropto;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.ui.CategoryListAdapter;

public class CategoryActivity extends AppCompatActivity {

    private CategoryListAdapter categoryListAdapter;

    @Override
    protected void onResume() {
        super.onResume();

        ArrayList<Category> categories = Global.getInstance().getCategories();
        for (int i=0; i<categories.size(); i++) {
            Category c = categories.get(i);
            if (c.needUpdate()) {
                categoryListAdapter.notifyItemChanged(i);
                c.setUpdated();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);

        RecyclerView rlCategory = findViewById(R.id.rlist_category);

        ArrayList<Category> categories = Global.getInstance().getCategories();
        categoryListAdapter = new CategoryListAdapter(categories);
        categoryListAdapter.setCategories(categories);

        rlCategory.setAdapter(categoryListAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        categoryListAdapter.setItemClickListener(new onListItemClick());

        File img_folder = getExternalFilesDir("imgs");
        if (img_folder == null) {
            Log.e(this.toString(), "Failed to get image folder, abort");
            assert false;   // crash app
        }
        if (!img_folder.exists()) {
            Log.i(this.toString(), "image folder not exist, create: " + img_folder.mkdir());
        }
        Global.getInstance().setFileStoreFolder(img_folder);

        if (BuildConfig.DEBUG) {
            DataBaseHelper dbHelper = new DataBaseHelper(this);
            dbHelper.destroyDatabase();
            dbHelper.start();

            long parentId = dbHelper.insertCategory("Local(Debug)", Category.Type.LOCAL_CATEGORY, "");
            dbHelper.insertCategory("REMOTE USERS", Category.Type.REMOTE_USERS, "");
            dbHelper.insertCategory("REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV, "");

            List<File> img_files = DebugFunction.try_extract_res_images(this, img_folder);
            Random r = new Random();
            int idx = 0;
            for (int i=0; i<15; i++) {
                NoteItem e = new NoteItem("ITEM" + i + i, System.currentTimeMillis());
                e.setCategoryId(parentId);
                if (r.nextBoolean()) {
                    e.setText(e.getText(), true);
                }
                if (idx < img_files.size() && r.nextBoolean()) {
                    File img_file = img_files.get(idx);
                    idx++;
                    if (img_file.exists()) {
                        Log.d("DebugFunction", "add image file: " + img_file);
                    } else {
                        Log.e("DebugFunction", "add image file failed, not exist: " + img_file);
                    }

                    e.setImageFile(img_file);
                }
                e.setId(dbHelper.insertNote(e));
            }

            dbHelper.queryCategory(-1, categories);
            dbHelper.queryNote(-1, categories.get(0).getNoteItems());
            dbHelper.finish();
            categoryListAdapter.notifyItemRangeChanged(0, categories.size()-1);
        }
    }

    ActivityResultLauncher<Intent> noteListActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> Log.d(this.toString(), "NoteListActivity exit")
    );

    class onListItemClick implements CategoryListAdapter.OnItemClickListener {

        @Override
        public void onItemClick(int index, View v) {
            Log.d(this.toString(), "Category clicked on " + index);
            Intent intent = new Intent(CategoryActivity.this, NoteListActivity.class);
            intent.putExtra(NoteListActivity.CATEGORY_INDEX, index);
            noteListActivityLauncher.launch(intent);
        }
    }
}