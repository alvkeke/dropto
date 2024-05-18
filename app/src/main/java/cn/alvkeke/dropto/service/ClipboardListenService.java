package cn.alvkeke.dropto.service;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class ClipboardListenService extends AccessibilityService{
    public ClipboardListenService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initClipboardListener();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
        switch (accessibilityEvent.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED:
//                Log.e(this.toString(), "clipboard? " + accessibilityEvent);
                ClipData data = clipboardManager.getPrimaryClip();
                if (data == null) break;
                ClipData.Item item = data.getItemAt(0);
//                if (item == oldItem) {
                if (item.equals(oldItem)) {
                    Log.e(this.toString(), "handled data: " + data);
                    break;
                }
                oldItem = item;
                clipChangedListener.onPrimaryClipChanged();
                break;
            default:
//                Log.e(this.toString(), "clipboard default? " + accessibilityEvent);
                break;
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        deInitClipboardListener();
    }

    private ClipboardManager clipboardManager;
    private ClipData.Item oldItem = null;

    private final ClipboardManager.OnPrimaryClipChangedListener clipChangedListener = () -> {
        if (!clipboardManager.hasPrimaryClip())
            return;

        ClipData clip = clipboardManager.getPrimaryClip();
        Log.d(this.toString(), "got new text: " + clip);
        if (clip == null) return;

        ClipData.Item item = clip.getItemAt(0);

        String text = String.valueOf(item.getText());
        Uri uri = item.getUri();

        Log.e(this.toString(), text + ", " + uri);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    };

    private void initClipboardListener() {
        clipboardManager = (ClipboardManager) this.getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            oldItem = clipData.getItemAt(0);
        }
//        clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
    }

    private void deInitClipboardListener() {
//        clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
    }
}