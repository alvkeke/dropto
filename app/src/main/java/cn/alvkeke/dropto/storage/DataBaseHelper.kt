package cn.alvkeke.dropto.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import android.util.Log
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global.getFolderImage


class DataBaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    fun interface IterateCallback<T> {
        fun onIterate(o: T)
    }

    private lateinit var db: SQLiteDatabase

    override fun onCreate(db: SQLiteDatabase) {
        createCategoryTable(db)
        createNoteTable(db)
    }

    override fun onUpgrade(sqLiteDatabase: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    private fun createCategoryTable(db: SQLiteDatabase) {
        db.execSQL(
            String.format(
                "CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, %s %s, %s %s)",
                TABLE_CATEGORY, CATEGORY_COLUMN_ID, CATEGORY_COLUMN_ID_TYPE,
                CATEGORY_COLUMN_TYPE, CATEGORY_COLUMN_TYPE_TYPE,
                CATEGORY_COLUMN_NAME, CATEGORY_COLUMN_NAME_TYPE,
                CATEGORY_COLUMN_PREVIEW, CATEGORY_COLUMN_PREVIEW_TYPE
            )
        )
    }

    private fun createNoteTable(db: SQLiteDatabase) {
        db.execSQL(
            String.format(
                "CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, %s %s, %s %s, %s %s)",
                TABLE_NOTE, NOTE_COLUMN_ID, NOTE_COLUMN_ID_TYPE,
                NOTE_COLUMN_CATE_ID, NOTE_COLUMN_CATE_ID_TYPE,
                NOTE_COLUMN_TEXT, NOTE_COLUMN_TEXT_TYPE,
                NOTE_COLUMN_C_TIME, NOTE_COLUMN_C_TIME_TYPE,
                NOTE_COLUMN_ATTACHMENT_INFO, NOTE_COLUMN_IMG_INFO_TYPE
            )
        )
    }

    @Suppress("unused")
    fun destroyDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    fun start() {
        if (::db.isInitialized) return
        db = this.writableDatabase
    }

    fun finish() {
        db.close()
    }

    @Throws(SQLiteException::class)
    fun insertCategory(id: Long, title: String, type: Category.Type, preview: String?): Long {
        val values = ContentValues()
        values.put(CATEGORY_COLUMN_ID, id)
        values.put(CATEGORY_COLUMN_NAME, title)
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
        values.put(CATEGORY_COLUMN_PREVIEW, preview)

        return db.insertOrThrow(TABLE_CATEGORY, null, values)
    }

    @Throws(SQLiteException::class)
    fun insertCategory(title: String, type: Category.Type, preview: String?): Long {
        val values = ContentValues()
        values.put(CATEGORY_COLUMN_NAME, title)
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
        values.put(CATEGORY_COLUMN_PREVIEW, preview)

        return db.insertOrThrow(TABLE_CATEGORY, null, values)
    }

    @Suppress("unused")
    @Throws(SQLiteException::class)
    fun insertCategory(c: Category): Long {
        val id: Long = if (c.id == Category.ID_NOT_ASSIGNED) {
            insertCategory(c.title, c.type, c.previewText)
        } else {
            insertCategory(c.id, c.title, c.type, c.previewText)
        }
        if (id < 0) {
            Log.e(TAG, "Failed to insert category")
            return -1
        }
        c.id = id
        return id
    }

    fun deleteCategory(id: Long): Int {
        val args = arrayOf(id.toString())
        return db.delete(TABLE_CATEGORY, CATEGORY_WHERE_CLAUSE_ID, args)
    }

    fun updateCategory(id: Long, title: String?, type: Category.Type, previewText: String?): Int {
        val values = ContentValues()
        values.put(CATEGORY_COLUMN_NAME, title)
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
        values.put(CATEGORY_COLUMN_PREVIEW, previewText)
        val args = arrayOf(id.toString())
        return db.update(TABLE_CATEGORY, values, CATEGORY_WHERE_CLAUSE_ID, args)
    }

    fun updateCategory(category: Category): Int {
        return updateCategory(
            category.id, category.title,
            category.type, category.previewText
        )
    }

    @JvmOverloads
    fun queryCategory(
        maxNum: Int,
        categories: ArrayList<Category>,
        cb: IterateCallback<Category>? = null
    ) {

        var selection: String? = null
        var args: Array<String?>? = null
        if (!categories.isEmpty()) {
            selection = "_id NOT IN (${"?,".repeat(categories.size-1)}?)"
            args = arrayOfNulls(categories.size)
            for (i in args.indices) {
                args[i] = categories[i].id.toString()
            }
        }
        val cursor = db.query(TABLE_CATEGORY, null, selection, args, null, null, null)
        val vv = Category.Type.entries.toTypedArray()

        var nCategory = 0
        while (cursor.moveToNext()) {

            if (maxNum in 1..nCategory) break
            var idx: Int = cursor.getColumnIndex(CATEGORY_COLUMN_ID)
            if (idx == -1) {
                Log.e(TAG, "invalid idx")
                continue
            }
            val id = cursor.getLong(idx)
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_NAME)
            if (idx == -1) {
                Log.e(TAG, "invalid idx")
                continue
            }
            val name = cursor.getString(idx)
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_PREVIEW)
            if (idx == -1) {
                Log.e(TAG, "invalid idx")
                continue
            }
            val preview = cursor.getString(idx)
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_TYPE)
            if (idx == -1) {
                Log.e(TAG, "invalid idx")
                continue
            }
            val typeNum = cursor.getInt(idx)
            if (typeNum < 0 || typeNum >= vv.size) {
                Log.e(TAG, "invalid type_num: $typeNum")
                continue
            }
            val type = Category.Type.entries[typeNum]

            val c = Category(name, type)
            c.id = id
            c.previewText = preview
            categories.add(c)
            nCategory++
            cb?.onIterate(c)
        }

        cursor.close()
    }

    private fun NoteItem.generateAttachmentString(): String? {
        if (this.attachments.isEmpty()) return null

        val sb = StringBuilder()

        this.attachments.iterator().forEach { f ->
            sb.append(when(f.type) {
                AttachmentFile.Type.MEDIA -> NOTE_ATTACHMENT_PREFIX_IMAGE
                AttachmentFile.Type.FILE -> NOTE_ATTACHMENT_PREFIX_FILE
            })
            sb.append(':')
            sb.append(f.md5)
            sb.append(':')
            sb.append(Base64.encodeToString(f.name.toByteArray(), Base64.DEFAULT))
            sb.append(',')
        }

        Log.v(TAG, "generateAttachmentString: $sb")
        return sb.toString()
    }

    // don't consider the backward compatibility of attachment info format here
    // TODO: implement database upgrade methods
    private fun parseAttachmentFile(info: String): AttachmentFile? {
        val infoS = info.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        val idxType = 0
        val idxMd5 = 1
        val idxName = 2

        val sType = when(infoS[idxType]) {
            NOTE_ATTACHMENT_PREFIX_IMAGE -> AttachmentFile.Type.MEDIA
            NOTE_ATTACHMENT_PREFIX_FILE -> AttachmentFile.Type.FILE
            else -> {
                Log.e(TAG, "Got unknown attachment type: ${infoS[0]}, return null")
                return null
            }
        }

        val sMd5 = infoS[idxMd5]
        val sName = if (infoS.size <= idxName) "" else String(
            Base64.decode(
                infoS[idxName],
                Base64.DEFAULT
            )
        )
        return from(
            getFolderImage(context),
            sMd5, sName, sType
        )
    }

    @Throws(SQLiteException::class)
    fun insertNote(
        id: Long, categoryId: Long, text: String?, ctime: Long,
        attachmentInfo: String?
    ): Long {
        val values = ContentValues()
        values.put(NOTE_COLUMN_ID, id)
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        return db.insertOrThrow(TABLE_NOTE, null, values)
    }

    @Throws(SQLiteException::class)
    fun insertNote(
        categoryId: Long, text: String?, ctime: Long,
        attachmentInfo: String?
    ): Long {
        val values = ContentValues()
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        return db.insertOrThrow(TABLE_NOTE, null, values)
    }

    /**
     * insert a note item into database, if noteItem.getId() == ID_NOT_ASSIGNED, will generate a
     * new ID for it, and return that new id
     * @param n the noteItem need to be insert
     * @return the id in database
     */
    @Throws(SQLiteException::class)
    fun insertNote(n: NoteItem): Long {
        val id: Long = if (n.id == NoteItem.ID_NOT_ASSIGNED) {
            insertNote(n.categoryId, n.text, n.createTime,
                n.generateAttachmentString())
        } else {
            insertNote(
                n.id, n.categoryId, n.text, n.createTime,
                n.generateAttachmentString()
            )
        }
        if (id < 0) {
            Log.e(TAG, "Failed to insert note")
            return -1
        }
        n.id = id
        return id
    }

    /**
     * delete a noteItem in database with specific id
     * @param id ID of target noteItem
     * @return number of deleted entries
     */
    fun deleteNote(id: Long): Int {
        val args = arrayOf(id.toString())
        return db.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_ID, args)
    }

    fun deleteNotes(categoryId: Long): Int {
        val args = arrayOf(categoryId.toString())
        return db.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_CATE_ID, args)
    }

    /**
     * update note info in database with specific ID, all data apart of ID will be changed
     * @param id ID of noteItem need to be updated
     * @param categoryId the ID of the new category
     * @param text new note text
     * @param ctime new create time
     * @param attachmentInfo new image file
     * @return count of affected rows
     */
    fun updateNote(
        id: Long, categoryId: Long, text: String?, ctime: Long,
        attachmentInfo: String?
    ): Int {
        val values = ContentValues()
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        val args = arrayOf(id.toString())
        return db.update(TABLE_NOTE, values, NOTE_WHERE_CLAUSE_ID, args)
    }

    /**
     * update note info in database with specific ID, all data apart of ID will be changed
     * @param note item object contain updated info
     * @return count of affected rows
     */
    fun updateNote(note: NoteItem): Int {
        return updateNote(
            note.id, note.categoryId, note.text,
            note.createTime, note.generateAttachmentString()
        )
    }

    private fun Cursor.parseCurrentNoteItem(): NoteItem? {
        var idx: Int = getColumnIndex(NOTE_COLUMN_ID)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for id")
            return null
        }
        val id = getLong(idx)
        idx = getColumnIndex(NOTE_COLUMN_CATE_ID)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for category id")
            return null
        }
        val categoryId = getLong(idx)
        idx = getColumnIndex(NOTE_COLUMN_TEXT)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for text")
            return null
        }
        val text = getString(idx)
        idx = getColumnIndex(NOTE_COLUMN_C_TIME)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for ctime")
            return null
        }
        val ctime = getLong(idx)
        idx = getColumnIndex(NOTE_COLUMN_ATTACHMENT_INFO)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for attachment_info")
            return null
        }
        val attachInfoAll: String? = getString(idx)

        val e = NoteItem(text, ctime)
        e.id = id
        e.categoryId = categoryId
        if (attachInfoAll != null && !attachInfoAll.isEmpty()) {
            Log.d(TAG, "imgInfoAll: $attachInfoAll")

            val imgInfoAllS =
                attachInfoAll.split(",".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            for (info in imgInfoAllS) {
                if (info.isEmpty()) continue
                val imageFile = parseAttachmentFile(info) ?: continue
                if (!e.attachments.add(imageFile)) {
                    Log.e(TAG, "Failed to set image file: $attachInfoAll")
                }
            }
        } else {
            Log.d(TAG, "no attachment info")
        }
        return e
    }

    /**
     * retrieve note data from database
     * @param maxNum max count of retrieve items, -1 for unlimited
     * @param targetCategoryId id of specific category, -1 for unspecific
     * @param noteItems the list to receive the result
     */
    @JvmOverloads
    fun queryNote(
        maxNum: Int,
        targetCategoryId: Long,
        noteItems: ArrayList<NoteItem>,
        cb: IterateCallback<NoteItem>? = null
    ) {

        var selection: String? = null
        var selectionArgs: Array<String>? = null
        if (targetCategoryId != -1L) {
            selection = "$NOTE_COLUMN_CATE_ID = ?"
            selectionArgs = arrayOf(targetCategoryId.toString())
        }
        val cursor = db.query(
            TABLE_NOTE, null, selection, selectionArgs,
            null, null, "$NOTE_COLUMN_C_TIME DESC"
        )
        // reverse query by C_TIME, make sure the latest added item can be got

        var nNotes = 0
        while (cursor.moveToNext()) {
            if (maxNum in 1..nNotes) break

            val e = cursor.parseCurrentNoteItem() ?: continue
            noteItems.add(e)
            nNotes++
            cb?.onIterate(e)
        }

        cursor.close()
    }

    companion object {
        const val TAG = "DataBaseHelper"

        private const val DATABASE_NAME = "note.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_CATEGORY = "category"
        private const val CATEGORY_COLUMN_ID = "_id"
        private const val CATEGORY_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT"
        private const val CATEGORY_COLUMN_NAME = "name"
        private const val CATEGORY_COLUMN_NAME_TYPE = "TEXT"
        private const val CATEGORY_COLUMN_PREVIEW = "preview_text"
        private const val CATEGORY_COLUMN_PREVIEW_TYPE = "TEXT"
        private const val CATEGORY_COLUMN_TYPE = "type"
        private const val CATEGORY_COLUMN_TYPE_TYPE = "INTEGER"

        private const val TABLE_NOTE = "note"
        private const val NOTE_COLUMN_ID = "_id"
        private const val NOTE_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT"
        private const val NOTE_COLUMN_CATE_ID = "category_id"
        private const val NOTE_COLUMN_CATE_ID_TYPE = "INTEGER"
        private const val NOTE_COLUMN_TEXT = "text"
        private const val NOTE_COLUMN_TEXT_TYPE = "TEXT"
        private const val NOTE_COLUMN_C_TIME = "ctime"
        private const val NOTE_COLUMN_C_TIME_TYPE = "INTEGER"
        private const val NOTE_COLUMN_ATTACHMENT_INFO = "img_info"  // TODO: rename to attachment_info
        private const val NOTE_COLUMN_IMG_INFO_TYPE = "TEXT"

        private const val NOTE_ATTACHMENT_PREFIX_IMAGE = "img"
        private const val NOTE_ATTACHMENT_PREFIX_FILE = "file"

        private const val CATEGORY_WHERE_CLAUSE_ID: String = "$CATEGORY_COLUMN_ID = ?"
        private const val NOTE_WHERE_CLAUSE_ID: String = "$NOTE_COLUMN_ID = ?"
        private const val NOTE_WHERE_CLAUSE_CATE_ID: String = "$NOTE_COLUMN_CATE_ID = ?"
    }
}
