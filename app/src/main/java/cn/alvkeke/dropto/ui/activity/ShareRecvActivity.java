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
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.storage.DataBaseHelper;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;

public class ShareRecvActivity extends AppCompatActivity {

    private void initCategoryList() {
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

    private CoreService.CoreSrvBinder binder = null;
    private final ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            binder = (CoreService.CoreSrvBinder) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (binder == null) return;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setupCoreService();
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action == null) {
            Toast.makeText(this, "action is null", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initCategoryList();

        if (!action.equals(Intent.ACTION_SEND)) {
            Toast.makeText(this, "unknown action: " + action, Toast.LENGTH_SHORT).show();
            finish();
        }
        NoteItem recvNote = handleSharedInfo(intent);
        if (recvNote == null) {
            Toast.makeText(this, "Failed to create new item",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .add(new CategorySelectorFragment(this, new ShareRecvHandler(recvNote)), null)
                .commit();
    }

    class ShareRecvHandler implements CategorySelectorFragment.CategorySelectListener {

        private final NoteItem recvNote;
        ShareRecvHandler(NoteItem recvNote) {
            this.recvNote = recvNote;
        }

        @Override
        public void onSelected(int index, Category category) {
            recvNote.setCategoryId(category.getId());
            binder.getService().queueTask(CoreService.Task.Type.CREATE, recvNote);
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