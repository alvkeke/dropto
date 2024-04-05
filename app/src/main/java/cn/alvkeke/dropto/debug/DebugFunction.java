package cn.alvkeke.dropto.debug;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.alvkeke.dropto.BuildConfig;
import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;
import cn.alvkeke.dropto.storage.DataBaseHelper;

public class DebugFunction {

    public static final String LOG_TAG = "DebugFunction";

    private static void dbgLog(String msg) {
        if (BuildConfig.DEBUG) Log.e(LOG_TAG, msg);
    }

    public static boolean extract_raw_file(Context context, int id, File o_file) {
        if (!BuildConfig.DEBUG) return false;
        dbgLog("Perform Debug function to extract res file");

        if (o_file.exists()) {
            // file exist, return true to indicate can be load
            dbgLog("file exist, don't extract:" + o_file);
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            dbgLog("SDK_VERSION error: " + Build.VERSION.SDK_INT);
            return false;
        }
        byte[] buffer = new byte[1024];
        try {
            InputStream is = context.getResources().openRawResource(id);
            OutputStream os = Files.newOutputStream(o_file.toPath());
            int len;
            while((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
            os.close();
            is.close();
        } catch (IOException e) {
            dbgLog("Failed to extract res: " +
                    context.getResources().getResourceEntryName(id) + " to " + o_file);
            return false;
        }
        return true;
    }

    public static List<File> try_extract_res_images(Context context, File folder) {
        if (!BuildConfig.DEBUG) return null;
        dbgLog("Perform Debug function to extract images");

        List<Integer> rawIds = new ArrayList<>();
        Field[] fields = R.raw.class.getFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    int id = f.getInt(null);
                    rawIds.add(id);
                } catch (IllegalAccessException e) {
                    dbgLog("failed to get resource ID of raw:" + f);
                }
            }
        }

        List<File> ret_files = new ArrayList<>();
        for (int id : rawIds) {
            File o_file = new File(folder, context.getResources().getResourceEntryName(id) + ".png");
            if (extract_raw_file(context, id, o_file))
                ret_files.add(o_file);
        }

        return ret_files;
    }


    /**
     * fill category database for debugging, this function will be exec only in DEBUG build
     * @param context context
     */
    public static void fill_database_for_category(Context context) {
        if (!BuildConfig.DEBUG) return;
        dbgLog("Perform Debug function to fill database for categories");
        try (DataBaseHelper dbHelper = new DataBaseHelper(context)) {
            dbHelper.start();

            // fix the category id, make sure it will not create multiple times
            dbHelper.insertCategory(1, "Local(Debug)", Category.Type.LOCAL_CATEGORY, "");
            dbHelper.insertCategory(2, "REMOTE USERS", Category.Type.REMOTE_USERS, "");
            dbHelper.insertCategory(3, "REMOTE SELF DEVICE", Category.Type.REMOTE_SELF_DEV, "");

            dbHelper.finish();
        } catch (Exception e) {
            dbgLog("Failed to perform debug database filling");
        }
    }

    public static void fill_database_for_note(Context context, List<File> img_files, long cate_id) {
        if (!BuildConfig.DEBUG) return;
        dbgLog("Perform Debug function to fill database for noteItems");

        try (DataBaseHelper dataBaseHelper = new DataBaseHelper(context)) {
            dataBaseHelper.start();
            Random r = new Random();
            int idx = 0;
            for (int i = 0; i < 15; i++) {
                NoteItem e = new NoteItem("ITEM" + i + i, System.currentTimeMillis());
                e.setCategoryId(cate_id);
                if (r.nextBoolean()) {
                    e.setText(e.getText(), true);
                }
                if (idx < img_files.size() && r.nextBoolean()) {
                    File img_file = img_files.get(idx);
                    idx++;
                    if (img_file.exists()) {
                        dbgLog("add image file: " + img_file);
                        e.setImageFile(img_file);
                    } else {
                        dbgLog("add image file failed, not exist: " + img_file);
                    }

                }
                e.setId(i+1);
                dataBaseHelper.insertNote(e);
            }
            dataBaseHelper.finish();
        } catch (Exception e) {
            dbgLog("Failed to perform debug database filling for note");
        }
    }

}
