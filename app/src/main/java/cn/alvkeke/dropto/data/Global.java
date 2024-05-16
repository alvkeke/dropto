package cn.alvkeke.dropto.data;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import cn.alvkeke.dropto.storage.DataBaseHelper;

public class Global {
    private static final Global _instance = new Global();
    private final ArrayList<Category> categories = new ArrayList<>();
    private File fileStorage;

    private Global() {
    }

    public static Global getInstance() {
        return _instance;
    }

    public void loadCategories(Context context) {
        try (DataBaseHelper helper = new DataBaseHelper(context)) {
            helper.start();
            helper.queryCategory(-1, categories);
            helper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to retrieve category data from database");
        }
    }

    public ArrayList<Category> getCategories() {
        return categories;
    }

    public Category findCategory(long id) {
        for (Category c : categories) {
            if (c.getId() == id) return c;
        }
        return null;
    }

    @SuppressWarnings("unused")
    public Category getCategory(int index) {
        if (index >= categories.size()) return null;
        return categories.get(index);
    }

    public File getFileStoreFolder() {
        return fileStorage;
    }

    public void setFileStoreFolder(File fileStorage) {
        this.fileStorage = fileStorage;
    }

    public static boolean loadCategoryNotes(Context context, Category category) {
        if (category.isInitialized())
            return true;
        try (DataBaseHelper helper = new DataBaseHelper(context)) {
            helper.start();
            helper.queryNote(-1, category.getId(), category.getNoteItems());
            category.setInitialized(true);
            helper.finish();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }
}
