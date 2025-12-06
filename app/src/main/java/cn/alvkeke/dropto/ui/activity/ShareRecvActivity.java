package cn.alvkeke.dropto.ui.activity;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.mgmt.Global;
import cn.alvkeke.dropto.data.ImageFile;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.service.CoreService;
import cn.alvkeke.dropto.service.CoreServiceConnection;
import cn.alvkeke.dropto.service.Task;
import cn.alvkeke.dropto.storage.DataLoader;
import cn.alvkeke.dropto.storage.FileHelper;
import cn.alvkeke.dropto.ui.fragment.CategorySelectorFragment;

public class ShareRecvActivity extends AppCompatActivity
        implements CategorySelectorFragment.CategorySelectListener,
        Task.ResultListener {

    private CoreService service = null;
    private final CoreServiceConnection serviceConn = new CoreServiceConnection(this) {
        @Override
        public void execOnServiceConnected(ComponentName componentName, Bundle bundleAfterConnected) {
            service = getService();
        }

        @Override
        public void execOnServiceDisconnected() {
            super.execOnServiceDisconnected();
            service = null;
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
        if (service == null) return;
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
        categorySelectorFragment.setCategories(DataLoader.getCategories());
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
        recvNote.categoryId = category.id;
        if (pendingUris != null) {
            for (Uri uri : pendingUris) {
                ImageFile imageFile = extraImageFileFromUri(uri);
                if (imageFile != null)
                    recvNote.addImageFile(imageFile);
            }
        }
        service.queueTask(Task.createNote(recvNote, null));
        finish();
    }

    @Override
    public void onError(String error) {
        Toast.makeText(ShareRecvActivity.this, error, Toast.LENGTH_SHORT).show();
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
        File folder = Global.getFolderImage(this);
        File md5file = FileHelper.saveUriToFile(this, uri, folder);
        if (md5file == null) return null;
        String imgName = FileHelper.getFileNameFromUri(this, uri);
        return new ImageFile(md5file, imgName);
    }

    private void onCategoryTaskFinish(Task task) {
        if (task.result < 0) return;
        if (categorySelectorFragment == null) return;
        switch (task.job) {
            case CREATE:
            case REMOVE:
            case UPDATE:
                categorySelectorFragment.notifyItemListChanged(
                        Task.jobToNotify(task.job), task.result, (Category) task.param);
                break;
            default:
                break;
        }
    }

    @Override
    public void onTaskFinish(Task task, Object param) {
        if (task.type == Task.Type.Category)
            onCategoryTaskFinish(task);
    }
}