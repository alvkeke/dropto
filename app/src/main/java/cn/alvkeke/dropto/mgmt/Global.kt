package cn.alvkeke.dropto.mgmt

import android.content.Context
import android.util.Log
import java.io.File

const val TAG: String = "Global"

object Global {
    private lateinit var imageStorage: File
    private lateinit var imageCacheShare: File

    @JvmStatic
    fun getFolderImage(context: Context): File {
        if (!::imageStorage.isInitialized) {
            imageStorage = context.getExternalFilesDir("imgs")!!
        }

        if (!imageStorage.exists()) {
            Log.i(TAG, "image folder not exist, create: " + imageStorage.mkdir())
        }
        return imageStorage
    }

    @JvmStatic
    fun getFolderImageShare(context: Context): File {
        if (!::imageCacheShare.isInitialized) {
            imageCacheShare = context.externalCacheDir!!
            imageCacheShare = File(imageCacheShare, "share")
        }

        if (!imageCacheShare.exists()) {
            Log.i(TAG, "share folder not exist, create: " + imageCacheShare.mkdir())
        }
        return imageCacheShare
    }

    fun mimeTypeFromFileName(name: String): String {
        val extension = name.substringAfterLast('.', "")
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }

}
