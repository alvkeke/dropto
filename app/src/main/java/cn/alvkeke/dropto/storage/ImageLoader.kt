package cn.alvkeke.dropto.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import cn.alvkeke.dropto.DroptoApplication
import cn.alvkeke.dropto.R
import java.io.File
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.ceil


object ImageLoader {

    const val TAG: String = "ImageLoader"

    fun interface ImageLoadListener {
        fun onImageLoaded(bitmap: Bitmap?)
    }

    private val handler = Handler(Looper.getMainLooper())

    private class WrappedBitmap(var bitmap: Bitmap, var isCut: Boolean) {
        var lastAccessTime: Long = System.currentTimeMillis()
    }

    private val imagePool: HashMap<String, WrappedBitmap> = HashMap<String, WrappedBitmap>()
    private val poolLock: ReadWriteLock = ReentrantReadWriteLock()
    private var imageTimeOut: Long = 60 * 1000 // 60 seconds
    private var imageMaxBytes:Int = 1048576 // 1*1024*1024;

    init {
        val imageTimeoutTimer = Timer(true)
        val imageTimeoutTask: TimerTask = object : TimerTask() {
            override fun run() {
                removeTimeoutImage()
            }
        }
        imageTimeoutTimer.schedule(
            imageTimeoutTask,
            imageTimeOut / 2, imageTimeOut / 2
        )
    }

    lateinit var errorBitmap: Bitmap
        private set

    fun initImageLoader(context: Context) {
        // Load vector drawable and convert to bitmap
        val drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.img_load_error, null)!!
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        errorBitmap = bitmap
    }


    private fun imagePoolGet(key: String): WrappedBitmap? {
        poolLock.readLock().lock()
        val wrappedBitmap: WrappedBitmap? = imagePool[key]
        poolLock.readLock().unlock()
        return wrappedBitmap
    }

    private fun imagePoolRemove(key: String) {
        poolLock.writeLock().lock()
        imagePool.remove(key)
        poolLock.writeLock().unlock()
    }

    private fun imagePoolPut(key: String, wrappedBitmap: WrappedBitmap): WrappedBitmap? {
        poolLock.writeLock().lock()
        val ret = imagePool.putIfAbsent(key, wrappedBitmap)
        poolLock.writeLock().unlock()
        return ret
    }

    private fun removeTimeoutImage() {
        val keys = ArrayList<String>()
        poolLock.readLock().lock()
        for (current in imagePool.entries) {
            val wrappedBitmap: WrappedBitmap = current.value
            val deltaTime = System.currentTimeMillis() - wrappedBitmap.lastAccessTime
            if (deltaTime < imageTimeOut) continue
            keys.add(current.key)
        }
        poolLock.readLock().unlock()

        var needGc = false
        poolLock.writeLock().lock()
        for (key in keys) {
            val wrappedBitmap = imagePool.get(key) ?: continue
            needGc = true
            imagePool.remove(key)
            Log.d( TAG, "[$key] expired, remove, last access: ${wrappedBitmap.lastAccessTime}")
        }
        poolLock.writeLock().unlock()
        if (needGc) Thread { Runtime.getRuntime().gc() }.start()
    }

    private fun setSampleSize(options: BitmapFactory.Options) {

        val byteCount: Int = options.outWidth * options.outHeight * getPixelDeep(options.outConfig)
        if (byteCount <= imageMaxBytes) return

        var divider = byteCount.toFloat() / imageMaxBytes
        divider /= 4f
        options.inSampleSize = ceil(divider.toDouble()).toInt()
        options.inSampleSize++
    }

    private fun loadBitmapWithOption(filePath: String, options: BitmapFactory.Options?): Bitmap? {
        val bitmap = if (options == null)
            BitmapFactory.decodeFile(filePath)
        else
            BitmapFactory.decodeFile(filePath, options)

        if (bitmap == null) return null

        val orientation: Int = getBitmapOrientation(filePath)
        val rotatedBitmap: Bitmap = rotateBitmap(bitmap, orientation)
        if (bitmap != rotatedBitmap) bitmap.recycle()
        return rotatedBitmap
    }

    private fun loadBitmapOriginal(file: File): Bitmap? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        val filePath = file.absolutePath
        return loadBitmapWithOption(filePath, null)
    }

    private fun loadBitmapSample(file: File, options: BitmapFactory.Options): Bitmap? {
        if (!file.exists() || !file.isFile) {
            return null
        }
        val filePath = file.absolutePath
        options.inJustDecodeBounds = true // just check metadata of the image
        BitmapFactory.decodeFile(filePath, options)
        setSampleSize(options)
        options.inJustDecodeBounds = false // really retrieve image from disk
        return loadBitmapWithOption(filePath, options)
    }

    /**
     * put the wrappedBitmap into the pool, and return the exact one in the pool
     * @param filePath filepath of the image
     * @param wrappedBitmap generated wrappedBitmap
     * @return the real wrappedBitmap in the pool
     */
    private fun putWrappedBitmapInPool(
        filePath: String,
        wrappedBitmap: WrappedBitmap
    ): WrappedBitmap {
        val ret = imagePoolPut(filePath, wrappedBitmap)
        if (ret != null && ret !== wrappedBitmap) {
            return ret
        }
        return wrappedBitmap
    }

    private fun getWrappedBitmapInPool(filePath: String): WrappedBitmap? {
        val wrappedBitmap = imagePoolGet(filePath) ?: return null
        if (wrappedBitmap.bitmap.isRecycled) {
            imagePoolRemove(filePath)
            return null
        }
        wrappedBitmap.lastAccessTime = System.currentTimeMillis()
        return wrappedBitmap
    }

    private fun loadWrappedBitmap(file: File): WrappedBitmap? {
        val filePath = file.absolutePath
        var wrappedBitmap = getWrappedBitmapInPool(filePath)
        if (wrappedBitmap != null) {
            Log.d(TAG, "[$filePath] loaded, update access time and return")
            return wrappedBitmap
        }
        Log.d(TAG, "[$filePath] not loaded, create new one")

        val options = BitmapFactory.Options()
        val bitmap = loadBitmapSample(file, options)
        if (bitmap == null) {
            Log.e(TAG, "Cannot load [$filePath] from disk!!!")
            return null
        }
        wrappedBitmap = WrappedBitmap(bitmap, options.inSampleSize > 1)
        val ret = putWrappedBitmapInPool(filePath, wrappedBitmap)
        // FIXME: NOT a good solution, need prevent one image be loaded multiple times
        if (ret !== wrappedBitmap) {
            wrappedBitmap.bitmap.recycle()
        }
        return ret
    }

    @JvmStatic
    fun loadImage(file: File): Bitmap? {
        val wrappedBitmap = loadWrappedBitmap(file) ?: return null
        return wrappedBitmap.bitmap
    }

    @JvmStatic
    @Suppress("unused")
    fun loadImageAsync(file: File, listener: ImageLoadListener) {
        val wrappedBitmap = getWrappedBitmapInPool(file.absolutePath)
        if (wrappedBitmap != null) {
            listener.onImageLoaded(wrappedBitmap.bitmap)
            return
        }
        Thread {
            val bitmap = loadImage(file)
            handler.post { listener.onImageLoaded(bitmap) }
        }.start()
    }

    @JvmStatic
    @Suppress("unused")
    fun loadOriginalImageAsync(file: File, listener: ImageLoadListener) {
        val wrappedBitmap = getWrappedBitmapInPool(file.absolutePath)
        if (wrappedBitmap != null && !wrappedBitmap.isCut) {
            listener.onImageLoaded(wrappedBitmap.bitmap)
            return
        }
        Thread {
            val bitmap = loadBitmapOriginal(file)
            handler.post { listener.onImageLoaded(bitmap) }
        }.start()
    }

    @JvmStatic
    @Suppress("unused")
    fun setImageTimeout(timeoutMs: Int) {
        this.imageTimeOut = timeoutMs.toLong()
    }

    @JvmStatic
    @Suppress("unused")
    fun setImageMaxBytes(imageMaxBytes: Int) {
        this.imageMaxBytes = imageMaxBytes
    }

    private fun getPixelDeep(config: Bitmap.Config): Int {
        when (config) {
            Bitmap.Config.HARDWARE -> {
                Log.i("$TAG:$config", "Got HARDWARE, treat it as 1 byte deep")
                return 1
            }

            Bitmap.Config.ALPHA_8 -> return 1
            Bitmap.Config.RGB_565, Bitmap.Config.ARGB_4444 -> return 2
            Bitmap.Config.RGBA_F16 -> return 8
            Bitmap.Config.RGBA_1010102, Bitmap.Config.ARGB_8888 -> return 4
        }
    }

    private fun getBitmapOrientation(path: String): Int {
        val exif: ExifInterface
        var orientation = 0
        try {
            exif = ExifInterface(path)
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            Log.e("$TAG:getBitmapOrientation", "getBitmapOrientation: $orientation")
        } catch (_: Exception) {
        }
        return orientation
    }

    private fun convertRotate(orientation: Int): Int {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> return 90
            ExifInterface.ORIENTATION_ROTATE_180 -> return 180
            ExifInterface.ORIENTATION_ROTATE_270 -> return 270
        }
        return 0
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(convertRotate(orientation).toFloat())
        return Bitmap.createBitmap(
            bitmap, 0, 0,
            bitmap.width, bitmap.height, matrix, true
        )
    }
}
