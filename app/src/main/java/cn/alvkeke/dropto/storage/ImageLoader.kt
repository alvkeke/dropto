package cn.alvkeke.dropto.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import cn.alvkeke.dropto.R
import cn.alvkeke.dropto.data.LockedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Timer
import java.util.TimerTask
import kotlin.math.ceil


object ImageLoader {

    const val TAG: String = "ImageLoader"

    private val imageLoaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun interface ImageLoadListener {
        fun onImageLoaded(bitmap: Bitmap?)
    }

    private val handler = Handler(Looper.getMainLooper())

    private class WrappedBitmap(var bitmap: Bitmap, var isCut: Boolean) {
        var lastAccessTime: Long = System.currentTimeMillis()
    }

    private val imagePool: LockedHashMap<String, WrappedBitmap> = LockedHashMap()
    private val videoPool: LockedHashMap<String, WrappedBitmap> = LockedHashMap()

    private var imageTimeOut: Long = 5 * 60 * 1000 // 60 seconds
    private var imageMaxBytes:Int = 1048576 // 1*1024*1024;

    init {
        val imageTimeoutTimer = Timer(true)
        val imageTimeoutTask: TimerTask = object : TimerTask() {
            override fun run() {
                if  (
                    imagePool.removeTimeoutCache() || videoPool.removeTimeoutCache()
                    ) {
                    Runtime.getRuntime().gc()
                }
            }
        }
        imageTimeoutTimer.schedule(
            imageTimeoutTask,
            imageTimeOut / 2, imageTimeOut / 2
        )
    }

    lateinit var errorBitmap: Bitmap
        private set
    lateinit var loadingBitmap: Bitmap
        private set
    lateinit var iconFile: Bitmap
        private set
    lateinit var iconVideoPlay: Bitmap
        private set
    lateinit var iconMore: Bitmap
        private set
    lateinit var iconUnsynced: Bitmap
        private set
    lateinit var iconDeleted: Bitmap
        private set
    lateinit var iconEdited: Bitmap
        private set

    fun initImageLoader(context: Context) {
        // Load vector drawable and convert to bitmap
        var drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.img_load_error, null)!!
        var bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        var canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        errorBitmap = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_not_sync, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconUnsynced = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_remove, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconDeleted = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_edit, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconEdited = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.img_loading, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        loadingBitmap = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_file, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconFile = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_video_play, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconVideoPlay = bitmap

        drawable = ResourcesCompat.getDrawable(context.resources, R.drawable.icon_common_more, null)!!
        bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        iconMore = bitmap
    }

    private fun LockedHashMap<String, WrappedBitmap>.removeTimeoutCache(): Boolean {
        if (this.isEmpty) return false

        return this.filter { (_, value) ->
            val deltaTime = System.currentTimeMillis() - value.lastAccessTime
            deltaTime >= imageTimeOut
        }.map { it.key }.let { entries ->
            this.removeAll(entries)
        }
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
        if (options.outConfig == null) {
            Log.d(TAG, "Cannot decode image config of [$filePath]")
            return null
        }
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
    private fun LockedHashMap<String, WrappedBitmap>.putWrappedBitmapInPool(
        filePath: String,
        wrappedBitmap: WrappedBitmap
    ): WrappedBitmap {
        val ret = this.putIfAbsent(filePath, wrappedBitmap)
        if (ret != null && ret !== wrappedBitmap) {
            return ret
        }
        return wrappedBitmap
    }

    private fun LockedHashMap<String, WrappedBitmap>.getWrappedBitmapInPool(
        filePath: String
    ): WrappedBitmap? {
        val wrappedBitmap = this[filePath] ?: return null
        if (wrappedBitmap.bitmap.isRecycled) {
            this.remove(filePath)
            return null
        }
        wrappedBitmap.lastAccessTime = System.currentTimeMillis()
        return wrappedBitmap
    }

    private fun LockedHashMap<String, WrappedBitmap>.loadWrappedBitmap(
        file: File,
        missCallback: (() -> WrappedBitmap?)
    ): WrappedBitmap? {
        val filePath = file.absolutePath
        val wrappedBitmap = this.getWrappedBitmapInPool(filePath)
        if (wrappedBitmap != null) {
            Log.d(TAG, "[$filePath] loaded, update access time and return")
            return wrappedBitmap
        }
        Log.d(TAG, "[$filePath] not loaded, call missCallback to load it")

        return missCallback()
    }

    @JvmStatic
    fun loadImage(file: File): Bitmap? {
        return imagePool.loadWrappedBitmap(file) {
            val filePath = file.absolutePath
            val options = BitmapFactory.Options()
            val bitmap = loadBitmapSample(file, options)
            if (bitmap == null) {
                Log.e(TAG, "Cannot load [$filePath] from disk!!!")
                return@loadWrappedBitmap null
            }
            val wbm = WrappedBitmap(bitmap, options.inSampleSize > 1)
            val ret = imagePool.putWrappedBitmapInPool(filePath, wbm)
            // FIXME: NOT a good solution, need prevent one image be loaded multiple times
            if (ret !== wbm) {
                wbm.bitmap.recycle()
            }
            ret
        }?.bitmap
    }

    /**
     * load image asynchronously, if the image is already in pool, return it directly
     * @param file image file
     * @param alwaysCallback whether always call the callback listener, even the image is already in pool
     * @param listener callback listener
     * @return the bitmap if it is already in pool, null otherwise
     */
    @JvmStatic
    @Suppress("unused")
    fun loadImageAsync(
        file: File,
        alwaysCallback: Boolean = true,
        listener: ImageLoadListener?
    ): Bitmap? {
        val wrappedBitmap = imagePool.getWrappedBitmapInPool(file.absolutePath)
        if (wrappedBitmap != null) {
            if (alwaysCallback)
                listener?.onImageLoaded(wrappedBitmap.bitmap)
            return wrappedBitmap.bitmap
        }
        imageLoaderScope.launch {
            val bitmap = loadImage(file)
            handler.post { listener?.onImageLoaded(bitmap) }
        }
        return null
    }

    @JvmStatic
    @Suppress("unused")
    fun loadOriginalImageAsync(file: File, listener: ImageLoadListener) {
        val wrappedBitmap = imagePool.getWrappedBitmapInPool(file.absolutePath)
        if (wrappedBitmap != null && !wrappedBitmap.isCut) {
            listener.onImageLoaded(wrappedBitmap.bitmap)
            return
        }
        imageLoaderScope.launch {
            val bitmap = loadBitmapOriginal(file)
            handler.post { listener.onImageLoaded(bitmap) }
        }
    }

    @JvmStatic
    fun loadVideoThumbnail(file: File): Bitmap? {
        return videoPool.loadWrappedBitmap(file) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val bitmap = retriever.frameAtTime
                if (bitmap == null) {
                    Log.e(TAG, "Cannot load video thumb from disk!!!")
                    return@loadWrappedBitmap null
                }
                val wbm = WrappedBitmap(bitmap, false)
                val ret = videoPool.putWrappedBitmapInPool(
                    file.absolutePath,
                    wbm
                )
                // FIXME: NOT a good solution, need prevent one image be loaded multiple times
                if (ret !== wbm) {
                    wbm.bitmap.recycle()
                }
                ret
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get video thumbnail: ${e.message}")
                null
            } finally {
                retriever.release()
            }
        }?.bitmap
    }

    @JvmStatic
    fun loadVideoThumbnailAsync(
        file: File,
        alwaysCallback: Boolean = true,
        listener: ImageLoadListener?
    ): Bitmap? {
        val wrappedBitmap = videoPool.getWrappedBitmapInPool(file.absolutePath)
        if (wrappedBitmap != null) {
            if (alwaysCallback)
                listener?.onImageLoaded(wrappedBitmap.bitmap)
            return wrappedBitmap.bitmap
        }

        imageLoaderScope.launch {
            val bitmap = loadVideoThumbnail(file)
            handler.post { listener?.onImageLoaded(bitmap) }
        }
        return null
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
            Log.v("$TAG:getBitmapOrientation", "getBitmapOrientation: $orientation")
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
