package cn.alvkeke.dropto.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;

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
import java.util.Date;

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;

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
        String action = intent.getAction();
        String type = intent.getType();

        adapter.setItemClickListener(new CategoryListAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int index, View v) {
                if (! Intent.ACTION_SEND.equals(action)) {
                    Log.e(this.toString(), "Wrong action: " + action);
                    return;
                }
                if (type == null) {
                    Log.e(this.toString(), "Cannot get type");
                    return;
                }

                if (type.startsWith("text/")) {
                    handleText(index, intent);
                } else if (type.startsWith("image/")) {
                    handleImage(index, intent);
                }

                finish();
            }
        });
    }

    void handleText(int index, Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            Log.e(this.toString(), "Failed to get shared text");
            return;
        }

        Category category = Global.getInstance().getCategories().get(index);
        if (category == null) {
            Log.e(this.toString(), "Failed to get category at : " + index);
            return;
        }

        NoteItem item = new NoteItem(text);
        category.getNoteItems().add(item);
    }

    private static final int BUFFER_SIZE = 1024;
    File saveFileWithMd5Name(FileDescriptor fd, File storeFolder) {

        String tempName = "tmp_" + new Date().getTime();
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

    void handleImage(int index, Intent intent) {
        File storeFolder = Global.getInstance().getFileStoreFolder();
        if (storeFolder == null) {
            Log.e(this.toString(), "Failed to get storage folder");
            return;
        }

        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            Log.e(this.toString(), "Failed to get Uri");
            return;
        }

        Category category = Global.getInstance().getCategories().get(index);
        if (category == null) {
            Log.e(this.toString(), "Failed to get category at : " + index);
            return;
        }

        ParcelFileDescriptor inputPFD;
        try {
            inputPFD = getContentResolver().openFileDescriptor(uri, "r");
            if (inputPFD == null) {
                Log.e(this.toString(), "Failed to get ParcelFileDescriptor");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(this.toString(), "Failed to get file fd");
            return;
        }

        FileDescriptor fd = inputPFD.getFileDescriptor();
        File retFile = saveFileWithMd5Name(fd, storeFolder);
        Log.d(this.toString(), "Save file to : " + retFile.getAbsolutePath());

        NoteItem item = new NoteItem("");
        item.setImageFile(retFile);
        category.getNoteItems().add(item);
    }
}