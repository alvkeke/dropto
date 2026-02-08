package cn.alvkeke.dropto.mgmt

import android.content.Context
import android.util.Log
import java.io.File

const val TAG: String = "Global"

object Global {
    
    lateinit var attachmentStorage: File
        private set
    lateinit var attachmentCacheShare: File
        private set

    private const val ATTACHMENT_FOLDER_OLD = "imgs"
    private const val ATTACHMENT_FOLDER_NEW = "attachments"
    private val ATTACHMENT_FOLDER_AVAILABLE = ArrayList<String>().apply {
        add(ATTACHMENT_FOLDER_OLD)
        add(ATTACHMENT_FOLDER_NEW)
    }
    private const val ATTACHMENT_FOLDER = ATTACHMENT_FOLDER_OLD
    private const val ATTACHMENT_SHARE_FOLDER = "share"

    private fun File.isValid(): Boolean {
        return this.exists() && this.isDirectory
    }

    fun folderInitAndMigrate(context: Context) {
        attachmentStorage = context.getExternalFilesDir(ATTACHMENT_FOLDER)!!
        Log.e(TAG, "migrating attachment folders to ${attachmentStorage.absolutePath}")

        if (attachmentStorage.exists() && !attachmentStorage.isDirectory) {
            val bakFile = File(
                "${attachmentStorage.absolutePath}.${System.currentTimeMillis()}.bak"
            )
            Log.e(TAG, "attachment folder exist but not a folder, backup it to ${bakFile.name}")
            attachmentStorage.renameTo(bakFile)
        }

        if (!attachmentStorage.exists()) {
            Log.e(TAG, "attachment folder doesn't exist, create: " + attachmentStorage.mkdir())
        }

        Log.e(TAG, "checking other available folders: ${ATTACHMENT_FOLDER_AVAILABLE.size}")
        for (s in ATTACHMENT_FOLDER_AVAILABLE) {
            if (s == ATTACHMENT_FOLDER) continue    // skip current folder

            Log.e(TAG, "checking folder: $s")
            val otherFolder = context.getExternalFilesDir(s) ?: continue
            if (otherFolder.isValid()) {
                val files = otherFolder.listFiles()

                if (files == null || files.isEmpty()) {
                    Log.e(TAG, "folder $s is empty, delete it")
                    otherFolder.delete()
                    continue
                }

                Log.e(TAG, "folder $s exists and valid, move ${files.size} files from it")
                files.forEach { file ->
                    Log.e(TAG, "Move file: ${file.name}")
                    file.renameTo(File(attachmentStorage, file.name))
                }
                otherFolder.delete()
            }
        }

        // init cache folder
        attachmentCacheShare = context.externalCacheDir!!
        attachmentCacheShare = File(attachmentCacheShare, ATTACHMENT_SHARE_FOLDER)
        if (attachmentCacheShare.exists() && !attachmentCacheShare.isDirectory) {
            val bakFile = File(
                "${attachmentCacheShare.absolutePath}.${System.currentTimeMillis()}.bak"
            )
            Log.e(TAG, "share folder exist but not a folder, backup it to ${bakFile.name}")
            attachmentCacheShare.renameTo(bakFile)
        }
        if (!attachmentCacheShare.exists()) {
            Log.i(TAG, "share folder not exist, create: " + attachmentCacheShare.mkdir())
        }
    }

    fun mimeTypeFromFileName(name: String): String {
        val extension = name.substringAfterLast('.', "")
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"
    }

}
