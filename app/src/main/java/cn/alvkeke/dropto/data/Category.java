package cn.alvkeke.dropto.data;

import java.util.ArrayList;
import java.util.Date;

public class Category {

    public enum Type {
        LOCAL_CATEGORY,
        REMOTE_SELF_DEV,
        REMOTE_USERS,
    }

    private final long id;
    private final String title;
    private final Type type;
    private final ArrayList<NoteItem> noteItems;

    public Category(String title, Type type) {
        this.id = new Date().getTime();
        this.title = title;
        this.type = type;
        this.noteItems = new ArrayList<>();
    }

    public ArrayList<NoteItem> getNoteItems() {
        return noteItems;
    }

    public String getTitle() {
        return this.title;
    }

    public Type getType() {
        return this.type;
    }

    public long getId() {
        return id;
    }

}
