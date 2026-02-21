package cn.alvkeke.dropto.debug

import android.content.Context
import cn.alvkeke.dropto.BuildConfig
import cn.alvkeke.dropto.R.raw
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.AttachmentFile.Type
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.DataBaseHelper
import cn.alvkeke.dropto.storage.insertCategory
import cn.alvkeke.dropto.storage.insertNote
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Random

object DebugFunction {
    @Suppress("unused")
    const val LOG_TAG: String = "DebugFunction"

    @Suppress("UNUSED_PARAMETER")
    private fun dbgLog(log: String) {
//        if (BuildConfig.DEBUG) android.util.Log.e(LOG_TAG, ignored);
    }

    fun extractRawFile(context: Context, id: Int, oFile: File): Boolean {
        if (!BuildConfig.DEBUG) return false
        dbgLog("Perform Debug function to extract res file")

        if (oFile.exists()) {
            // file exist, return true to indicate can be load
            dbgLog("file exist, don't extract:$oFile")
            return true
        }
        val buffer = ByteArray(1024)
        try {
            val `is` = context.resources.openRawResource(id)
            val os = Files.newOutputStream(oFile.toPath())
            var len: Int
            while ((`is`.read(buffer).also { len = it }) > 0) {
                os.write(buffer, 0, len)
            }
            os.flush()
            os.close()
            `is`.close()
        } catch (_: IOException) {
            dbgLog(
                "Failed to extract res: " +
                        context.resources.getResourceEntryName(id) + " to " + oFile
            )
            return false
        }
        return true
    }

    @JvmStatic
    fun tryExtractResImages(context: Context, folder: File): MutableList<File>? {
        if (!BuildConfig.DEBUG) return null
        dbgLog("Perform Debug function to extract images")

        val rawIds: MutableList<Int> = ArrayList()
        val fields = raw::class.java.fields
        for (f in fields) {
            if (f.type == Int::class.javaPrimitiveType) {
                try {
                    val id = f.getInt(null)
                    rawIds.add(id)
                } catch (_: IllegalAccessException) {
                    dbgLog("failed to get resource ID of raw:$f")
                }
            }
        }

        val retFiles: MutableList<File> = ArrayList()
        for (id in rawIds) {
            val oFile = File(folder, context.resources.getResourceEntryName(id) + ".png")
            if (extractRawFile(context, id, oFile)) retFiles.add(oFile)
        }

        return retFiles
    }


    /**
     * fill category database for debugging, this function will be exec only in DEBUG build
     * @param context context
     */
    @Suppress("unused")
    fun fillDatabaseForCategory(context: Context) {
        if (!BuildConfig.DEBUG) return
        dbgLog("Perform Debug function to fill database for categories")
        try {
            DataBaseHelper(context).writableDatabase.use { db ->
                // fix the category id, make sure it will not create multiple times
                db.insertCategory(1, "Local(Debug)", Category.Type.LOCAL_CATEGORY, "")
                db.insertCategory(2, "REMOTE USERS", Category.Type.REMOTE_USERS, "")
                db.insertCategory(3, "REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV, "")
            }
        } catch (_: Exception) {
            dbgLog("Failed to perform debug database filling")
        }
    }

    @Suppress("unused")
    fun fillDatabaseForNote(context: Context, imgFiles: MutableList<File>, categoryId: Long) {
        if (!BuildConfig.DEBUG) return
        dbgLog("Perform Debug function to fill database for noteItems")

        try {
            DataBaseHelper(context).writableDatabase.use { db ->
                val r = Random()
                var idx = 0
                for (i in 0..14) {
                    val e = NoteItem("ITEM$i$i", System.currentTimeMillis())
                    e.categoryId = categoryId
                    if (r.nextBoolean()) {
                        e.isEdited = true
                    }
                    if (idx < imgFiles.size && r.nextBoolean()) {
                        val imgFile = imgFiles[idx]
                        idx++
                        if (imgFile.exists()) {
                            dbgLog("add image file: $imgFile")
                            val imageFile = from(imgFile, "", Type.MEDIA)
                            e.attachments.add(imageFile)
                        } else {
                            dbgLog("add image file failed, not exist: $imgFile")
                        }
                    }
                    e.id = (i + 1).toLong()
                    db.insertNote(e)
                }
            }
        } catch (_: Exception) {
            dbgLog("Failed to perform debug database filling for note")
        }
    }
}
