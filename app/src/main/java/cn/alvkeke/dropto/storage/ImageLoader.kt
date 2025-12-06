package cn.alvkeke.dropto.storage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ImageLoader {

    public interface ImageLoadListener {
        void onImageLoaded(Bitmap bitmap);
    }

    private static final ImageLoader instance = new ImageLoader();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageLoader() {
        Timer imageTimeoutTimer = new Timer(true);
        TimerTask imageTimeoutTask = new TimerTask() {
            @Override
            public void run() {
                removeTimeoutImage();
            }
        };
        imageTimeoutTimer.scheduleAtFixedRate(imageTimeoutTask,
                imageTimeOut/2, imageTimeOut/2);
    }

    public static ImageLoader getInstance() {
        return instance;
    }

    private static class WrappedBitmap {
        Bitmap bitmap;
        long lastAccessTime;
        boolean isCut;
        public WrappedBitmap(Bitmap bitmap, boolean isCut) {
            this.bitmap = bitmap;
            this.isCut = isCut;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
    private final HashMap<String, WrappedBitmap> imagePool = new HashMap<>();
    private final ReadWriteLock poolLock = new ReentrantReadWriteLock();
    private long imageTimeOut = 60*1000;    // 60 seconds
    private int imageMaxBytes = 1048576;  // 1*1024*1024;

    private WrappedBitmap imagePoolGet(String key) {
        WrappedBitmap wrappedBitmap;
        poolLock.readLock().lock();
        wrappedBitmap = imagePool.get(key);
        poolLock.readLock().unlock();
        return wrappedBitmap;
    }

    private void imagePoolRemove(String key) {
        poolLock.writeLock().lock();
        imagePool.remove(key);
        poolLock.writeLock().unlock();
    }

    private WrappedBitmap imagePoolPut(String key, WrappedBitmap wrappedBitmap) {
        poolLock.writeLock().lock();
        WrappedBitmap ret = imagePool.putIfAbsent(key, wrappedBitmap);
        poolLock.writeLock().unlock();
        return ret;
    }

    private void removeTimeoutImage() {
        ArrayList<String> keys = new ArrayList<>();
        poolLock.readLock().lock();
        for (Map.Entry<String, WrappedBitmap> current : imagePool.entrySet()) {
            WrappedBitmap wrappedBitmap = current.getValue();
            long deltaTime = System.currentTimeMillis() - wrappedBitmap.lastAccessTime;
            if (deltaTime < imageTimeOut)
                continue;
            keys.add(current.getKey());
        }
        poolLock.readLock().unlock();

        boolean needGc = false;
        poolLock.writeLock().lock();
        for (String key : keys) {
            WrappedBitmap wrappedBitmap = imagePool.get(key);
            if (wrappedBitmap == null) continue;
            needGc = true;
            imagePool.remove(key);
            Log.d(this.toString(), "[" + key +
                    "] expired, remove, last access: " + wrappedBitmap.lastAccessTime);
        }
        poolLock.writeLock().unlock();
        if (needGc)
            new Thread(() -> Runtime.getRuntime().gc()).start();
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
    private void setSampleSize(BitmapFactory.Options options) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        int byteCount = options.outWidth * options.outHeight * getPixelDeep(options.outConfig);
        if (byteCount <= imageMaxBytes) return;

        float divider = (float) byteCount / imageMaxBytes;
        divider /= 4;
        options.inSampleSize = (int) Math.ceil(divider);
        options.inSampleSize++;
    }

    private static int getBitmapOrientation(String path){
        ExifInterface exif;
        int orientation = 0;
        try {
            exif = new ExifInterface(path);
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            Log.e("getBitmapOrientation", "getBitmapOrientation: "+orientation );
        } catch (Exception ignore) { }
        return orientation;
    }
    private static int convertRotate(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
        }
        return 0;
    }
    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(convertRotate(orientation));
        return Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private Bitmap loadBitmapWithOption(String filePath, BitmapFactory.Options options) {
        Bitmap bitmap;
        if (options == null)
            bitmap = BitmapFactory.decodeFile(filePath);
        else
            bitmap = BitmapFactory.decodeFile(filePath, options);
        if (bitmap == null)
            return null;
        int orientation = getBitmapOrientation(filePath);
        Bitmap rotatedBitmap = rotateBitmap(bitmap, orientation);
        if (bitmap != rotatedBitmap)
            bitmap.recycle();
        return rotatedBitmap;
    }

    private Bitmap loadBitmapOriginal(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        String filePath = file.getAbsolutePath();
        return loadBitmapWithOption(filePath, null);
    }

    private Bitmap loadBitmapSample(File file, BitmapFactory.Options options) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        String filePath = file.getAbsolutePath();
        options.inJustDecodeBounds = true;  // just check metadata of the image
        BitmapFactory.decodeFile(filePath, options);
        setSampleSize(options);
        options.inJustDecodeBounds = false; // really retrieve image from disk
        return loadBitmapWithOption(filePath, options);
    }

    /**
     * put the wrappedBitmap into the pool, and return the exact one in the pool
     * @param filePath filepath of the image
     * @param wrappedBitmap generated wrappedBitmap
     * @return the real wrappedBitmap in the pool
     */
    private WrappedBitmap putWrappedBitmapInPool(String filePath, WrappedBitmap wrappedBitmap) {
        WrappedBitmap ret = imagePoolPut(filePath, wrappedBitmap);
        if (ret != null && ret != wrappedBitmap) {
            return ret;
        }
        return wrappedBitmap;
    }

    private WrappedBitmap getWrappedBitmapInPool(String filePath) {
        WrappedBitmap wrappedBitmap = imagePoolGet(filePath);
        if (wrappedBitmap == null) return null;
        if (wrappedBitmap.bitmap.isRecycled()) {
            imagePoolRemove(filePath);
            return null;
        }
        wrappedBitmap.lastAccessTime = System.currentTimeMillis();
        return wrappedBitmap;
    }

    private WrappedBitmap loadWrappedBitmap(File file) {
        String filePath = file.getAbsolutePath();
        WrappedBitmap wrappedBitmap = getWrappedBitmapInPool(filePath);
        if (wrappedBitmap != null) {
            Log.d(this.toString(), "["+filePath+"] loaded, update access time and return");
            return wrappedBitmap;
        }
        Log.d(this.toString(), "["+filePath+"] not loaded, create new one");

        BitmapFactory.Options options = new BitmapFactory.Options();
        Bitmap bitmap = loadBitmapSample(file, options);
        if (bitmap == null) {
            Log.e(this.toString(), "Cannot load ["+filePath+"]from disk!!!");
            return null;
        }
        wrappedBitmap = new WrappedBitmap(bitmap, options.inSampleSize > 1);
        WrappedBitmap ret = putWrappedBitmapInPool(filePath, wrappedBitmap);
        // FIXME: NOT a good solution, need prevent one image be loaded multiple times
        if (ret != wrappedBitmap) {
            wrappedBitmap.bitmap.recycle();
        }
        return ret;
    }

    public Bitmap loadImage(File file) {
        WrappedBitmap wrappedBitmap = loadWrappedBitmap(file);
        if (wrappedBitmap == null) return null;
        return wrappedBitmap.bitmap;
    }

    @SuppressWarnings("unused")
    public void loadImageAsync(File file, ImageLoadListener listener) {
        WrappedBitmap wrappedBitmap = getWrappedBitmapInPool(file.getAbsolutePath());
        if (wrappedBitmap !=null) {
            listener.onImageLoaded(wrappedBitmap.bitmap);
            return;
        }
        new Thread(() -> {
            Bitmap bitmap = loadImage(file);
            handler.post(() -> listener.onImageLoaded(bitmap));
        }).start();
    }

    @SuppressWarnings("unused")
    public void loadOriginalImageAsync(File file, ImageLoadListener listener) {
        WrappedBitmap wrappedBitmap = getWrappedBitmapInPool(file.getAbsolutePath());
        if (wrappedBitmap != null && !wrappedBitmap.isCut){
            listener.onImageLoaded(wrappedBitmap.bitmap);
            return;
        }
        new Thread(() -> {
            Bitmap bitmap = loadBitmapOriginal(file);
            handler.post(() -> listener.onImageLoaded(bitmap));
        }).start();
    }

    @SuppressWarnings("unused")
    public void setImageTimeout(int timeout_ms) {
        this.imageTimeOut = timeout_ms;
    }

    @SuppressWarnings("unused")
    public void setImageMaxBytes(int imageMaxBytes) {
        this.imageMaxBytes = imageMaxBytes;
    }

}
