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
import java.io.File
import androidx.core.database.sqlite.transaction


class DataBaseHelper(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    fun interface IterateCallback<T> {
        fun onIterate(o: T)
    }

    companion object {
        const val TAG = "DataBaseHelper"

        private const val DATABASE_NAME = "note.db"
        private const val DATABASE_VERSION = 4

        const val TABLE_CATEGORY = "category"
        const val CATEGORY_COLUMN_ID = "_id"
        const val CATEGORY_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT"
        const val CATEGORY_COLUMN_NAME = "name"
        const val CATEGORY_COLUMN_NAME_TYPE = "TEXT"
        const val CATEGORY_COLUMN_PREVIEW = "preview_text"
        const val CATEGORY_COLUMN_PREVIEW_TYPE = "TEXT"
        const val CATEGORY_COLUMN_TYPE = "type"
        const val CATEGORY_COLUMN_TYPE_TYPE = "INTEGER"

        const val TABLE_NOTE = "note"
        const val NOTE_COLUMN_ID = "_id"
        const val NOTE_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT"
        const val NOTE_COLUMN_CATE_ID = "category_id"
        const val NOTE_COLUMN_CATE_ID_TYPE = "INTEGER"
        const val NOTE_COLUMN_TEXT = "text"
        const val NOTE_COLUMN_TEXT_TYPE = "TEXT"
        const val NOTE_COLUMN_C_TIME = "ctime"
        const val NOTE_COLUMN_C_TIME_TYPE = "INTEGER"
        const val NOTE_COLUMN_ATTACHMENT_INFO_V1 = "img_info"
        const val NOTE_COLUMN_ATTACHMENT_INFO_V2 = "attachment_info"
        const val NOTE_COLUMN_ATTACHMENT_INFO = NOTE_COLUMN_ATTACHMENT_INFO_V2
        const val NOTE_COLUMN_ATTACHMENT_INFO_TYPE = "TEXT"
        const val NOTE_ATTACHMENT_PREFIX_IMAGE = "img"
        const val NOTE_ATTACHMENT_PREFIX_FILE = "file"
        const val NOTE_COLUMN_FLAGS = "flags"
        const val NOTE_COLUMN_FLAGS_TYPE = "INTEGER DEFAULT 0"
        const val NOTE_FLAG_IS_DELETED: Long = 1 shl 0
        const val NOTE_FLAG_IS_EDITED:Long = 1 shl 1
        const val NOTE_FLAG_IS_SYNCED:Long = 1 shl 2
        const val NOTE_COLUMN_SENDER = "sender"
        const val NOTE_COLUMN_SENDER_TYPE = "TEXT DEFAULT NULL"
        const val NOTE_COLUMN_REACTION = "reaction"
        const val NOTE_COLUMN_REACTION_TYPE = "TEXT DEFAULT NULL"

        const val TABLE_REACTION = "reaction"
        const val REACTION_COLUMN_ID = "_id"
        const val REACTION_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT"
        const val REACTION_COLUMN_TEXT = "reaction_text"
        const val REACTION_COLUMN_TEXT_TYPE = "TEXT"
        const val REACTION_COLUMN_SEQUENCE = "reaction_sequence"
        const val REACTION_COLUMN_SEQUENCE_TYPE = "INTEGER"

    }

    override fun onCreate(db: SQLiteDatabase) {
        createCategoryTable(db)
        createNoteTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (oldVersion) {
            1 -> {
                upgradeV1ToV2(db)
                upgradeV2ToV3(db)
                upgradeV3ToV4(db)
            }
            2 -> {
                upgradeV2ToV3(db)
                upgradeV3ToV4(db)
            }
            3 -> {
                upgradeV3ToV4(db)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.e(TAG, "Database try downgrade: $oldVersion -> $newVersion")
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

    private fun upgradeV2ToV3(db: SQLiteDatabase) {
        Log.e(TAG, "Database upgrading: 2 -> 3, add new column for the column")
        try {
            db.execSQL("ALTER TABLE $TABLE_NOTE ADD COLUMN $NOTE_COLUMN_SENDER $NOTE_COLUMN_SENDER_TYPE;")
            Log.e(TAG, "Database upgraded successfully: 2 -> 3")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add new column for sender, maybe already exists, ignore and continue: $e")
        }
    }

    private fun upgradeV3ToV4(db: SQLiteDatabase) {
        Log.e(TAG, "Database upgrading: 3 -> 4, add new table for reactions")
        createReactionTable(db)
        Log.e(TAG, "Database upgrading: 3 -> 4, add new column for reaction in note table")
        try {
            db.execSQL("ALTER TABLE $TABLE_NOTE ADD COLUMN $NOTE_COLUMN_REACTION $NOTE_COLUMN_REACTION_TYPE;")
            Log.e(TAG, "Database upgraded successfully: 3 -> 4")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upgrade database for reactions, maybe already exists, ignore and continue: $e")
        }
    }

    @Suppress("unused")
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

    @Suppress("unused")
    private fun downgradeV3ToV2(db: SQLiteDatabase) {
        Log.e(TAG, "nothing to do in downgrading from V3 to V2, the new column doesn't matter")
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
                $NOTE_COLUMN_FLAGS $NOTE_COLUMN_FLAGS_TYPE,
                $NOTE_COLUMN_SENDER $NOTE_COLUMN_SENDER_TYPE,
                $NOTE_COLUMN_REACTION $NOTE_COLUMN_REACTION_TYPE
            );
        """.trimIndent()

        db.execSQL(sql)
    }

    private fun createReactionTable(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_REACTION (
                $REACTION_COLUMN_ID $REACTION_COLUMN_ID_TYPE,
                $REACTION_COLUMN_TEXT $REACTION_COLUMN_TEXT_TYPE,
                $REACTION_COLUMN_SEQUENCE $REACTION_COLUMN_SEQUENCE_TYPE
            );
        """.trimIndent()

        db.execSQL(sql)
    }

    @Suppress("unused")
    fun destroyDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

}
