package cn.alvkeke.dropto.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

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

    private final Context context;
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

    @SuppressWarnings("unused")
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

    private static final String CATEGORY_WHERE_CLAUSE_ID = CATEGORY_COLUMN_ID + " = ?";
    public long insertCategory(long id, String title, Category.Type type, String preview) throws SQLiteException{
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(CATEGORY_COLUMN_ID, id);
        values.put(CATEGORY_COLUMN_NAME, title);
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal());
        values.put(CATEGORY_COLUMN_PREVIEW, preview);

        return db.insertOrThrow(TABLE_CATEGORY, null, values);
    }

    public long insertCategory(String title, Category.Type type, String preview) throws SQLiteException{
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(CATEGORY_COLUMN_NAME, title);
        values.put(CATEGORY_COLUMN_TYPE, type.ordinal());
        values.put(CATEGORY_COLUMN_PREVIEW, preview);

        return db.insertOrThrow(TABLE_CATEGORY, null, values);
    }

    @SuppressWarnings("unused")
    public long insertCategory(Category c) throws SQLiteException{

        long id;
        if (c.getId() == Category.ID_NOT_ASSIGNED) {
            id = insertCategory(c.getTitle(), c.getType(), c.getPreviewText());
        } else {
            id = insertCategory(c.getId(), c.getTitle(), c.getType(), c.getPreviewText());
        }
        if (id < 0) {
            Log.e(this.toString(), "Failed to insert category");
            return -1;
        }
        c.setId(id);
        return id;
    }

    public int deleteCategory(long id) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return 0;
        }
        String[] args = { String.valueOf(id) };
        return db.delete(TABLE_CATEGORY, CATEGORY_WHERE_CLAUSE_ID, args);
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

        String selection = null;
        String[] args = null;
        if (!categories.isEmpty()) {
            selection = "_id NOT IN (" +  "?,".repeat(categories.size()-1) + "?)";
            args = new String[categories.size()];
            for (int i=0; i<args.length; i++) {
                args[i] = String.valueOf(categories.get(i).getId());
            }
            Log.e(this.toString(), "args result: " + Arrays.toString(args));
        }
        Cursor cursor = db.query(TABLE_CATEGORY, null, selection, args, null, null, null);
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

            Category c = new Category(name, type);
            c.setId(id);
            c.setPreviewText(preview);
            categories.add(c);
            n_category++;
        }

        cursor.close();
    }

    private static final String NOTE_WHERE_CLAUSE_ID = NOTE_COLUMN_ID + " = ?";
    public long insertNote(long id, long categoryId, String text, long ctime,
                           String img_file, String img_name) throws SQLiteException{
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
        return db.insertOrThrow(TABLE_NOTE, null, values);
    }

    public long insertNote(long categoryId, String text, long ctime,
                           String img_file, String img_name) throws SQLiteException {
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
        return db.insertOrThrow(TABLE_NOTE, null, values);
    }

    /**
     * insert a note item into database, if noteItem.getId() == ID_NOT_ASSIGNED, will generate a
     * new ID for it, and return that new id
     * @param n the noteItem need to be insert
     * @return the id in database
     */
    public long insertNote(NoteItem n) throws SQLiteException{
        long id;
        String img_name = n.getImageFile() == null ? "" : n.getImageFile().getName();
        if (n.getId() == NoteItem.ID_NOT_ASSIGNED) {
            id = insertNote(n.getCategoryId(), n.getText(), n.getCreateTime(),
                    img_name, n.getImageName());
        } else {
            id = insertNote(n.getId(), n.getCategoryId(), n.getText(), n.getCreateTime(),
                    img_name, n.getImageName());
        }
        if (id < 0) {
            Log.e(this.toString(), "Failed to insert note");
            return -1;
        }
        n.setId(id);
        return id;
    }

    /**
     * delete a noteItem in database with specific id
     * @param id ID of target noteItem
     * @return number of deleted entries
     */
    public int deleteNote(long id) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return 0;
        }
        String[] args = { String.valueOf(id) };
        return db.delete(TABLE_NOTE, NOTE_WHERE_CLAUSE_ID, args);
    }

    /**
     * update note info in database with specific ID, all data apart of ID will be changed
     * @param id ID of noteItem need to be updated
     * @param categoryId the ID of the new category
     * @param text new note text
     * @param ctime new create time
     * @param img_file new image file
     * @param img_name new name of the file
     * @return count of affected rows
     */
    public int updateNote(long id, long categoryId, String text, long ctime,
                           String img_file, String img_name){
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return 0;
        }
        ContentValues values = new ContentValues();
        values.put(NOTE_COLUMN_CATE_ID, categoryId);
        values.put(NOTE_COLUMN_TEXT, text);
        values.put(NOTE_COLUMN_C_TIME, ctime);
        values.put(NOTE_COLUMN_IMG_FILE, img_file);
        values.put(NOTE_COLUMN_IMG_NAME, img_name);
        String[] args = { String.valueOf(id) };
        return db.update(TABLE_NOTE, values, NOTE_WHERE_CLAUSE_ID, args);
    }

    /**
     * update note info in database with specific ID, all data apart of ID will be changed
     * @param item item object contain updated info
     * @return count of affected rows
     */
    public int updateNote(NoteItem item) {
        File img_file = item.getImageFile();
        String s_img_file = img_file == null ? "" : img_file.getName();
        return updateNote(item.getId(), item.getCategoryId(), item.getText(),
                item.getCreateTime(), s_img_file, item.getImageName());
    }

    /**
     * retrieve note data from database
     * @param max_num max count of retrieve items, -1 for unlimited
     * @param target_category_id id of specific category, -1 for unspecific
     * @param noteItems the list to receive the result
     */
    public void queryNote(int max_num, long target_category_id, ArrayList<NoteItem> noteItems) {
        if (db == null) {
            Log.e(this.toString(), "database not opened");
            return;
        }
        if (noteItems == null) {
            Log.i(this.toString(), "uninitialized note item list");
            return;
        }

        String selection = null;
        String[] selectionArgs = null;
        if (target_category_id != -1) {
            selection = NOTE_COLUMN_CATE_ID + " = ?";
            selectionArgs = new String[]{ String.valueOf(target_category_id), };
        }
        Cursor cursor = db.query(TABLE_NOTE, null, selection, selectionArgs, null, null, null);
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
            long ctime = cursor.getLong(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_IMG_FILE);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String img_file = cursor.getString(idx);
            idx = cursor.getColumnIndex(NOTE_COLUMN_IMG_NAME);
            if (idx == -1) { Log.e(this.toString(), "invalid idx"); continue; }
            String img_name = cursor.getString(idx);

            NoteItem e = new NoteItem(text, ctime);
            e.setId(id);
            e.setCategoryId(category_id);
            if (!img_file.isEmpty()) {
                File f_img_file = new File(Global.getInstance().getFileStoreFolder(), img_file);
                if (!e.setImageFile(f_img_file)) {
                    Log.e(this.toString(), "Failed to set image file: " + img_file);
                }
            }
            if (img_name == null || img_name.isEmpty())
                img_name = null;
            e.setImageName(img_name);
            noteItems.add(e);
            n_notes++;
        }

        cursor.close();
    }
}
