package cn.alvkeke.dropto;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.ui.CategoryListAdapter;

public class CategoryActivity extends AppCompatActivity {

    private CategoryListAdapter categoryListAdapter;

    private boolean extract_raw_file(int id, File o_file) {
        if (o_file.exists()) {
            // file exist, return true to indicate can be load
            Log.d(this.toString(), "file exist, don't extract:" + o_file);
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Log.e(this.toString(), "SDK_VERSION error: " + Build.VERSION.SDK_INT);
            return false;
        }
        byte[] buffer = new byte[1024];
        try {
            InputStream is = getResources().openRawResource(id);
            OutputStream os = Files.newOutputStream(o_file.toPath());
            int len;
            while((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            is.close();
        } catch (IOException e) {
            Log.e(this.toString(), "Failed to extract res: " +
                    getResources().getResourceEntryName(id) + " to " + o_file);
            return false;
        }
        return true;
    }

    private List<File> try_extract_res_images(File folder) {

        List<Integer> rawIds = new ArrayList<>();
        Field[] fields = R.raw.class.getFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    int id = f.getInt(null);
                    rawIds.add(id);
                } catch (IllegalAccessException e) {
                    Log.e(this.toString(), "failed to get resource ID of raw:" + f);
                }
            }
        }

        List<File> ret_files = new ArrayList<>();
        for (int id : rawIds) {
            File o_file = new File(folder, getResources().getResourceEntryName(id) + ".png");
            if (extract_raw_file(id, o_file))
                ret_files.add(o_file);
        }

        return ret_files;
    }

    private void dbg_fill_list(ArrayList<NoteItem> list) {

        Log.e(this.toString(), "sdcard: " + Environment.getExternalStorageDirectory());

        int idx = 0;
        Random r = new Random();
        File img_folder = this.getExternalFilesDir("imgs");
        if (img_folder == null) {
            Log.e(this.toString(), "Failed to get image folder, exit!!");
            return;
        }
        Log.d(this.toString(), "image folder path: " + img_folder);
        if (!img_folder.exists() && img_folder.mkdir()) {
            Log.e(this.toString(), "failed to create folder: " + img_folder);
        }
        Global.getInstance().setFileStoreFolder(img_folder);
        List<File> img_files = try_extract_res_images(img_folder);

        for (int i=0; i<15; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i, new Date().getTime());
            if (r.nextBoolean()) {
                e.setText(e.getText(), true);
            }
            if (idx < img_files.size() && r.nextBoolean()) {
                File img_file = img_files.get(idx);
                idx++;
                if (img_file.exists()) {
                    Log.d(this.toString(), "add image file: " + img_file);
                } else {
                    Log.e(this.toString(), "add image file failed, not exist: " + img_file);
                }

                e.setImageFile(img_file);
            }
            list.add(e);
            categoryListAdapter.notifyItemInserted(i);
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

        if (BuildConfig.DEBUG) {
            Category categoryDebug;
            categoryDebug = new Category("Local(Debug)", Category.Type.LOCAL_CATEGORY);
            dbg_fill_list(categoryDebug.getNoteItems());
            categories.add(categoryDebug);
            categoryListAdapter.notifyItemInserted(categories.size()-1);

            categories.add(new Category("REMOTE USERS", Category.Type.REMOTE_USERS));
            categoryListAdapter.notifyItemInserted(categories.size()-1);
            categories.add(new Category("REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV));
            categoryListAdapter.notifyItemInserted(categories.size()-1);
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