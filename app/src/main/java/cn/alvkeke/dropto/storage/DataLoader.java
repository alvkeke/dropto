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

    private void loadCategories(Context context) {
        if (categories == null)
            categories = new ArrayList<>();
        try (DataBaseHelper helper = new DataBaseHelper(context)) {
            helper.start();
            helper.queryCategory(-1, categories);
            helper.finish();
        } catch (Exception ex) {
            Log.e(this.toString(), "Failed to retrieve category data from database");
        }
    }

    public ArrayList<Category> getCategories(Context context) {
        loadCategories(context);
        return categories;
    }

    public Category findCategory(Context context, long id) {
        loadCategories(context);

        for (Category c : categories) {
            if (c.getId() == id)
                return c;
        }
        return null;
    }

    public boolean loadCategoryNotes(Context context, Category category) {
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
