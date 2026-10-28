package cn.alvkeke.dropto.provider.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import cn.alvkeke.dropto.provider.local.LocalUserDBHelper.Companion.COLUMN_AVATAR
import cn.alvkeke.dropto.provider.local.LocalUserDBHelper.Companion.COLUMN_NICKNAME
import cn.alvkeke.dropto.provider.local.LocalUserDBHelper.Companion.COLUMN_PASSWORD
import cn.alvkeke.dropto.provider.local.LocalUserDBHelper.Companion.COLUMN_USER_ID
import cn.alvkeke.dropto.provider.local.LocalUserDBHelper.Companion.TABLE_USER
import cn.alvkeke.dropto.provider.model.UserRecord

private const val TAG = "UserDBFunctions"

private const val USER_WHERE_CLAUSE_ID: String = "$COLUMN_USER_ID = ?"

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertUser(
    userId: String, nickname: String? = null, avatar: String? = null, password: String? = null
): Long {
    val values = ContentValues()
    values.put(COLUMN_USER_ID, userId)
    values.put(COLUMN_NICKNAME, nickname)
    values.put(COLUMN_AVATAR, avatar)
    values.put(COLUMN_PASSWORD, password)

    return this.insertOrThrow(TABLE_USER, null, values)
}

@Throws(SQLiteException::class)
fun SQLiteDatabase.insertUser(info: UserRecord): Long {
    return insertUser(info.userId, info.nickname, info.avatar)
}

fun SQLiteDatabase.hasUser(userId: String): Boolean {
    val cursor = this.query(
        TABLE_USER, arrayOf(COLUMN_USER_ID),
        USER_WHERE_CLAUSE_ID, arrayOf(userId),
        null, null, null, "1"
    )
    val exists = cursor.moveToFirst()
    cursor.close()

    return exists
}

fun SQLiteDatabase.queryUser(userId: String): UserRecord? {
    val cursor = this.query(
        TABLE_USER, null,
        USER_WHERE_CLAUSE_ID, arrayOf(userId),
        null, null, null
    )
    val info = if (cursor.moveToFirst()) cursor.parseLocalUser() else null
    cursor.close()

    return info
}

fun SQLiteDatabase.queryUsers(users: ArrayList<UserRecord>) {
    val cursor = this.query(
        TABLE_USER, null, null, null,
        null, null, "$COLUMN_USER_ID ASC"
    )
    while (cursor.moveToNext()) {
        val info = cursor.parseLocalUser() ?: continue
        users.add(info)
    }
    cursor.close()
}

fun SQLiteDatabase.updateUser(userId: String, nickname: String?, avatar: String?): Int {
    val values = ContentValues()
    values.put(COLUMN_NICKNAME, nickname)
    values.put(COLUMN_AVATAR, avatar)

    return this.update(TABLE_USER, values, USER_WHERE_CLAUSE_ID, arrayOf(userId))
}

fun SQLiteDatabase.updateUser(info: UserRecord): Int {
    return updateUser(info.userId, info.nickname, info.avatar)
}

fun SQLiteDatabase.updateUserPassword(userId: String, password: String?): Int {
    val values = ContentValues()
    values.put(COLUMN_PASSWORD, password)

    return this.update(TABLE_USER, values, USER_WHERE_CLAUSE_ID, arrayOf(userId))
}

/** The stored password hash of the user, null when the user has no password (or no row at all). */
fun SQLiteDatabase.queryPassword(userId: String): String? {
    val cursor = this.query(
        TABLE_USER, arrayOf(COLUMN_PASSWORD),
        USER_WHERE_CLAUSE_ID, arrayOf(userId),
        null, null, null, "1"
    )
    val password = if (cursor.moveToFirst()) {
        val idx = cursor.getColumnIndex(COLUMN_PASSWORD)
        if (idx == -1) null else cursor.getString(idx)
    } else {
        null
    }
    cursor.close()

    return password
}

fun SQLiteDatabase.deleteUser(userId: String): Int {
    return this.delete(TABLE_USER, USER_WHERE_CLAUSE_ID, arrayOf(userId))
}

private fun Cursor.parseLocalUser(): UserRecord? {
    var idx = getColumnIndex(COLUMN_USER_ID)
    if (idx == -1) {
        Log.e(TAG, "invalid idx for user id")
        return null
    }
    val userId = getString(idx) ?: return null

    idx = getColumnIndex(COLUMN_NICKNAME)
    val nickname = if (idx == -1) null else getString(idx)
    idx = getColumnIndex(COLUMN_AVATAR)
    val avatar = if (idx == -1) null else getString(idx)

    return UserRecord(userId, nickname, avatar)
}
