package cn.alvkeke.dropto.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.Global;
import cn.alvkeke.dropto.data.NoteItem;

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
    private static final String NOTE_COLUMN_CATE_ID = "category_id";
    private static final String NOTE_COLUMN_CATE_ID_TYPE = "INTEGER";
    private static final String NOTE_COLUMN_TEXT = "text";
    private static final String NOTE_COLUMN_TEXT_TYPE = "TEXT";
    private static final String NOTE_COLUMN_C_TIME = "ctime";
    private static final String NOTE_COLUMN_C_TIME_TYPE = "INTEGER";
    private static final String NOTE_COLUMN_IMG_FILE = "img_file";
    private static final String NOTE_COLUMN_IMG_FILE_TYPE = "TEXT";
    private static final String NOTE_COLUMN_IMG_NAME = "img_name";
    private static final String NOTE_COLUMN_IMG_NAME_TYPE = "TEXT";

    private Context context;
    private SQLiteDatabase db = null;

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
        db.execSQL(String.format(
                "CREATE TABLE IF NOT EXISTS %s (%s %s, %s %s, %s %s, %s %s, %s %s, %s %s)",
                TABLE_NOTE, NOTE_COLUMN_ID, NOTE_COLUMN_ID_TYPE,
                NOTE_COLUMN_CATE_ID, NOTE_COLUMN_CATE_ID_TYPE,
                NOTE_COLUMN_TEXT, NOTE_COLUMN_TEXT_TYPE,
                NOTE_COLUMN_C_TIME, NOTE_COLUMN_C_TIME_TYPE,
                NOTE_COLUMN_IMG_FILE, NOTE_COLUMN_IMG_FILE_TYPE,
                NOTE_COLUMN_IMG_NAME, NOTE_COLUMN_IMG_NAME_TYPE));
    }

    public void destroyDatabase() {
        context.deleteDatabase(DATABASE_NAME);
    }

    public void start() {
        if (db != null) return;
        db = this.getWritableDatabase();
    }

    public void finish() {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    public long insertCategory(long id, String title, Category.Type type, String preview) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(CATEGORY_COLUMN_ID, id);
        values.put(CATEGORY_COLUMN_NAME, title);
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal());
        values.put(CATEGORY_COLUMN_PREVIEW, preview);

        return db.insert(TABLE_CATEGORY, null, values);
    }

    public long insertCategory(String title, Category.Type type, String preview) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(CATEGORY_COLUMN_NAME, title);
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal());
        values.put(CATEGORY_COLUMN_PREVIEW, preview);

        return db.insert(TABLE_CATEGORY, null, values);
    }

    public long insertCategory(Category c, boolean genNewId) {

        long id;
        if (genNewId) {
            id = insertCategory(c.getId(), c.getTitle(), c.getType(), c.getPreviewText());
        } else {
            id = insertCategory(c.getTitle(), c.getType(), c.getPreviewText());
        }
        if (id < 0) {
            Log.e(this.toString(), "Failed to insert category");
            return -1;
        }
        c.setId(id);
        return id;
    }

    public void queryCategory(int max_num, ArrayList<Category> categories) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return;
        }
        if (categories == null) {
            Log.i(this.toString(), "uninitialized category list");
            return;
        }

        Cursor cursor = db.query(TABLE_CATEGORY, null, null, null, null, null, null);
        if (cursor == null) {
            Log.e(this.toString(), "Failed to get cursor");
            return;
        }
        Category.Type[] vv = Category.Type.values();

        int n_category = 0;
        while(cursor.moveToNext()) {
            int idx;

            if (max_num > 0 && n_category >= max_num ) break;
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_ID);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            long id = cursor.getLong(idx);
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_NAME);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String name = cursor.getString(idx);
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_PREVIEW);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String preview = cursor.getString(idx);
            idx = cursor.getColumnIndex(CATEGORY_COLUMN_TYPE);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            int type_num = cursor.getInt(idx);
            if (type_num < 0 || type_num >= vv.length) {
                Log.e(this.toString(), "invalid type_num: " + type_num);
                continue;
            }
            Category.Type type = Category.Type.values()[type_num];

            Category c = new Category(id, name, type);
            c.setPreviewText(preview);
            categories.add(c);
            n_category++;
        }

        cursor.close();
    }

    public long insertNote(long id, long categoryId, String text, long ctime,
                           String img_file, String img_name) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(NOTE_COLUMN_ID, id);
        values.put(NOTE_COLUMN_CATE_ID, categoryId);
        values.put(NOTE_COLUMN_TEXT, text);
        values.put(NOTE_COLUMN_C_TIME, ctime);
        values.put(NOTE_COLUMN_IMG_FILE, img_file);
        values.put(NOTE_COLUMN_IMG_NAME, img_name);
        return db.insert(TABLE_NOTE, null, values);
    }

    public long insertNote(long categoryId, String text, long ctime,
                           String img_file, String img_name) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(NOTE_COLUMN_CATE_ID, categoryId);
        values.put(NOTE_COLUMN_TEXT, text);
        values.put(NOTE_COLUMN_C_TIME, ctime);
        values.put(NOTE_COLUMN_IMG_FILE, img_file);
        values.put(NOTE_COLUMN_IMG_NAME, img_name);
        return db.insert(TABLE_NOTE, null, values);
    }

    public long insertNote(NoteItem n, boolean genNewId) {
        long id;
        String img_name = n.getImageFile() == null ? "" : n.getImageFile().getName();
        if (genNewId) {
            id = insertNote(n.getCategoryId(), n.getText(), n.getCreateTime(),
                    img_name, "");
        } else {
            id = insertNote(n.getId(), n.getCategoryId(), n.getText(), n.getCreateTime(),
                    img_name, "");
        }
        if (id < 0) {
            Log.e(this.toString(), "Failed to insert category");
            return -1;
        }
        n.setId(id);
        return id;
    }

    public void queryNote(int max_num, ArrayList<NoteItem> noteItems) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return;
        }
        if (noteItems == null) {
            Log.i(this.toString(), "uninitialized note item list");
            return;
        }

        Cursor cursor = db.query(TABLE_NOTE, null, null, null, null, null, null);
        if (cursor == null) {
            Log.e(this.toString(), "Failed to get cursor");
            return;
        }

        int n_notes = 0;
        while(cursor.moveToNext()) {
            if (max_num > 0 && n_notes >= max_num ) break;
            int idx;
            idx = cursor.getColumnIndex(NOTE_COLUMN_ID);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            long id = cursor.getLong(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_CATE_ID);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            long category_id = cursor.getLong(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_TEXT);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String text = cursor.getString(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_C_TIME);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            long ctime = cursor.getInt(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_IMG_FILE);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String img_file = cursor.getString(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_IMG_NAME);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String img_name = cursor.getString(idx);
            Log.d(this.toString(), "image name: " + img_name);

            File f_img_file = new File(Global.getInstance().getFileStoreFolder(), img_file);
            NoteItem e = new NoteItem(text, ctime);
            e.setId(id);
            e.setCategoryId(category_id);
            e.setImageFile(f_img_file);
            noteItems.add(e);
            n_notes++;
        }

        cursor.close();
    }
}
