package cn.alvkeke.dropto.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

object FileHelper {
    private const val BUFFER_SIZE = 1024

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
    fun copyFileTo(fd: FileDescriptor, dest: File) {
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

    @JvmStatic
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
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
        return fileName
    }

    @JvmStatic
    fun saveUriToFile(context: Context, uri: Uri, storeFolder: File): File? {

        try {
            context.contentResolver.openFileDescriptor(uri, "r").use { inputPFD ->
                if (inputPFD == null) {
                    Log.e(TAG, "saveUriToFile: failed to open uri: $uri")
                    return null
                }
                val fd = inputPFD.fileDescriptor
                val md5sum = calculateMD5(fd)
                val retFile = md5ToFile(storeFolder, md5sum)
                if (!(retFile.isFile && retFile.exists())) {
                    copyFileTo(fd, retFile)
                }
                return retFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveUriToFile: failed to save uri to file: $uri", e)
            return null
        }
    }
}
