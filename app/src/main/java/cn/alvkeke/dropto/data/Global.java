package cn.alvkeke.dropto.data;

import java.util.ArrayList;

public class Global {
    private static final Global _instance = new Global();
    private final ArrayList<Category> categories = new ArrayList<>();

    private Global() {
    }

    public static Global getInstance() {
        return _instance;
    }

    public ArrayList<Category> getCategories() {
        return categories;
    }

}
