package cn.alvkeke.dropto.mgmt;

import android.content.Context;
import android.util.Log;

import java.io.File;

public class Global {
    private static final Global _instance = new Global();
    private File imageStorage = null;
    private File imageCacheShare = null;

    private Global() {
    }

    public static Global getInstance() {
        return _instance;
    }

    public File getFolderImage(Context context) {
        if (imageStorage != null) {
            if (!imageStorage.exists())
                Log.i(this.toString(), "image folder not exist, create: " + imageStorage.mkdir());
            return imageStorage;
        }

        imageStorage = context.getExternalFilesDir("imgs");
        assert imageStorage != null;
        if (!imageStorage.exists()) {
            Log.i(this.toString(), "image folder not exist, create: " + imageStorage.mkdir());
        }
        return imageStorage;
    }

    public File getFolderImageShare(Context context) {
        if (imageCacheShare != null) {
            if (!imageCacheShare.exists())
                Log.i(this.toString(), "share folder not exist, create: " + imageCacheShare.mkdir());
            return imageCacheShare;
        }

        imageCacheShare = context.getExternalCacheDir();
        imageCacheShare = new File(imageCacheShare, "share");
        if (!imageCacheShare.exists()) {
            Log.i(this.toString(), "share folder not exist, create: " + imageCacheShare.mkdir());
        }
        return imageCacheShare;
    }

}
