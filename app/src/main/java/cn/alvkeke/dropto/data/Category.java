package cn.alvkeke.dropto.data;

import java.util.ArrayList;

public class Category {

    public static final long ID_NOT_ASSIGNED = -1;

    public enum Type {
        LOCAL_CATEGORY,
        REMOTE_SELF_DEV,
        REMOTE_USERS,
    }

    private long id;
    private final String title;
    private final Type type;
    private String previewText = null;
    private final ArrayList<NoteItem> noteItems;
    private boolean needUpdate = false;

    public Category(String title, Type type) {
        this.id = ID_NOT_ASSIGNED;
        this.title = title;
        this.type = type;
        this.noteItems = new ArrayList<>();
    }

    public ArrayList<NoteItem> getNoteItems() {
        return noteItems;
    }

    public void addNoteItem(NoteItem item) {
        noteItems.add(item);
        previewText = item.getText();
        needUpdate = true;
    }

    public void setUpdated() {
        needUpdate = false;
    }

    public boolean needUpdate() {
        return needUpdate;
    }

    public void setPreviewText(String previewText) {
        this.previewText = previewText;
    }

    public String getPreviewText() {
        return previewText == null ? "" : previewText;
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

    public void setId(long id) {
        this.id = id;
    }
}
