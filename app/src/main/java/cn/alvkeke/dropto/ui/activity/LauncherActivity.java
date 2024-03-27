package cn.alvkeke.dropto.ui.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.debug.DebugFunction;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;

public class LauncherActivity extends AppCompatActivity implements CategorySelectorFragment.CategorySelectListener {


    private NoteItem noteItem;
    private void initData() {
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
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            finish();
            return;
        }
        initData();

        switch (action) {
            case Intent.ACTION_MAIN:
                Intent intent1 = new Intent(this, MainActivity.class);
                startActivity(intent1);
                finish();
                break;
            case Intent.ACTION_SEND:
                noteItem = handleSharedInfo(intent);
                if (noteItem == null) {
                    Toast.makeText(this, "Failed to create new item",
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                getSupportFragmentManager().beginTransaction()
                        .add(new CategorySelectorFragment(this), null)
                        .commit();
                break;
            default:
                finish();
        }
    }

    @Override
    public void onShareRecvResult(Result result, int index, Category category) {
        assert result != null;
        if (result == Result.SELECTED) {
            Log.e(this.toString(), "new item for category at: " + index);
            handleItemNew(category, noteItem);
        } else if (result == Result.ERROR) {
            Log.e(this.toString(), "Failed to select the category at: " + index);
            Toast.makeText(this, "Failed to create new item for category at " + index,
                    Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void handleItemNew(Category category, NoteItem item) {
        if (item == null) {
            Log.e(this.toString(), "Failed to generated a new noteItem");
            finish();
            return;
        }
        item.setCategoryId(category.getId());
        try (DataBaseHelper dbHelper = new DataBaseHelper(this)) {
            dbHelper.start();
            item.setId(dbHelper.insertNote(item));
            dbHelper.finish();
            category.addNoteItem(item);
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to add item to database, abort");
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
}
