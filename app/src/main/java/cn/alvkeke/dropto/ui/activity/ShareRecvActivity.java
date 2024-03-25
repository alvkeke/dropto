package cn.alvkeke.dropto.ui.activity;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.adapter.CategoryListAdapter;

public class ShareRecvActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_recv);

        RecyclerView rlCategory = findViewById(R.id.rlist_share_recv);
        ArrayList<Category> categories = Global.getInstance().getCategories();
        CategoryListAdapter adapter = new CategoryListAdapter(categories);
        rlCategory.setAdapter(adapter);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        rlCategory.setLayoutManager(layoutManager);
        rlCategory.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        Intent intent = getIntent();
        if (intent == null) {
            Log.e(this.toString(), "Failed to get intent");
            finish();
            return;
        }

        adapter.setItemClickListener(new CategoryListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int index, View v) {
                Category category = Global.getInstance().getCategories().get(index);
                if (category == null) {
                    Log.e(this.toString(), "Failed to get category at : " + index);
                    finish();
                    return;
                }

                NoteItem item = handleSharedInfo(intent);
                if (item == null) {
                    Log.e(this.toString(), "Failed to generated a new noteItem");
                    finish();
                    return;
                }
                item.setCategoryId(category.getId());
                try (DataBaseHelper dbHelper = new DataBaseHelper(ShareRecvActivity.this)) {
                    dbHelper.start();
                    item.setId(dbHelper.insertNote(item));
                    dbHelper.finish();
                } catch (Exception e) {
                    Log.e(this.toString(), "Failed to add item to database, abort");
                    return;
                }

                // no need to add item in noteItem list, item will be retrieved when list load
                finish();
            }

            @Override
            public boolean onItemLongClick(int index, View v) {
                return false;
            }
        });
    }

    NoteItem handleSharedInfo(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        if (! Intent.ACTION_SEND.equals(action)) {
            Log.e(this.toString(), "Wrong action: " + action);
            Toast.makeText(ShareRecvActivity.this,
                    "Got wrong action: " + action, Toast.LENGTH_SHORT).show();
            return null;
        }

        if (type == null) {
            Log.e(this.toString(), "Cannot get type");
            Toast.makeText(ShareRecvActivity.this,
                    "Cannot get type", Toast.LENGTH_SHORT).show();
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
        ContentResolver resolver = getContentResolver();
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

        try (ParcelFileDescriptor inputPFD = getContentResolver().openFileDescriptor(uri, "r")) {
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