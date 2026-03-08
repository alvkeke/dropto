package cn.alvkeke.dropto.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.NoteItem
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.function.Consumer

object FileHelper {

    private const val TAG = "FileHelper"

    private const val BUFFER_SIZE = 1024

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
    private const val ATTACHMENT_FOLDER = ATTACHMENT_FOLDER_NEW
    private const val ATTACHMENT_SHARE_FOLDER = "share"

    private fun sanitizeFileName(rawName: String): String {
        val normalized = rawName.trim()
            .replace('/', '_')
            .replace('\\', '_')
        return normalized.ifBlank { "file_${System.currentTimeMillis()}" }
    }

    private val pathDownload = Environment.DIRECTORY_DOWNLOADS + "/DropTo/"
    private val pathGallery = Environment.DIRECTORY_DCIM + "/DropTo/"

    private fun saveToPathByMediaStore(
        context: Context,
        sourceFile: File,
        path: String,
        displayName: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uniqueName = buildUniqueDisplayName(context, path, displayName)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(downloadsUri, values) ?: return null
        return try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("openOutputStream returned null")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save file to Downloads by MediaStore", e)
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun buildUniqueDisplayName(context: Context, relativePath: String, originalName: String): String {
        val resolver = context.contentResolver
        val baseName = originalName.substringBeforeLast('.', originalName)
        val ext = originalName.substringAfterLast('.', "")
        var index = 0
        while (true) {
            val candidate = if (index == 0) {
                originalName
            } else if (ext.isBlank()) {
                "${baseName}_$index"
            } else {
                "${baseName}_$index.$ext"
            }

            val cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(candidate, relativePath),
                null
            )
            val exists = cursor?.use { it.moveToFirst() } == true
            if (!exists) return candidate
            index++
        }
    }

    fun saveFileToDownload(context: Context, file: File, name: String): Uri? {
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "saveFileToDownload: source file invalid: ${file.absolutePath}")
            return null
        }

        val displayName = sanitizeFileName(name.ifBlank { file.name })
        val mimeType = mimeTypeFromFileName(displayName)

        return saveToPathByMediaStore(
            context,
            file,
            pathDownload,
            displayName,
            mimeType
        )
    }

    fun saveFileToGallery(context: Context, file: File, name: String): Uri? {
        if (!file.exists() || !file.isFile) {
            Log.e(TAG, "saveFileToGallery: source file invalid: ${file.absolutePath}")
            return null
        }

        val displayName = sanitizeFileName(name.ifBlank { file.name })
        val mimeType = mimeTypeFromFileName(displayName)

        return saveToPathByMediaStore(
            context,
            file,
            pathGallery,
            displayName,
            mimeType
        )
    }

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

        Log.e(TAG, "checking available folders: ${ATTACHMENT_FOLDER_AVAILABLE.size}")
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

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b.toInt() and 0xff))
        }
        return sb.toString()
    }

    @Throws(Exception::class)
    fun calculateMD5(fd: FileDescriptor): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(fd).use { fis ->
            fis.channel.position(0)
            val buffer = ByteArray(BUFFER_SIZE)
            var nBytes: Int
            while ((fis.read(buffer).also { nBytes = it }) != -1) {
                md.update(buffer, 0, nBytes)
            }
        }
        return md.digest()
    }

    fun md5ToFile(folder: File, md5: ByteArray): File {
        val name = bytesToHex(md5)
        return File(folder, name)
    }

    @Throws(IOException::class)
    fun copyFileTo(fd: FileDescriptor, dest: File, fileName: String) {
        val nameOnly = dest.isFile && dest.exists()

        if (!nameOnly) {
            val fis = FileInputStream(fd)
            val srcChannel = fis.channel.position(0)
            val fos = FileOutputStream(dest)
            val destChannel = fos.channel
            // do data copy
            destChannel.transferFrom(srcChannel, 0, srcChannel.size())
            srcChannel.close()
            destChannel.close()
            fis.close()
            fos.close()
        }

        val nameFile = File(dest.parentFile, dest.name + ".names")
        if (nameFile.exists()) {
            val existingName = nameFile.readLines()
            if (existingName.contains(fileName)) {
                return
            }
        }
        nameFile.appendText("$fileName\n")
    }

    const val UNKNOWN_FILE_NAME = "file.name.unknown"
    const val FILE_NAME_SUFFIX = ".names"
    @JvmStatic
    @Suppress("unused")
    fun getGoodFileNameFromMd5File(md5File: File): String? {
        val nameFile = File(md5File.parentFile, md5File.name + FILE_NAME_SUFFIX)
        if (!nameFile.exists()) {
            return null
        }
        val names = nameFile.readLines()
            .filter { it.isNotBlank() }.filter { it != UNKNOWN_FILE_NAME }
        return if (names.isNotEmpty()) names[0] else UNKNOWN_FILE_NAME
    }

    @JvmStatic
    @Suppress("unused")
    fun getAllFileNamesFromMd5File(md5File: File): List<String>? {
        val nameFile = File(md5File.parentFile, md5File.name + FILE_NAME_SUFFIX)
        if (!nameFile.exists()) {
            return null
        }
        return nameFile.readLines()
    }

    @JvmStatic
    fun getFileNameFromUri(context: Context, uri: Uri): String {
        // ContentResolver to resolve the content Uri
        val resolver = context.contentResolver
        // Query the file name from the content Uri
        val cursor = resolver.query(uri, null, null, null, null)
        var fileName: String? = null
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index != -1) {
                fileName = cursor.getString(index)
            }
            cursor.close()
        }
        return fileName ?: UNKNOWN_FILE_NAME
    }

    @JvmStatic
    fun saveUriToFile(context: Context, uri: Uri, storeFolder: File): File? {

        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { inputPFD ->
                if (inputPFD == null) {
                    Log.e(TAG, "saveUriToFile: failed to open uri: $uri")
                    return null
                }
                val fileName = getFileNameFromUri(context, uri)
                val fd = inputPFD.fileDescriptor
                val md5sum = calculateMD5(fd)
                val retFile = md5ToFile(storeFolder, md5sum)
                copyFileTo(fd, retFile, fileName)
                return retFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveUriToFile: failed to save uri to file: $uri", e)
            return null
        }
    }

    private fun generateShareFile(imageFile: AttachmentFile): File? {
        try {
            val imageName = imageFile.name
            val fileToShare: File?
            if (imageName.isEmpty()) {
                fileToShare = imageFile.md5file
            } else {
                fileToShare = File(attachmentCacheShare, imageName)
                imageFile.md5file.copyTo(fileToShare, true)
            }
            return fileToShare
        } catch (e: IOException) {
            Log.e(this.toString(), "Failed to copy file: $e")
            return null
        }
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", file
        )
    }

    fun generateShareFile(
        context: Context,
        attachment: AttachmentFile,
    ) : Uri {
        val ff = generateShareFile(attachment)
        val uri = getUriForFile(context, ff!!)
        return uri
    }

    fun generateShareFiles(
        context: Context,
        note: NoteItem,
        uris: ArrayList<Uri>
    ) {
        note.attachments.iterator().forEachRemaining(Consumer { f: AttachmentFile ->
            val uri = generateShareFile(context, f)
            uris.add(uri)
        })
    }

    fun emptyShareFolder() {
        val files = attachmentCacheShare.listFiles() ?: return
        for (file in files) {
            if (!file.isDirectory) {
                val ret = file.delete()
                Log.d("emptyFolder", "file delete result: $ret")
            }
        }
    }

}
