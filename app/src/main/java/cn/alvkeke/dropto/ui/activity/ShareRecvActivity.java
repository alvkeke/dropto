package cn.alvkeke.dropto.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;
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

    private static final int BUFFER_SIZE = 1024;
    File saveFileWithMd5Name(FileDescriptor fd, File storeFolder) {

        String tempName = "tmp_" + System.nanoTime();
        int name_idx = 0;
        File fileTmp = new File(storeFolder, tempName);
        while (fileTmp.exists()) {
            fileTmp = new File(storeFolder, tempName + "_" + name_idx++);
            if (name_idx >= 1000 ) {
                Log.e(this.toString(), "Try names so many times, abort!!");
                return null;
            }
        }
        Log.d(this.toString(), "target name: " + fileTmp.getName());

        byte[] buffer = new byte[BUFFER_SIZE];
        int lenRead;
        File fileTarget;
        try {
            FileInputStream fis = new FileInputStream(fd);
            BufferedInputStream bis = new BufferedInputStream(fis);
            DigestInputStream dis = new DigestInputStream(bis,
                    MessageDigest.getInstance("MD5"));
            FileOutputStream fos = new FileOutputStream(fileTmp);

            while ((lenRead = dis.read(buffer)) != -1) {
                fos.write(buffer, 0, lenRead);
            }
            fos.flush();
            fos.close();
            dis.close();

            byte[] digest = dis.getMessageDigest().digest();
            String md5 = bytesToHex(digest);
            fileTarget = new File(storeFolder, md5);
            boolean ret;
            if (fileTarget.exists()) {
                Log.i(this.toString(), "file with md5:" + md5 + " already exist, delete");
                ret = fileTmp.delete();
            } else {
                ret = fileTmp.renameTo(fileTarget);
            }
            Log.d(this.toString(), "Operation result: " + ret);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return fileTarget;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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

        ParcelFileDescriptor inputPFD;
        try {
            inputPFD = getContentResolver().openFileDescriptor(uri, "r");
            if (inputPFD == null) {
                Log.e(this.toString(), "Failed to get ParcelFileDescriptor");
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(this.toString(), "Failed to get file fd");
            return null;
        }

        FileDescriptor fd = inputPFD.getFileDescriptor();
        File retFile = saveFileWithMd5Name(fd, storeFolder);
        Log.d(this.toString(), "Save file to : " + retFile.getAbsolutePath());

        String ext_str = intent.getStringExtra(Intent.EXTRA_TEXT);
        NoteItem item = new NoteItem(ext_str == null ? "" : ext_str);
        item.setImageFile(retFile);
        return item;
    }
}