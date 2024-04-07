package cn.alvkeke.dropto.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ImageLoader {

    private static final ImageLoader instance = new ImageLoader();
    private ImageLoader() {

    }

    public static ImageLoader getInstance() {
        return instance;
    }

    private static class WrappedBitmap {
        Bitmap bitmap;
        long lastAccessTime;
        public WrappedBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    private final HashMap<String, WrappedBitmap> imagePool = new HashMap<>();
    private int imagePoolSize = 15;

    private void removeLongNotUsedImage() {
        Map.Entry<String, WrappedBitmap> target = null;
        for (Map.Entry<String, WrappedBitmap> current : imagePool.entrySet()) {
            if (target == null) {
                target = current;
                continue;
            }
            if (current.getValue().lastAccessTime < target.getValue().lastAccessTime) {
                target = current;
            }
        }
        if (target == null) return;
        Log.d(this.toString(), "[" + target.getKey() +
                "] expired, remove, last access: " + target.getValue().lastAccessTime);
        imagePool.remove(target.getKey());
    }

    public Bitmap loadImage(File file) {
        String filePath = file.getAbsolutePath();
        WrappedBitmap wrappedBitmap = imagePool.get(filePath);
        if (wrappedBitmap != null && wrappedBitmap.bitmap != null) {
            wrappedBitmap.lastAccessTime = System.currentTimeMillis();
            Log.d(this.toString(), "["+filePath+"] loaded, update access time and return");
            return wrappedBitmap.bitmap;
        }
        if (imagePool.size() >= imagePoolSize) {
            removeLongNotUsedImage();
        }
        Log.d(this.toString(), "["+filePath+"] not loaded, create new one");
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) {
            Log.e(this.toString(), "Cannot load ["+filePath+"]from disk!!!");
            return null;
        }
        wrappedBitmap = new WrappedBitmap(bitmap);
        imagePool.put(filePath, wrappedBitmap);
        return wrappedBitmap.bitmap;
    }

    @SuppressWarnings("unused")
    public void setImagePoolSize(int imagePoolSize) {
        this.imagePoolSize = imagePoolSize;
    }


}
