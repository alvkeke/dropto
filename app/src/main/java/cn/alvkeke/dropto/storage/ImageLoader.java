package cn.alvkeke.dropto.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ImageLoader {

    public interface ImageLoadListener {
        void onImageLoaded(Bitmap bitmap);
    }

    private static final ImageLoader instance = new ImageLoader();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageLoader() {

    }

    public static ImageLoader getInstance() {
        return instance;
    }

    private static class WrappedBitmap {
        Bitmap bitmap;
        Bitmap previewBitmap = null;
        long lastAccessTime;
        boolean isCut;
        public WrappedBitmap(Bitmap bitmap, boolean isCut) {
            this.bitmap = bitmap;
            this.isCut = isCut;
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

    private static int getPixelDeep(Bitmap.Config config) {
        switch (config) {
            case HARDWARE:
                Log.i("ImageLoader:"+config, "Got HARDWARE, treat it as 1 byte deep");
            case ALPHA_8:
                return 1;
            case RGB_565:
            case ARGB_4444:
                return 2;
            case RGBA_F16:
                return 8;
            case RGBA_1010102:
            case ARGB_8888:
            default:
                return 4;
        }
    }
    private static final int POOL_BITMAP_MAX_BYTES = 1024*1024*4;    // 4MB
    private void setSampleSize(BitmapFactory.Options options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        int byteCount = options.outWidth * options.outHeight * getPixelDeep(options.outConfig);
        if (byteCount <= POOL_BITMAP_MAX_BYTES) return;

        float divider = (float) byteCount / POOL_BITMAP_MAX_BYTES;
        divider /= 4;
        options.inSampleSize = (int) Math.ceil(divider);
        options.inSampleSize++;
    }

    private WrappedBitmap getWrappedBitmap(String filePath) {
        WrappedBitmap wrappedBitmap = imagePool.get(filePath);
        if (wrappedBitmap == null) return null;
        wrappedBitmap.lastAccessTime = System.currentTimeMillis();
        return wrappedBitmap;
    }

    private void putWrappedBitmap(String filePath, WrappedBitmap wrappedBitmap) {
        imagePool.put(filePath, wrappedBitmap);
    }

    private boolean isPoolFull() {
        return imagePool.size() >= imagePoolSize;
    }

    private WrappedBitmap loadWrappedBitmap(String filePath) {
        WrappedBitmap wrappedBitmap = getWrappedBitmap(filePath);
        if (wrappedBitmap != null) {
            Log.d(this.toString(), "["+filePath+"] loaded, update access time and return");
            return wrappedBitmap;
        }
        if (isPoolFull()) {
            removeLongNotUsedImage();
        }
        Log.d(this.toString(), "["+filePath+"] not loaded, create new one");

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;  // just check metadata of the image
        BitmapFactory.decodeFile(filePath, options);
        setSampleSize(options);
        options.inJustDecodeBounds = false; // really retrieve image from disk
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        if (bitmap == null) {
            Log.e(this.toString(), "Cannot load ["+filePath+"]from disk!!!");
            return null;
        }
        Log.e(this.toString(), "["+filePath+"] result byte count: " + bitmap.getByteCount());
        wrappedBitmap = new WrappedBitmap(bitmap, options.inSampleSize>1);
        putWrappedBitmap(filePath, wrappedBitmap);
        return wrappedBitmap;
    }

    private WrappedBitmap loadWrappedBitmap(File file) {
        return loadWrappedBitmap(file.getAbsolutePath());
    }

    public Bitmap loadImage(File file) {
        return loadWrappedBitmap(file).bitmap;
    }

    @SuppressWarnings("unused")
    public void loadImageAsync(File file, ImageLoadListener listener) {
        WrappedBitmap wrappedBitmap = getWrappedBitmap(file.getAbsolutePath());
        if (wrappedBitmap !=null) {
            listener.onImageLoaded(wrappedBitmap.bitmap);
            return;
        }
        new Thread(() -> {
            Bitmap bitmap = loadImage(file);
            handler.post(() -> listener.onImageLoaded(bitmap));
        }).start();
    }

    private static final double PREVIEW_MAX_SIZE = 200*1024; // 200KB
    private Bitmap resizeBitmap(Bitmap origin) {
        int byteCount = origin.getByteCount();
        if (byteCount < PREVIEW_MAX_SIZE) {
            return origin;  // just return origin bitmap since it no need to compress
        }

        double scaleFactor = Math.sqrt(PREVIEW_MAX_SIZE / byteCount);
        Matrix matrix = new Matrix();
        matrix.postScale((float) scaleFactor, (float) scaleFactor);
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
    public void loadPreviewImageAsync(File file, ImageLoadListener listener) {
        WrappedBitmap wrappedBitmap = getWrappedBitmap(file.getAbsolutePath());
        if (wrappedBitmap !=null && wrappedBitmap.previewBitmap != null) {
            listener.onImageLoaded(wrappedBitmap.previewBitmap);
            return;
        }
        new Thread(() -> {
            Bitmap preview = loadPreviewImage(file);
            handler.post(() -> listener.onImageLoaded(preview));
        }).start();
    }

    @SuppressWarnings("unused")
    public void loadOriginalImageAsync(File file, ImageLoadListener listener) {
        WrappedBitmap wrappedBitmap = getWrappedBitmap(file.getAbsolutePath());
        if (wrappedBitmap != null && !wrappedBitmap.isCut){
            listener.onImageLoaded(wrappedBitmap.bitmap);
            return;
        }
        new Thread(() -> {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            handler.post(() -> listener.onImageLoaded(bitmap));
        }).start();
    }

    @SuppressWarnings("unused")
    public void setImagePoolSize(int imagePoolSize) {
        this.imagePoolSize = imagePoolSize;
    }


}
