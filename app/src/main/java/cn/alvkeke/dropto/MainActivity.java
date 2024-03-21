package cn.alvkeke.dropto;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class MainActivity extends AppCompatActivity {

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

            dbHelper.queryCategory(-2, categories);
            dbHelper.queryNote(-2, categories.get(0).getNoteItems());
            dbHelper.finish();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new CategoryActivity())
                    .commitNow();
        }
    }
}