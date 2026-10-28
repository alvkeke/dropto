package cn.alvkeke.dropto.provider.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log


class LocalUserDBHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val TAG = "UserDBHelper"

        const val DATABASE_NAME = "localuser.db"

        private const val DATABASE_VERSION = 1

        const val TABLE_USER = "user"

        const val COLUMN_USER_ID = "userId"
        const val COLUMN_USER_ID_TYPE = "TEXT PRIMARY KEY"

        // the salted hash of the password, the password itself is never stored
        const val COLUMN_PASSWORD = "password"
        const val COLUMN_PASSWORD_TYPE = "TEXT"

        const val COLUMN_NICKNAME = "nickname"
        const val COLUMN_NICKNAME_TYPE = "TEXT"


        const val COLUMN_AVATAR = "avatar"
        const val COLUMN_AVATAR_TYPE = "TEXT"

    }

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "onCreate, creating the user table")
        createUserTable(db)
    }

    override fun onUpgrade(p0: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Log.i(TAG, "onUpgrade $oldVersion -> $newVersion")
    }

    private fun createUserTable(db: SQLiteDatabase) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $TABLE_USER (
                $COLUMN_USER_ID $COLUMN_USER_ID_TYPE,
                $COLUMN_PASSWORD $COLUMN_PASSWORD_TYPE,
                $COLUMN_NICKNAME $COLUMN_NICKNAME_TYPE,
                $COLUMN_AVATAR $COLUMN_AVATAR_TYPE
            );
        """.trimIndent()

        db.execSQL(sql)
    }

}
