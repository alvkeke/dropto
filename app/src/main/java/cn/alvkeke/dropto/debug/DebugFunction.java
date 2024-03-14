package cn.alvkeke.dropto.debug;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
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

import cn.alvkeke.dropto.R;
import cn.alvkeke.dropto.data.Category;
import cn.alvkeke.dropto.data.NoteItem;

public class DebugFunction {
    public static boolean extract_raw_file(Context context, int id, File o_file) {
        if (o_file.exists()) {
            // file exist, return true to indicate can be load
            Log.d("DebugFunction", "file exist, don't extract:" + o_file);
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Log.e("DebugFunction", "SDK_VERSION error: " + Build.VERSION.SDK_INT);
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
            Log.e("DebugFunction", "Failed to extract res: " +
                    context.getResources().getResourceEntryName(id) + " to " + o_file);
            return false;
        }
        return true;
    }

    public static List<File> try_extract_res_images(Context context, File folder) {

        List<Integer> rawIds = new ArrayList<>();
        Field[] fields = R.raw.class.getFields();
        for (Field f : fields) {
            if (f.getType() == int.class) {
                try {
                    int id = f.getInt(null);
                    rawIds.add(id);
                } catch (IllegalAccessException e) {
                    Log.e("DebugFunction", "failed to get resource ID of raw:" + f);
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

    public static void dbg_fill_list(Context context, Category category, File img_folder) {

        Log.e("DebugFunction", "sdcard: " + Environment.getExternalStorageDirectory());

        int idx = 0;
        Random r = new Random();
        if (img_folder == null) {
            Log.e("DebugFunction", "Failed to get image folder, exit!!");
            return;
        }
        Log.d("DebugFunction", "image folder path: " + img_folder);
        List<File> img_files = try_extract_res_images(context, img_folder);

        for (int i=0; i<15; i++) {
            NoteItem e = new NoteItem("ITEM" + i + i, System.currentTimeMillis());
            if (r.nextBoolean()) {
                e.setText(e.getText(), true);
            }
            if (idx < img_files.size() && r.nextBoolean()) {
                File img_file = img_files.get(idx);
                idx++;
                if (img_file.exists()) {
                    Log.d("DebugFunction", "add image file: " + img_file);
                } else {
                    Log.e("DebugFunction", "add image file failed, not exist: " + img_file);
                }

                e.setImageFile(img_file);
            }
            category.addNoteItem(e);
        }
    }

}
