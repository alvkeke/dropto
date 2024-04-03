package cn.alvkeke.dropto.data;

import java.io.File;
import java.util.ArrayList;

public class Global {
    private static final Global _instance = new Global();
    private final ArrayList<Category> categories = new ArrayList<>();
    private File fileStorage;

    private Global() {
    }

    public static Global getInstance() {
        return _instance;
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

    public File getFileStoreFolder() {
        return fileStorage;
    }

    public void setFileStoreFolder(File fileStorage) {
        this.fileStorage = fileStorage;
    }

}
