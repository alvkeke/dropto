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
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.mgmt.Global
import java.io.File


class DataBaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    fun interface IterateCallback<T> {
        fun onIterate(o: T)
    }

    companion object {
        const val TAG = "DataBaseHelper"

        private const val DATABASE_NAME = "note.db"
        private const val DATABASE_VERSION = 2

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
        private const val NOTE_COLUMN_ATTACHMENT_INFO_V1 = "img_info"
        private const val NOTE_COLUMN_ATTACHMENT_INFO_V2 = "attachment_info"
        private const val NOTE_COLUMN_ATTACHMENT_INFO = NOTE_COLUMN_ATTACHMENT_INFO_V2
        private const val NOTE_COLUMN_ATTACHMENT_INFO_TYPE = "TEXT"
        private const val NOTE_ATTACHMENT_PREFIX_IMAGE = "img"
        private const val NOTE_ATTACHMENT_PREFIX_FILE = "file"
        private const val NOTE_COLUMN_FLAGS = "flags"
        private const val NOTE_COLUMN_FLAGS_TYPE = "INTEGER DEFAULT 0"
        private const val NOTE_FLAG_IS_DELETED: Long = 1 shl 0
        private const val NOTE_FLAG_IS_EDITED:Long = 1 shl 1
        private const val NOTE_FLAG_IS_SYNCED:Long = 1 shl 2

        private const val CATEGORY_WHERE_CLAUSE_ID: String = "$CATEGORY_COLUMN_ID = ?"
        private const val NOTE_WHERE_CLAUSE_ID: String = "$NOTE_COLUMN_ID = ?"
        private const val NOTE_WHERE_CLAUSE_CATE_ID: String = "$NOTE_COLUMN_CATE_ID = ?"
    }

    private lateinit var db: SQLiteDatabase

    override fun onCreate(db: SQLiteDatabase) {
        createCategoryTable(db)
        createNoteTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1) {
            upgradeV1ToV2(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 2) {
            downgradeV2ToV1(db)
        }
    }

    private fun upgradeV1ToV2(db: SQLiteDatabase) {
        Log.e(TAG, "Database upgrading: 1 -> 2, rename current note table to backup table")
        db.execSQL("ALTER TABLE $TABLE_NOTE RENAME TO ${TABLE_NOTE}_BAK;")
        Log.e(TAG, "Database upgrading: 1 -> 2, create new note table with updated schema")
        createNoteTable(db)
        Log.e(TAG, "Database upgrading: 1 -> 2, copy data from backup table to new table")
        db.execSQL("""
            INSERT INTO $TABLE_NOTE (
                $NOTE_COLUMN_ID,
                $NOTE_COLUMN_CATE_ID,
                $NOTE_COLUMN_TEXT,
                $NOTE_COLUMN_C_TIME,
                $NOTE_COLUMN_ATTACHMENT_INFO_V2
            )
            SELECT
                $NOTE_COLUMN_ID,
                $NOTE_COLUMN_CATE_ID,
                $NOTE_COLUMN_TEXT,
                $NOTE_COLUMN_C_TIME,
                $NOTE_COLUMN_ATTACHMENT_INFO_V1
            FROM ${TABLE_NOTE}_BAK;
        """.trimIndent())
        Log.e(TAG, "Database upgrading: 1 -> 2, drop backup table")
        db.execSQL("DROP TABLE IF EXISTS ${TABLE_NOTE}_BAK;")
        Log.e(TAG, "Database upgraded successfully: 1 -> 2")
        printCurrentDbStruct(db)
    }

    private fun downgradeV2ToV1(db: SQLiteDatabase) {
        Log.e(TAG, "Database downgrading: 2 -> 1, rename current note table to backup table")
        db.execSQL("ALTER TABLE $TABLE_NOTE RENAME TO ${TABLE_NOTE}_BAK;")
        Log.e(TAG, "Database downgrading: 2 -> 1, create new note table with supported schema")
        createNoteTable(db)
        printCurrentDbStruct(db)
        Log.e(TAG, "Database downgrading: 2 -> 1, copy data from backup table to new table")
        db.execSQL("""
            INSERT INTO $TABLE_NOTE (
                $NOTE_COLUMN_ID,
                $NOTE_COLUMN_CATE_ID,
                $NOTE_COLUMN_TEXT,
                $NOTE_COLUMN_C_TIME,
                $NOTE_COLUMN_ATTACHMENT_INFO_V1
            )
            SELECT
                $NOTE_COLUMN_ID,
                $NOTE_COLUMN_CATE_ID,
                $NOTE_COLUMN_TEXT,
                $NOTE_COLUMN_C_TIME,
                $NOTE_COLUMN_ATTACHMENT_INFO_V2
            FROM ${TABLE_NOTE}_BAK;
        """.trimIndent())
        Log.e(TAG, "Database downgrading: 2 -> 1, drop backup table")
        db.execSQL("DROP TABLE IF EXISTS ${TABLE_NOTE}_BAK;")
        Log.e(TAG, "Database downgraded successfully: 2 -> 1")
        printCurrentDbStruct(db)
    }

    private fun printCurrentDbStruct(db: SQLiteDatabase) {
        val cursor = db.rawQuery("PRAGMA table_info($TABLE_NOTE);", null)
        while (cursor.moveToNext()) {
            val nameIdx = cursor.getColumnIndex("name")
            val typeIdx = cursor.getColumnIndex("type")
            if (nameIdx == -1 || typeIdx == -1) continue
            val name = cursor.getString(nameIdx)
            val type = cursor.getString(typeIdx)
            Log.e(TAG, "Note table column: $name, type: $type")
        }
        cursor.close()
    }

    fun exportDatabaseFile(): File {
        return context.getDatabasePath(DATABASE_NAME)
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

    /*
    private fun createNoteTableV1(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTE (
                $NOTE_COLUMN_ID $NOTE_COLUMN_ID_TYPE,
                $NOTE_COLUMN_CATE_ID $NOTE_COLUMN_CATE_ID_TYPE,
                $NOTE_COLUMN_TEXT $NOTE_COLUMN_TEXT_TYPE,
                $NOTE_COLUMN_C_TIME $NOTE_COLUMN_C_TIME_TYPE,
                $NOTE_COLUMN_ATTACHMENT_INFO_V1 $NOTE_COLUMN_ATTACHMENT_INFO_TYPE
            );
        """.trimIndent()

        db.execSQL(sql)
    }
     */

    private fun createNoteTable(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTE (
                $NOTE_COLUMN_ID $NOTE_COLUMN_ID_TYPE,
                $NOTE_COLUMN_CATE_ID $NOTE_COLUMN_CATE_ID_TYPE,
                $NOTE_COLUMN_TEXT $NOTE_COLUMN_TEXT_TYPE,
                $NOTE_COLUMN_C_TIME $NOTE_COLUMN_C_TIME_TYPE,
                $NOTE_COLUMN_ATTACHMENT_INFO $NOTE_COLUMN_ATTACHMENT_INFO_TYPE,
                $NOTE_COLUMN_FLAGS $NOTE_COLUMN_FLAGS_TYPE
            );
        """.trimIndent()

        db.execSQL(sql)
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
        return from(Global.attachmentStorage, sMd5, sName, sType)
    }

    private fun Long.set(flag: Long): Long {
        return this or flag
    }

    private fun Long.reset(flag: Long): Long {
        return this and flag.inv()
    }

    private fun Long.isSet(flag: Long): Boolean {
        return this and flag == flag
    }

    private fun NoteItem.generateFlags(): Long {
        var flags = 0L
        if (isDeleted) flags = NOTE_FLAG_IS_DELETED
        if (isEdited) flags = flags.set(NOTE_FLAG_IS_EDITED)
        if (isSynced) flags = flags.set(NOTE_FLAG_IS_SYNCED)
        return flags
    }

    private fun NoteItem.parseFlags(flags: Long) {
        isDeleted = flags.isSet(NOTE_FLAG_IS_DELETED)
        isEdited = flags.isSet(NOTE_FLAG_IS_EDITED)
        isSynced = flags.isSet(NOTE_FLAG_IS_SYNCED)
    }

    @Throws(SQLiteException::class)
    fun insertNote(
        id: Long, categoryId: Long, text: String?, ctime: Long,
        attachmentInfo: String?, flags: Long
    ): Long {
        val values = ContentValues()
        values.put(NOTE_COLUMN_ID, id)
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        values.put(NOTE_COLUMN_FLAGS, flags)
        return db.insertOrThrow(TABLE_NOTE, null, values)
    }

    @Throws(SQLiteException::class)
    fun insertNote(
        categoryId: Long, text: String?, ctime: Long,
        attachmentInfo: String?, flags: Long
    ): Long {
        val values = ContentValues()
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        values.put(NOTE_COLUMN_FLAGS, flags)
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
                n.generateAttachmentString(), n.generateFlags())
        } else {
            insertNote(
                n.id, n.categoryId, n.text, n.createTime,
                n.generateAttachmentString(), n.generateFlags()
            )
        }
        if (id < 0) {
            Log.e(TAG, "Failed to insert note")
            return -1
        }
        n.id = id
        return id
    }

    private val noteQueryFlagsColumn: Array<String> = arrayOf(NOTE_COLUMN_FLAGS)
    fun queryFlags(id: Long): Long? {
        val args = arrayOf(id.toString())
        val cursor = db.query(
            TABLE_NOTE, noteQueryFlagsColumn,
            NOTE_WHERE_CLAUSE_ID, args,
            null, null, null
        )
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(NOTE_COLUMN_FLAGS)
            if (idx == -1) {
                Log.e(TAG, "invalid idx for flags")
                cursor.close()
                return null
            }
            val flags = cursor.getLong(idx)
            cursor.close()
            return flags
        } else {
            Log.e(TAG, "no note found with id: $id")
            cursor.close()
            return null
        }
    }

    fun updateFlags(id: Long, flags: Long): Int {
        val values = ContentValues()
        values.put(NOTE_COLUMN_FLAGS, flags)
        val args = arrayOf(id.toString())
        return db.update(TABLE_NOTE, values, NOTE_WHERE_CLAUSE_ID, args)
    }

    fun deleteNote(id: Long): Int {
        val flags = queryFlags(id) ?: return -1
        val newFlags = flags.set(NOTE_FLAG_IS_DELETED)

        return updateFlags(id, newFlags)
    }

    fun deleteNotes(categoryId: Long): Int {
        val noteIds = queryNoteIds(categoryId)
        var count = 0
        for (id in noteIds) {
            if (deleteNote(id) > 0) {
                count++
            }
        }
        return count
    }

    fun restoreNote(id: Long): Int {
        val flags = queryFlags(id) ?: return -1
        val newFlags = flags.reset(NOTE_FLAG_IS_DELETED)

        return updateFlags(id, newFlags)
    }

    fun restoreNotes(categoryId: Long): Int {
        val noteIds = queryNoteIds(categoryId)
        var count = 0
        for (id in noteIds) {
            if (restoreNote(id) > 0) {
                count++
            }
        }
        return count
    }

    /**
     * delete a noteItem in database with specific id
     * @param id ID of target noteItem
     * @return number of deleted entries
     */
    fun realDeleteNote(id: Long): Int {
        val args = arrayOf(id.toString())
        return db.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_ID, args)
    }

    fun realDeleteNotes(categoryId: Long): Int {
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
        attachmentInfo: String?, flags: Long
    ): Int {
        val values = ContentValues()
        values.put(NOTE_COLUMN_CATE_ID, categoryId)
        values.put(NOTE_COLUMN_TEXT, text)
        values.put(NOTE_COLUMN_C_TIME, ctime)
        values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
        values.put(NOTE_COLUMN_FLAGS, flags)
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
            note.createTime,
            note.generateAttachmentString(), note.generateFlags()
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
        idx = getColumnIndex(NOTE_COLUMN_FLAGS)
        if (idx == -1) {
            Log.e(TAG, "invalid idx for flags")
            return null
        }
        val flags = getLong(idx)

        val e = NoteItem(text, ctime)
        e.parseFlags(flags)
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

    private val queryNoteIdsSelection = "$NOTE_COLUMN_CATE_ID = ?"
    fun queryNoteIds(targetCategoryId: Long): ArrayList<Long> {
        val ids = ArrayList<Long>()

        val selectionArgs = arrayOf(targetCategoryId.toString())

        val cursor = db.query(
            TABLE_NOTE, arrayOf(NOTE_COLUMN_ID), queryNoteIdsSelection, selectionArgs,
            null, null, null
        )

        while (cursor.moveToNext()) {
            val idx: Int = cursor.getColumnIndex(NOTE_COLUMN_ID)
            if (idx == -1) {
                Log.e(TAG, "invalid idx for id")
                continue
            }
            val id = cursor.getLong(idx)
            ids.add(id)
        }

        cursor.close()
        return ids
    }

}
