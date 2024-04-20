package cn.alvkeke.dropto.ui.activity;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;

public class ShareRecvActivity extends AppCompatActivity
        implements CategorySelectorFragment.CategorySelectListener, CoreService.TaskResultListener {



    private CoreService.CoreSrvBinder binder = null;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (CoreService.CoreSrvBinder) iBinder;
            binder.getService().addTaskListener(ShareRecvActivity.this);
            binder.getService().queueReadTask(CoreService.Task.Target.Category, 0);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (binder == null) return;
            binder.getService().delTaskListener(ShareRecvActivity.this);
            binder = null;
        }
    };

    private void setupCoreService() {
        Intent serviceIntent = new Intent(this, CoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
    }

    private void clearCoreService() {
        if (binder == null) return;
        unbindService(serviceConn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearCoreService();
    }

    CategorySelectorFragment categorySelectorFragment;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        if (savedInstanceState != null) {
            this.finish();
            // just end after change to dark mode
            return;
        }

        setupCoreService();
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            Toast.makeText(this, "action is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!isActionAllow(action)) {
            Toast.makeText(this, "unknown action: " + action, Toast.LENGTH_SHORT).show();
            finish();
        }
        recvNotes = handleIntent(intent);
        if (recvNotes == null || recvNotes.isEmpty()) {
            Toast.makeText(this, "Failed to create new item",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        categorySelectorFragment = new CategorySelectorFragment();
        categorySelectorFragment.setCategories(Global.getInstance().getCategories());
        getSupportFragmentManager().beginTransaction()
                .add(categorySelectorFragment, null)
                .commit();
    }

    private boolean isActionAllow(String action) {
        switch (action) {
            case Intent.ACTION_SEND:
            case Intent.ACTION_SEND_MULTIPLE:
                return true;
        }
        return false;
    }

    private ArrayList<NoteItem> recvNotes;

    @Override
    public void onSelected(int index, Category category) {
        for (NoteItem recvNote : recvNotes) {
            recvNote.setCategoryId(category.getId());
            binder.getService().queueTask(CoreService.Task.Type.CREATE, recvNote);
        }
        finish();
    }

    @Override
    public void onError(String error) {
        Toast.makeText(ShareRecvActivity.this, error, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onExit() {
        finish();
    }

    ArrayList<NoteItem> handleIntent(@NonNull Intent intent) {
        ArrayList<NoteItem> items = new ArrayList<>();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            NoteItem noteItem = handleSharedInfo(intent);
            items.add(noteItem);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            handleSharedInfoMultiple(intent, items);
        }
        return items;
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

    void handleSharedInfoMultiple(Intent intent, ArrayList<NoteItem> list) {
        String type = intent.getType();
        if (type == null) {
            Log.e(this.toString(), "Cannot get type");
            Toast.makeText(this, "Cannot get type", Toast.LENGTH_SHORT).show();
            return;
        }
        if (type.startsWith("image/")) {
            handleImageMultiple(intent, list);
        } else {
            Log.e(this.toString(), "Got unsupported type: "+type);
        }
    }

    NoteItem handleText(Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            Log.e(this.toString(), "Failed to get shared text");
            return null;
        }

        return new NoteItem(text);
    }

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

    private NoteItem extraNoteFromUri(Uri uri, String noteText) {
        File storeFolder = Global.getInstance().getFileStoreFolder();
        if (storeFolder == null) {
            Log.e(this.toString(), "Failed to get storage folder");
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
            NoteItem item = new NoteItem(noteText == null ? "" : noteText);
            item.setImageFile(retFile);
            item.setImageName(getFileNameFromUri(uri));
            return item;
        } catch (Exception e) {
            Log.e(this.toString(), "Failed to store shared file: " + e);
            return null;
        }
    }

    private NoteItem handleImage(Intent intent) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            Log.e(this.toString(), "Failed to get Uri");
            return null;
        }

        String ext_str = intent.getStringExtra(Intent.EXTRA_TEXT);

        return extraNoteFromUri(uri, ext_str);
    }

    private void handleImageMultiple(Intent intent, ArrayList<NoteItem> list) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris == null || imageUris.isEmpty()) return;

        for (Uri uri : imageUris) {
            if (uri == null) continue;
            NoteItem e = extraNoteFromUri(uri, "");
            if (e == null) continue;
            list.add(e);
        }
    }

    @Override
    public void onCategoryTaskFinish(CoreService.Task.Type taskType, int index, Category c) {
        if (index < 0) return;
        if (categorySelectorFragment == null) return;
        categorySelectorFragment.notifyItemListChanged(CoreService.taskToNotify(taskType), index, c);
    }

    @Override
    public void onNoteTaskFinish(CoreService.Task.Type taskType, int index, NoteItem n) { }
}