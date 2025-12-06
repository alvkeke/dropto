package cn.alvkeke.dropto.storage;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

import cn.alvkeke.dropto.data.Category;

public class DataLoader {

    private static DataLoader instance = null;

    private DataLoader() { }

    public static DataLoader getInstance() {
        if (instance != null)
            return instance;

        instance = new DataLoader();
        return instance;
    }

    private ArrayList<Category> categories = null;

    public ArrayList<Category> loadCategories(Context context) {
        if (categories == null)
            categories = new ArrayList<>();
        try (DataBaseHelper helper = new DataBaseHelper(context)) {
            helper.start();
            helper.queryCategory(-1, categories);
            helper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to retrieve category data from database");
        }

        return categories;
    }

    public ArrayList<Category> getCategories() {
        return categories;
    }

    public Category findCategory(long id) {
        if (categories == null)
            return null;

        for (Category c : categories) {
            if (c.id == id)
                return c;
        }
        return null;
    }

    public boolean loadCategoryNotes(Context context, Category category) {
        if (category.isInitialized)
            return true;
        try (DataBaseHelper helper = new DataBaseHelper(context)) {
            helper.start();
            helper.queryNote(-1, category.id, category.noteItems);
            category.isInitialized = true;
            helper.finish();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

}
