package cn.alvkeke.dropto.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Base64
import android.util.Log
import androidx.core.database.sqlite.transaction
import cn.alvkeke.dropto.data.AttachmentFile
import cn.alvkeke.dropto.data.AttachmentFile.Companion.from
import cn.alvkeke.dropto.data.Category
import cn.alvkeke.dropto.data.NoteItem
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.CATEGORY_COLUMN_ID
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.CATEGORY_COLUMN_NAME
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.CATEGORY_COLUMN_PREVIEW
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.CATEGORY_COLUMN_TYPE
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_ATTACHMENT_PREFIX_FILE
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_ATTACHMENT_PREFIX_IMAGE
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_ATTACHMENT_INFO
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_CATE_ID
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_C_TIME
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_FLAGS
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_ID
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_REACTION
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_SENDER
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_COLUMN_TEXT
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_FLAG_IS_DELETED
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_FLAG_IS_EDITED
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.NOTE_FLAG_IS_SYNCED
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.REACTION_COLUMN_SEQUENCE
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.REACTION_COLUMN_TEXT
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.TABLE_CATEGORY
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.TABLE_NOTE
import cn.alvkeke.dropto.storage.DataBaseHelper.Companion.TABLE_REACTION
import cn.alvkeke.dropto.storage.DataBaseHelper.IterateCallback

const val TAG = "DataBaseFunctions"

const val CATEGORY_WHERE_CLAUSE_ID: String = "$CATEGORY_COLUMN_ID = ?"
const val NOTE_WHERE_CLAUSE_ID: String = "$NOTE_COLUMN_ID = ?"
const val NOTE_WHERE_CLAUSE_CATE_ID: String = "$NOTE_COLUMN_CATE_ID = ?"

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(id: Long, title: String, type: Category.Type, preview: String?): Long {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_ID, id)
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
    values.put(CATEGORY_COLUMN_PREVIEW, preview)

    return this.insertOrThrow(TABLE_CATEGORY, null, values)
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(title: String, type: Category.Type, preview: String?): Long {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
    values.put(CATEGORY_COLUMN_PREVIEW, preview)

    return this.insertOrThrow(TABLE_CATEGORY, null, values)
}

@Suppress("unused")
@Throws(SQLiteException::class)
fun SQLiteDatabase.insertCategory(c: Category): Long {
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

fun SQLiteDatabase.deleteCategory(id: Long): Int {
    val args = arrayOf(id.toString())
    return this.delete(TABLE_CATEGORY, CATEGORY_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.updateCategory(id: Long, title: String?, type: Category.Type, previewText: String?): Int {
    val values = ContentValues()
    values.put(CATEGORY_COLUMN_NAME, title)
    values.put(CATEGORY_COLUMN_TYPE, type.ordinal)
    values.put(CATEGORY_COLUMN_PREVIEW, previewText)
    val args = arrayOf(id.toString())
    return this.update(TABLE_CATEGORY, values, CATEGORY_WHERE_CLAUSE_ID, args)
}

fun SQLiteDatabase.updateCategory(category: Category): Int {
    return updateCategory(
        category.id, category.title,
        category.type, category.previewText
    )
}

@JvmOverloads
fun SQLiteDatabase.queryCategory(
    categories: ArrayList<Category>,
    maxNum: Int = -1,
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
    val cursor = this.query(TABLE_CATEGORY, null, selection, args, null, null, null)
    val vv = Category.Type.entries.toTypedArray()

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
        if (typeNum < 0 || typeNum >= vv.size) {
            Log.e(TAG, "invalid type_num: $typeNum")
            continue
        }
        val type = Category.Type.entries[typeNum]

        val c = Category(name, type)
        c.id = id
        c.previewText = preview
        categories.add(c)
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
    return from(FileHelper.attachmentStorage, sMd5, sName, sType)
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

private fun NoteItem.generateReactionString(): String? {
    if (reactions.isEmpty()) return null

    val sb = StringBuilder()
    for (r in reactions) {
        sb.append(Base64.encodeToString(r.toByteArray(), Base64.DEFAULT))
        sb.append(',')
    }

    return sb.toString()
}

private fun NoteItem.parseReactions(reactionInfo: String?) {
    if (reactionInfo == null) return
    val reactionS = reactionInfo.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (r in reactionS) {
        reactions.add(String(Base64.decode(r, Base64.DEFAULT)))
    }
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
 * insert a note item into database, if noteItem.getId() == ID_NOT_ASSIGNED, will generate a
 * new ID for it, and return that new id
 * @param n the noteItem need to be insert
 * @return the id in database
 */
@Throws(SQLiteException::class)
fun SQLiteDatabase.insertNote(n: NoteItem): Long {
    val id: Long = if (n.id == NoteItem.ID_NOT_ASSIGNED) {
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
        return -1
    }
    n.id = id
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
 * delete a noteItem in database with specific id
 * @param id ID of target noteItem
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
 * @param id ID of noteItem need to be updated
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
fun SQLiteDatabase.updateNote(note: NoteItem): Int {
    return updateNote(
        note.id, note.categoryId, note.text,
        note.createTime,
        note.generateAttachmentString(), note.generateFlags(),
        note.generateReactionString()
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

    val e = NoteItem(text, ctime, sender)
    e.parseFlags(flags)
    e.parseReactions(reactionInfo)
    e.id = id
    e.categoryId = categoryId
    if (!attachInfoAll.isNullOrEmpty()) {
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
fun SQLiteDatabase.queryNote(
    targetCategoryId: Long,
    noteItems: ArrayList<NoteItem>,
    maxNum: Int = -1,
    cb: IterateCallback<NoteItem>? = null
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
