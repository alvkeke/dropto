package cn.alvkeke.dropto.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import cn.alvkeke.dropto.data.Category;

public class DataBaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "note.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_CATEGORY = "category";
    private static final String CATEGORY_COLUMN_ID = "_id";
    private static final String CATEGORY_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT";
    private static final String CATEGORY_COLUMN_NAME = "name";
    private static final String CATEGORY_COLUMN_NAME_TYPE = "TEXT";
    private static final String CATEGORY_COLUMN_PREVIEW = "preview_text";
    private static final String CATEGORY_COLUMN_PREVIEW_TYPE = "TEXT";
    private static final String CATEGORY_COLUMN_TYPE = "type";
    private static final String CATEGORY_COLUMN_TYPE_TYPE = "INTEGER";

    private static final String TABLE_NOTE = "note";
    private static final String NOTE_COLUMN_ID = "_id";
    private static final String NOTE_COLUMN_ID_TYPE = "INTEGER PRIMARY KEY AUTOINCREMENT";
    private static final String NOTE_COLUMN_TEXT = "text";
    private static final String NOTE_COLUMN_TEXT_TYPE = "TEXT";
    private static final String NOTE_COLUMN_C_TIME = "ctime";
    private static final String NOTE_COLUMN_C_TIME_TYPE = "INTEGER";
    private static final String NOTE_COLUMN_IMG_FILE = "img_file";
    private static final String NOTE_COLUMN_IMG_FILE_TYPE = "TEXT";
    private static final String NOTE_COLUMN_IMG_NAME = "img_name";
    private static final String NOTE_COLUMN_IMG_NAME_TYPE = "TEXT";

    private Context context;

    public DataBaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createCategoryTable(db);
        createNoteTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {

    }

    private void createCategoryTable(SQLiteDatabase db) {
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, %s %s, %s %s)",
                TABLE_CATEGORY, CATEGORY_COLUMN_ID, CATEGORY_COLUMN_ID_TYPE,
                CATEGORY_COLUMN_TYPE, CATEGORY_COLUMN_TYPE_TYPE,
                CATEGORY_COLUMN_NAME, CATEGORY_COLUMN_NAME_TYPE,
                CATEGORY_COLUMN_PREVIEW, CATEGORY_COLUMN_PREVIEW_TYPE));
    }

    private void createNoteTable(SQLiteDatabase db) {
        db.execSQL(String.format("CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, %s %s, %s %s, %s %s)",
                TABLE_NOTE, NOTE_COLUMN_ID, NOTE_COLUMN_ID_TYPE,
                NOTE_COLUMN_TEXT, NOTE_COLUMN_TEXT_TYPE,
                NOTE_COLUMN_C_TIME, NOTE_COLUMN_C_TIME_TYPE,
                NOTE_COLUMN_IMG_FILE, NOTE_COLUMN_IMG_FILE_TYPE,
                NOTE_COLUMN_IMG_NAME, NOTE_COLUMN_IMG_NAME_TYPE));
    }

    public void destroyDatabase() {
        context.deleteDatabase(DATABASE_NAME);
    }

    public void insertCategory(Category c, boolean genID) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (!genID) values.put(CATEGORY_COLUMN_ID, c.getId());
        values.put(CATEGORY_COLUMN_NAME, c.getTitle());
        values.put(CATEGORY_COLUMN_PREVIEW, c.getPreviewText());
        db.insert(TABLE_CATEGORY, null, values);

        db.close();
    }
}
