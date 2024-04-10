package cn.alvkeke.dropto.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
        Bitmap previewBitmap = null;
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

    private WrappedBitmap loadWrappedBitmap(String filePath) {
        WrappedBitmap wrappedBitmap = imagePool.get(filePath);
        if (wrappedBitmap != null) {
            wrappedBitmap.lastAccessTime = System.currentTimeMillis();
            Log.d(this.toString(), "["+filePath+"] loaded, update access time and return");
            return wrappedBitmap;
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
        return wrappedBitmap;
    }

    private WrappedBitmap loadWrappedBitmap(File file) {
        return loadWrappedBitmap(file.getAbsolutePath());
    }

    public Bitmap loadImage(File file) {
        return loadWrappedBitmap(file).bitmap;
    }

    private static final float COMPRESS_MAX_HEIGHT = 600;
    private static final float COMPRESS_MAX_WIDTH = 600;
    private Bitmap resizeBitmap(Bitmap origin) {
        if (origin.getWidth() < COMPRESS_MAX_WIDTH && origin.getHeight() < COMPRESS_MAX_HEIGHT) {
            return origin;  // just return origin bitmap since it no need to compress
        }
        float scaleWidth = COMPRESS_MAX_WIDTH / origin.getWidth();
        float scaleHeight = COMPRESS_MAX_HEIGHT / origin.getHeight();
        float scaleFactor = Math.min(scaleWidth, scaleHeight);
        Matrix matrix = new Matrix();
        matrix.postScale(scaleFactor, scaleFactor);

        return Bitmap.createBitmap(origin, 0, 0,
                origin.getWidth(), origin.getHeight(), matrix, false);
    }

    public Bitmap loadPreviewImage(File file) {
        WrappedBitmap wrappedBitmap = loadWrappedBitmap(file);
        if (wrappedBitmap.previewBitmap != null) {
            return wrappedBitmap.previewBitmap;
        }
        wrappedBitmap.previewBitmap = resizeBitmap(wrappedBitmap.bitmap);
        return wrappedBitmap.previewBitmap;
    }

    @SuppressWarnings("unused")
    public void setImagePoolSize(int imagePoolSize) {
        this.imagePoolSize = imagePoolSize;
    }


}
