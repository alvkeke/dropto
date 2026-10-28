package cn.alvkeke.dropto.provider.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Base64
import android.util.Log
import androidx.core.database.sqlite.transaction
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.CATEGORY_COLUMN_ID
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.CATEGORY_COLUMN_NAME
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.CATEGORY_COLUMN_PREVIEW
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.CATEGORY_COLUMN_SEQUENCE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.CATEGORY_COLUMN_TYPE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_ATTACHMENT_PREFIX_FILE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_ATTACHMENT_PREFIX_IMAGE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_ATTACHMENT_INFO
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_CATE_ID
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_C_TIME
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_FLAGS
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_ID
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_REACTION
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_SENDER
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_COLUMN_TEXT
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_FLAG_FORCE_NEW_GROUP
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_FLAG_IS_DELETED
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_FLAG_IS_EDITED
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.NOTE_FLAG_IS_SYNCED
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.REACTION_COLUMN_SEQUENCE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.REACTION_COLUMN_TEXT
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.TABLE_CATEGORY
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.TABLE_NOTE
import cn.alvkeke.dropto.provider.local.DataBaseHelper.Companion.TABLE_REACTION
import cn.alvkeke.dropto.provider.local.DataBaseHelper.IterateCallback
import cn.alvkeke.dropto.provider.model.AttachmentRecord
import cn.alvkeke.dropto.provider.model.CategoryRecord
import cn.alvkeke.dropto.provider.model.ID_NOT_ASSIGNED
import cn.alvkeke.dropto.provider.model.NoteRecord

private const val TAG = "DataBaseFunctions"

private const val CATEGORY_WHERE_CLAUSE_ID: String = "$CATEGORY_COLUMN_ID = ?"
private const val NOTE_WHERE_CLAUSE_ID: String = "$NOTE_COLUMN_ID = ?"
private const val NOTE_WHERE_CLAUSE_CATE_ID: String = "$NOTE_COLUMN_CATE_ID = ?"

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(id: Long, title: String, type: Int, preview: String?): Long {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_ID, id)
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type)
    values.put(CATEGORY_COLUMN_PREVIEW, preview)

    return this.insertOrThrow(TABLE_CATEGORY, null, values)
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(title: String, type: Int, preview: String?): Long {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type)
    values.put(CATEGORY_COLUMN_PREVIEW, preview)

    return this.insertOrThrow(TABLE_CATEGORY, null, values)
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(c: CategoryRecord): Long {
    val id: Long = if (c.id == ID_NOT_ASSIGNED) {
        insertCategory(c.title, c.type, c.previewText)
    } else {
        insertCategory(c.id, c.title, c.type, c.previewText)
    }
    if (id < 0) {
        Log.e(TAG, "Failed to insert category")
    }
    return id
}

fun SQLiteDatabase.deleteCategory(id: Long): Int {
    val args = arrayOf(id.toString())
    return this.delete(TABLE_CATEGORY, CATEGORY_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.updateCategory(id: Long, title: String?, type: Int, previewText: String?): Int {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type)
    values.put(CATEGORY_COLUMN_PREVIEW, previewText)
    val args = arrayOf(id.toString())
    return this.update(TABLE_CATEGORY, values, CATEGORY_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.updateCategory(category: CategoryRecord): Int {
    return updateCategory(
        category.id, category.title,
        category.type, category.previewText
    )
}

@JvmOverloads
fun SQLiteDatabase.queryCategory(
    categories: ArrayList<CategoryRecord>,
    maxNum: Int = -1,
    cb: IterateCallback<CategoryRecord>? = null
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
    val cursor = this.query(
        TABLE_CATEGORY,
        null,
        selection,
        args,
        null,
        null,
        "$CATEGORY_COLUMN_SEQUENCE ASC"
    )

    var nCategory = 0
    while (cursor.moveToNext()) {

        nCategory++
        if (maxNum > -1 && nCategory > maxNum) break
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

        val c = CategoryRecord(id, name, typeNum, preview)
        categories.add(c)
        cb?.onIterate(c)
    }

    cursor.close()
}

fun SQLiteDatabase.reorderCategories(categoryIds: List<Long>) {
    this.transaction {
        for (i in categoryIds.indices) {
            val values = ContentValues()
            values.put(CATEGORY_COLUMN_SEQUENCE, i)
            val args = arrayOf(categoryIds[i].toString())
            this.update(TABLE_CATEGORY, values, CATEGORY_WHERE_CLAUSE_ID, args)
        }
    }
}

private fun NoteRecord.generateAttachmentString(): String? {
    if (this.attachments.isEmpty()) return null

    val sb = StringBuilder()

    this.attachments.forEach { f ->
        sb.append(
            if (f.type == AttachmentRecord.TYPE_FILE) NOTE_ATTACHMENT_PREFIX_FILE
            else NOTE_ATTACHMENT_PREFIX_IMAGE
        )
        sb.append(':')
        sb.append(f.md5)
        sb.append(':')
        sb.append(Base64.encodeToString(f.name.toByteArray(), Base64.DEFAULT))
        sb.append(',')
    }

    Log.v(TAG, "generateAttachmentString: $sb")
    return sb.toString()
}

private fun parseAttachmentFile(info: String): AttachmentRecord? {
    val infoS = info.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

    val idxType = 0
    val idxMd5 = 1
    val idxName = 2

    val sType = when(infoS[idxType]) {
        NOTE_ATTACHMENT_PREFIX_IMAGE -> AttachmentRecord.TYPE_MEDIA
        NOTE_ATTACHMENT_PREFIX_FILE -> AttachmentRecord.TYPE_FILE
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
    return AttachmentRecord(sMd5, sName, sType)
}

private fun parseAttachmentString(attachInfoAll: String?): List<AttachmentRecord> {
    if (attachInfoAll.isNullOrEmpty()) {
        Log.d(TAG, "no attachment info")
        return emptyList()
    }
    Log.d(TAG, "imgInfoAll: $attachInfoAll")

    val result = ArrayList<AttachmentRecord>()
    attachInfoAll.split(",".toRegex())
        .dropLastWhile { it.isEmpty() }
        .filter { it.isNotEmpty() }
        .forEach { info ->
            val attachment = parseAttachmentFile(info)
            if (attachment == null) {
                Log.e(TAG, "Failed to parse attachment file: $info")
            } else {
                result.add(attachment)
            }
        }
    return result
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

private fun NoteRecord.generateFlags(): Long {
    var flags = 0L
    if (deleted) flags = NOTE_FLAG_IS_DELETED
    if (edited) flags = flags.set(NOTE_FLAG_IS_EDITED)
    if (synced) flags = flags.set(NOTE_FLAG_IS_SYNCED)
    if (forceNewGroup) flags = flags.set(NOTE_FLAG_FORCE_NEW_GROUP)
    return flags
}

private fun NoteRecord.generateReactionString(): String? {
    if (reactions.isEmpty()) return null

    val sb = StringBuilder()
    for (r in reactions) {
        sb.append(Base64.encodeToString(r.toByteArray(), Base64.DEFAULT))
        sb.append(',')
    }

    return sb.toString()
}

private fun parseReactionString(reactionInfo: String?): List<String> {
    if (reactionInfo.isNullOrEmpty()) return emptyList()

    return reactionInfo.split(",".toRegex())
        .dropLastWhile { it.isEmpty() }
        .filter { it.isNotEmpty() }
        .map { String(Base64.decode(it, Base64.DEFAULT)) }
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertNote(
    id: Long, categoryId: Long, text: String?, ctime: Long, sender: String?,
    attachmentInfo: String?, flags: Long, reactionInfo: String?
): Long {
    val values = ContentValues()
    values.put(NOTE_COLUMN_ID, id)
    values.put(NOTE_COLUMN_CATE_ID, categoryId)
    values.put(NOTE_COLUMN_TEXT, text)
    values.put(NOTE_COLUMN_C_TIME, ctime)
    values.put(NOTE_COLUMN_SENDER, sender)
    values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
    values.put(NOTE_COLUMN_FLAGS, flags)
    values.put(NOTE_COLUMN_REACTION, reactionInfo)
    return this.insertOrThrow(TABLE_NOTE, null, values)
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertNote(
    categoryId: Long, text: String?, ctime: Long, sender: String?,
    attachmentInfo: String?, flags: Long, reactionInfo: String?
): Long {
    val values = ContentValues()
    values.put(NOTE_COLUMN_CATE_ID, categoryId)
    values.put(NOTE_COLUMN_TEXT, text)
    values.put(NOTE_COLUMN_C_TIME, ctime)
    values.put(NOTE_COLUMN_SENDER, sender)
    values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
    values.put(NOTE_COLUMN_FLAGS, flags)
    values.put(NOTE_COLUMN_REACTION, reactionInfo)
    return this.insertOrThrow(TABLE_NOTE, null, values)
}

/**
 * insert a note into database, a new ID is generated when the note has none assigned
 * @param n the note need to be insert
 * @return the id in database
 */
@Throws(SQLiteException::class)
fun SQLiteDatabase.insertNote(n: NoteRecord): Long {
    val id: Long = if (n.id == ID_NOT_ASSIGNED) {
        insertNote(n.categoryId, n.text, n.createTime, n.sender,
            n.generateAttachmentString(), n.generateFlags(),
            n.generateReactionString())
    } else {
        insertNote(
            n.id, n.categoryId, n.text, n.createTime, n.sender,
            n.generateAttachmentString(), n.generateFlags(),
            n.generateReactionString()
        )
    }
    if (id < 0) {
        Log.e(TAG, "Failed to insert note")
    }
    return id
}

private val noteQueryFlagsColumn: Array<String> = arrayOf(NOTE_COLUMN_FLAGS)
fun SQLiteDatabase.queryFlags(id: Long): Long? {
    val args = arrayOf(id.toString())
    val cursor = this.query(
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

fun SQLiteDatabase.updateFlags(id: Long, flags: Long): Int {
    val values = ContentValues()
    values.put(NOTE_COLUMN_FLAGS, flags)
    val args = arrayOf(id.toString())
    return this.update(TABLE_NOTE, values, NOTE_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.deleteNote(id: Long): Int {
    val flags = queryFlags(id) ?: return -1
    val newFlags = flags.set(NOTE_FLAG_IS_DELETED)

    return updateFlags(id, newFlags)
}

fun SQLiteDatabase.deleteNotes(categoryId: Long): Int {
    val noteIds = queryNoteIds(categoryId)
    var count = 0
    for (id in noteIds) {
        if (deleteNote(id) > 0) {
            count++
        }
    }
    return count
}

fun SQLiteDatabase.restoreNote(id: Long): Int {
    val flags = queryFlags(id) ?: return -1
    val newFlags = flags.reset(NOTE_FLAG_IS_DELETED)

    return updateFlags(id, newFlags)
}

fun SQLiteDatabase.restoreNotes(categoryId: Long): Int {
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
 * delete a note in database with specific id
 * @param id ID of target note
 * @return number of deleted entries
 */
fun SQLiteDatabase.realDeleteNote(id: Long): Int {
    val args = arrayOf(id.toString())
    return this.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.realDeleteNotes(categoryId: Long): Int {
    val args = arrayOf(categoryId.toString())
    return this.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_CATE_ID, args)
}

/**
 * update note info in database with specific ID, all data apart of ID will be changed
 * @param id ID of note need to be updated
 * @param categoryId the ID of the new category
 * @param text new note text
 * @param ctime new create time
 * @param attachmentInfo new image file
 * @return count of affected rows
 */
fun SQLiteDatabase.updateNote(
    id: Long, categoryId: Long, text: String?, ctime: Long,
    attachmentInfo: String?, flags: Long, reactionInfo: String?
): Int {
    val values = ContentValues()
    values.put(NOTE_COLUMN_CATE_ID, categoryId)
    values.put(NOTE_COLUMN_TEXT, text)
    values.put(NOTE_COLUMN_C_TIME, ctime)
    // update sender is not allowed now
    values.put(NOTE_COLUMN_ATTACHMENT_INFO, attachmentInfo)
    values.put(NOTE_COLUMN_FLAGS, flags)
    values.put(NOTE_COLUMN_REACTION, reactionInfo)
    val args = arrayOf(id.toString())
    return this.update(TABLE_NOTE, values, NOTE_WHERE_CLAUSE_ID, args)
}

/**
 * update note info in database with specific ID, all data apart of ID will be changed
 * @param note item object contain updated info
 * @return count of affected rows
 */
fun SQLiteDatabase.updateNote(note: NoteRecord): Int {
    return updateNote(
        note.id, note.categoryId, note.text,
        note.createTime,
        note.generateAttachmentString(), note.generateFlags(),
        note.generateReactionString()
    )
}

private fun Cursor.parseCurrentNoteItem(): NoteRecord? {
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
    idx = getColumnIndex(NOTE_COLUMN_SENDER)
    if (idx == -1) {
        Log.e(TAG, "invalid idx for sender")
        return null
    }
    val sender: String? = getString(idx)
    idx = getColumnIndex(NOTE_COLUMN_REACTION)
    if (idx == -1) {
        Log.e(TAG, "invalid idx for reaction")
        return null
    }
    val reactionInfo: String? = getString(idx)

    return NoteRecord(
        id = id,
        categoryId = categoryId,
        text = text,
        createTime = ctime,
        sender = sender,
        deleted = flags.isSet(NOTE_FLAG_IS_DELETED),
        edited = flags.isSet(NOTE_FLAG_IS_EDITED),
        synced = flags.isSet(NOTE_FLAG_IS_SYNCED),
        forceNewGroup = flags.isSet(NOTE_FLAG_FORCE_NEW_GROUP),
        reactions = parseReactionString(reactionInfo),
        attachments = parseAttachmentString(attachInfoAll),
    )
}

/**
 * retrieve note data from database
 * @param maxNum max count of retrieve items, -1 for unlimited
 * @param targetCategoryId id of specific category, -1 for unspecific
 * @param noteItems the list to receive the result
 */
@JvmOverloads
fun SQLiteDatabase.queryNote(
    targetCategoryId: Long,
    noteItems: ArrayList<NoteRecord>,
    maxNum: Int = -1,
    cb: IterateCallback<NoteRecord>? = null
) {

    var selection: String? = null
    var selectionArgs: Array<String>? = null
    if (targetCategoryId != -1L) {
        selection = "$NOTE_COLUMN_CATE_ID = ?"
        selectionArgs = arrayOf(targetCategoryId.toString())
    }
    val cursor = this.query(
        TABLE_NOTE, null, selection, selectionArgs,
        null, null, "$NOTE_COLUMN_C_TIME DESC"
    )
    // reverse query by C_TIME, make sure the latest added item can be got

    var nNotes = 0
    while (cursor.moveToNext()) {
        nNotes++
        if (maxNum > -1 && nNotes > maxNum) break

        val e = cursor.parseCurrentNoteItem() ?: continue
        noteItems.add(e)
        cb?.onIterate(e)
    }

    cursor.close()
}

private const val queryNoteIdsSelection = "$NOTE_COLUMN_CATE_ID = ?"
fun SQLiteDatabase.queryNoteIds(targetCategoryId: Long): ArrayList<Long> {
    val ids = ArrayList<Long>()

    val selectionArgs = arrayOf(targetCategoryId.toString())

    val cursor = this.query(
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


fun SQLiteDatabase.getReactionList(): MutableList<String> {
    val reactions = mutableListOf<String>()
    try {
        val cursor = this.query(
            TABLE_REACTION, arrayOf(REACTION_COLUMN_TEXT),
            null, null, null, null,
            "$REACTION_COLUMN_SEQUENCE ASC"
        )
        while (cursor.moveToNext()) {
            val idx: Int = cursor.getColumnIndex(REACTION_COLUMN_TEXT)
            if (idx == -1) {
                Log.e(TAG, "invalid idx for reaction text")
                continue
            }
            val text = cursor.getString(idx)
            reactions.add(text)
        }
        cursor.close()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get reaction list", e)
    }
    return reactions
}

fun SQLiteDatabase.updateReactionList(reactions: List<String>) {
    this.transaction {
        try {
            delete(TABLE_REACTION, null, null)
            for (i in reactions.indices) {
                val values = ContentValues()
                values.put(REACTION_COLUMN_TEXT, reactions[i])
                values.put(REACTION_COLUMN_SEQUENCE, i)
                insertOrThrow(TABLE_REACTION, null, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update reaction list", e)
        }
    }
}

// write back the committed operations into database file
fun SQLiteDatabase.checkpoint() {
    try {
        rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null).use { it.moveToFirst() }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to checkpoint database", e)
    }
}
