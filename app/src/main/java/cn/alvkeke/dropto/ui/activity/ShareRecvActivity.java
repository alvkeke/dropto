package cn.alvkeke.dropto.ui.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.ImageFile;
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

    private CategorySelectorFragment categorySelectorFragment;
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

        pendingText = handleTextInfo(intent);
        String type = intent.getType();
        if (type != null) {
            if (type.startsWith("image/")) {
                pendingUris = handleImageUris(intent);
            }
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

    private String pendingText = null;
    private ArrayList<Uri> pendingUris = null;
    private boolean noPendingUris() {
        return pendingUris == null || pendingUris.isEmpty();
    }

    @Override
    public void onSelected(int index, Category category) {
        if (pendingText.isEmpty() && noPendingUris()) {
            Toast.makeText(this, "Empty item, abort", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        NoteItem recvNote = new NoteItem(pendingText);
        recvNote.setCategoryId(category.getId());
        if (pendingUris != null) {
            for (Uri uri : pendingUris) {
                ImageFile imageFile = extraImageFileFromUri(uri);
                recvNote.addImageFile(imageFile);
            }
        }
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

    @NonNull
    private String handleTextInfo(@NonNull Intent intent) {
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) {
            Log.e(this.toString(), "Failed to get shared text");
            return "";
        }
        return text.trim();
    }

    private ArrayList<Uri> handleImageUris(@NonNull Intent intent) {
        ArrayList<Uri> uris = new ArrayList<>();
        String action = intent.getAction();
        if (action == null)
            return null;

        switch (action) {
            case Intent.ACTION_SEND:
                uris.add(handleSingleImageUri(intent));
                break;
            case Intent.ACTION_SEND_MULTIPLE:
                handleMultipleImageUris(intent, uris);
                break;
        }

        return uris;
    }

    private Uri handleSingleImageUri(@NonNull Intent intent) {
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private void handleMultipleImageUris(@NonNull Intent intent, ArrayList<Uri> uris) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris == null || imageUris.isEmpty()) return;
        uris.addAll(imageUris);
    }

    private ImageFile extraImageFileFromUri(Uri uri) {
        File folder = Global.getInstance().getFileStoreFolder();
        File md5file = FileHelper.saveUriToFile(this, uri, folder);
        if (md5file == null) return null;
        String imgName = FileHelper.getFileNameFromUri(this, uri);
        return new ImageFile(md5file, imgName);
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